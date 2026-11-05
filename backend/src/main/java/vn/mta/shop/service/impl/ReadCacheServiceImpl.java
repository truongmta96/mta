package vn.mta.shop.service.impl;

import com.fasterxml.jackson.databind.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.mta.shop.cache.CacheRegion;
import vn.mta.shop.repository.RedisCacheRepository;
import vn.mta.shop.service.ReadCacheService;

/** Cache-aside: mọi lỗi cache phải được cô lập khỏi loader database và kết quả nghiệp vụ. */
@Service
public class ReadCacheServiceImpl implements ReadCacheService {
  private static final Logger log = LoggerFactory.getLogger(ReadCacheServiceImpl.class);
  private final RedisCacheRepository repository;
  private final ObjectMapper json;
  private final boolean enabled;
  private final Duration ttl, backoff;
  private final AtomicLong retryAt = new AtomicLong();
  private final AtomicBoolean probe = new AtomicBoolean();
  private final AtomicBoolean dirty = new AtomicBoolean();

  public record Payload(int schema, List<Map<String, Object>> rows) {}

  public ReadCacheServiceImpl(
      RedisCacheRepository repository,
      ObjectMapper json,
      @Value("${app.cache.enabled:true}") boolean enabled,
      @Value("${app.cache.ttl:10s}") Duration ttl,
      @Value("${app.cache.failure-backoff:5s}") Duration backoff) {
    if (ttl.isNegative()
        || ttl.isZero()
        || ttl.compareTo(Duration.ofMinutes(5)) > 0
        || backoff.isNegative()
        || backoff.isZero()
        || backoff.compareTo(Duration.ofMinutes(1)) > 0)
      throw new IllegalArgumentException(
          "Cache TTL/backoff phải dương và trong giới hạn 5 phút/1 phút");
    this.repository = repository;
    this.json = json;
    this.enabled = enabled;
    this.ttl = ttl;
    this.backoff = backoff;
  }

  @Override
  public List<Map<String, Object>> getOrLoad(
      CacheRegion region, Supplier<List<Map<String, Object>>> databaseLoader) {
    long gate = retryAt.get(), now = System.nanoTime();
    if (!enabled || (gate != 0 && now < gate)) return databaseLoader.get();
    boolean halfOpen = gate != 0;
    if (halfOpen && !probe.compareAndSet(false, true)) return databaseLoader.get();
    boolean redisAvailable = false;
    try {
      try {
        // Nếu xóa cache từng lỗi sau DB commit, xóa lại trước khi dùng Redis phục hồi.
        if (dirty.get()) {
          repository.delete(Arrays.stream(CacheRegion.values()).map(CacheRegion::key).toList());
          dirty.set(false);
        }
        String value = repository.get(region.key());
        retryAt.compareAndSet(gate, 0);
        redisAvailable = true;
        if (value != null) {
          Payload payload =
              json.readerFor(Payload.class)
                  .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                  .readValue(value);
          validate(region, payload);
          return payload.rows();
        }
      } catch (Exception cacheFailure) {
        redisAvailable = false;
        failed(cacheFailure);
      }
      // Cố ý ở ngoài try/catch Redis: lỗi MySQL phải được truyền lên, không che thành cache miss.
      List<Map<String, Object>> rows = databaseLoader.get();
      if (redisAvailable && retryAt.get() == 0) {
        try {
          repository.put(region.key(), json.writeValueAsString(new Payload(1, rows)), ttl);
        } catch (Exception cacheFailure) {
          failed(cacheFailure);
        }
      }
      return rows;
    } finally {
      if (halfOpen) probe.set(false);
    }
  }

  private void validate(CacheRegion region, Payload p) {
    if (p == null || p.schema() != 1 || p.rows() == null || p.rows().size() > region.maximumRows())
      throw new IllegalArgumentException("Cache payload không đúng phiên bản/shape");
    for (var row : p.rows()) {
      if (row == null || !(row.get("id") instanceof Number) || !(row.get("name") instanceof String))
        throw new IllegalArgumentException("Cache row không hợp lệ");
      if (region == CacheRegion.PRODUCTS
          && (!(row.get("price") instanceof Number)
              || !row.containsKey("brand")
              || !row.containsKey("storage_gb")
              || !row.containsKey("color")))
        throw new IllegalArgumentException("Cache product không hợp lệ");
      if (region == CacheRegion.CAMPAIGNS) {
        java.time.OffsetDateTime.parse(Objects.toString(row.get("starts_at"), ""));
        java.time.OffsetDateTime.parse(Objects.toString(row.get("ends_at"), ""));
        if (!Set.of("ACTIVE", "SCHEDULED").contains(row.get("status")))
          throw new IllegalArgumentException("Cache campaign không hợp lệ");
      }
    }
  }

  @Override
  public void invalidatePublicMetadata() {
    if (!enabled) return;
    dirty.set(true);
    try {
      repository.delete(Arrays.stream(CacheRegion.values()).map(CacheRegion::key).toList());
      dirty.set(false);
    } catch (RuntimeException cacheFailure) {
      failed(cacheFailure);
    }
  }

  private void failed(Exception failure) {
    long now = System.nanoTime(), until = now + backoff.toNanos();
    long previous = retryAt.getAndUpdate(old -> old == 0 || old <= now ? until : old);
    if (previous == 0 || previous <= now)
      log.warn(
          "Redis cache unavailable ({}); bypassing cache for {} ms; reads use primary database",
          failure.getClass().getSimpleName(),
          backoff.toMillis());
  }
}
