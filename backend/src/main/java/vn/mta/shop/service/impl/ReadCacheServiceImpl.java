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

/**
 * Cơ chế dự phòng (Fallback Strategy) theo mô hình cache-aside:
 * 1. Redis có dữ liệu hợp lệ (hit): dùng metadata trong cache.
 * 2. Redis chưa có dữ liệu (miss): đọc MySQL rồi thử ghi metadata vào Redis.
 * 3. Redis lỗi/timeout hoặc JSON hỏng: đọc MySQL, không trả lỗi chỉ vì cache hỏng.
 *
 * Khoảng nghỉ sau lỗi hoạt động như circuit breaker đơn giản: CLOSED cho đọc Redis,
 * OPEN tạm bỏ qua Redis, HALF-OPEN cho một request thăm dò rồi quyết định phục hồi.
 * Các cờ bên dưới chỉ có hiệu lực trong API instance này, không phải khóa phân tán.
 */
@Service
public class ReadCacheServiceImpl implements ReadCacheService {
  private static final Logger log = LoggerFactory.getLogger(ReadCacheServiceImpl.class);
  private final RedisCacheRepository repository;
  private final ObjectMapper json;
  // false: bỏ qua cả GET/SET/DEL tại service, các lần đọc đi thẳng tới MySQL.
  private final boolean enabled;
  // ttl: thời gian sống mỗi entry (mặc định 10 giây).
  // backoff: khoảng bỏ qua Redis sau lỗi (mặc định 5 giây), khác với timeout của lệnh Redis.
  private final Duration ttl, backoff;
  // 0 nghĩa là circuit đóng. Giá trị khác 0 là mốc nanoTime được thử Redis lần tiếp theo.
  // AtomicLong giúp các request chạy trên nhiều thread đọc/đổi trạng thái an toàn.
  private final AtomicLong retryAt = new AtomicLong();
  // Chỉ một thread được thử Redis khi hết backoff; các thread khác tiếp tục đọc DB.
  private final AtomicBoolean probe = new AtomicBoolean();
  // DB đã commit nhưng DEL Redis chưa thành công: cần xóa lại cache khi Redis phục hồi.
  // Cờ ở RAM của instance, không thay thế TTL hay bảo đảm nhất quán tuyệt đối nhiều instance.
  private final AtomicBoolean dirty = new AtomicBoolean();

  // schema=1 là phiên bản định dạng JSON cache, không phải version chống ghi đè của entity.
  // Chỉ chứa metadata công khai; không lưu phiên đăng nhập hoặc quyết định thanh toán/kho.
  public record Payload(int schema, List<Map<String, Object>> rows) {}

  public ReadCacheServiceImpl(
      RedisCacheRepository repository,
      ObjectMapper json,
      @Value("${app.cache.enabled:true}") boolean enabled,
      @Value("${app.cache.ttl:10s}") Duration ttl,
      @Value("${app.cache.failure-backoff:5s}") Duration backoff) {
    // Sai cấu hình phải được phát hiện khi khởi động, tránh entry tồn tại quá lâu hoặc retry liên tục.
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

  /**
   * databaseLoader là hàm đọc DB được truyền vào nhưng CHƯA thực thi (Supplier).
   * Ví dụ productRepository::findCatalogMetadata; chỉ .get() mới thật sự đọc MySQL.
   */
  @Override
  public List<Map<String, Object>> getOrLoad(
      CacheRegion region, Supplier<List<Map<String, Object>>> databaseLoader) {
    // Bước 1: kiểm tra circuit. nanoTime đo khoảng thời gian, không phụ thuộc chỉnh giờ hệ thống.
    long gate = retryAt.get(), now = System.nanoTime();
    // Cache tắt hoặc vẫn đang trong khoảng nghỉ: KHÔNG chờ Redis, đọc DB ngay.
    if (!enabled || (gate != 0 && now < gate)) return databaseLoader.get();
    // Bước 2: hết khoảng nghỉ thì HALF-OPEN. compareAndSet chọn đúng một request thăm dò.
    boolean halfOpen = gate != 0;
    if (halfOpen && !probe.compareAndSet(false, true)) return databaseLoader.get();
    // Chỉ thử SET sau cache miss nếu GET thực sự chạy được; GET lỗi thì không chờ SET thêm lần nữa.
    boolean redisAvailable = false;
    try {
      try {
        // Nếu xóa cache từng lỗi sau DB commit, xóa lại trước khi dùng Redis phục hồi.
        if (dirty.get()) {
          repository.delete(Arrays.stream(CacheRegion.values()).map(CacheRegion::key).toList());
          dirty.set(false);
        }
        // Bước 3: Redis GET. Timeout kết nối/lệnh được cấu hình ở application.yml/RedisClientConfig.
        String value = repository.get(region.key());
        // GET thành công: đóng circuit nếu mốc chưa bị thread khác thay đổi.
        // Nếu JSON phía dưới hỏng, catch sẽ mở lại khoảng nghỉ và chuyển về DB.
        retryAt.compareAndSet(gate, 0);
        redisAvailable = true;
        if (value != null) {
          // CACHE HIT: giải mã và kiểm tra payload trước khi dùng; null mới là cache miss.
          Payload payload =
              json.readerFor(Payload.class)
                  // Giữ số thập phân dưới dạng BigDecimal, tránh mất độ chính xác giá do Double.
                  .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                  .readValue(value);
          validate(region, payload);
          // Chỉ trả metadata; ShopServiceImpl vẫn đọc tồn/quota/giá sale hiện tại từ MySQL.
          return payload.rows();
        }
      } catch (Exception cacheFailure) {
        // Bao gồm lỗi kết nối, timeout, deserialize và payload sai shape; đều không phải lỗi DB.
        redisAvailable = false;
        failed(cacheFailure);
      }
      // Bước 4: CACHE MISS hoặc CACHE FAILURE -> Fallback về database chính.
      // Cố ý nằm NGOÀI catch Redis: MySQL cũng lỗi thì phải báo lỗi thật, không giả vờ thành công.
      List<Map<String, Object>> rows = databaseLoader.get();
      // Bước 5: DB đọc thành công, thử nạp lại cache nếu Redis còn dùng được.
      // TTL giới hạn thời gian tồn tại entry; không phải khóa và không bảo đảm dữ liệu luôn mới tuyệt đối.
      if (redisAvailable && retryAt.get() == 0) {
        try {
          repository.put(region.key(), json.writeValueAsString(new Payload(1, rows)), ttl);
        } catch (Exception cacheFailure) {
          // SET lỗi vẫn trả dữ liệu DB vừa lấy; không làm API 500 chỉ vì không ghi được cache.
          failed(cacheFailure);
        }
      }
      return rows;
    } finally {
      // Luôn nhả quyền thăm dò, kể cả khi return cache hit hoặc databaseLoader ném exception.
      if (halfOpen) probe.set(false);
    }
  }

  // Dữ liệu cache có thể cũ hoặc hỏng: chỉ chấp nhận đúng schema/shape/thời gian.
  // Exception ở đây được getOrLoad bắt để đọc lại DB, không đẩy lỗi cache xuống giao diện.
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

  /**
   * Được gọi sau khi transaction quản trị commit. Chỉ DEL hai key của ứng dụng, không FLUSHALL.
   * Nếu Redis hỏng, giữ dirty=true để lần phục hồi thử xóa trước khi dùng lại metadata cũ.
   */
  @Override
  public void invalidatePublicMetadata() {
    if (!enabled) return;
    // Vẫn thử xóa dù circuit đọc đang mở: DB đã đổi và cache cũ cần được vô hiệu hóa.
    dirty.set(true);
    try {
      repository.delete(Arrays.stream(CacheRegion.values()).map(CacheRegion::key).toList());
      dirty.set(false);
    } catch (RuntimeException cacheFailure) {
      // DB đã commit: DEL lỗi không được làm thao tác lưu của người dùng thành lỗi.
      failed(cacheFailure);
    }
  }

  // Mở khoảng nghỉ chung cho cache trên instance; không retry Redis lặp trong cùng request.
  private void failed(Exception failure) {
    long now = System.nanoTime(), until = now + backoff.toNanos();
    // Chỉ mở khoảng nghỉ mới nếu circuit chưa mở hoặc mốc cũ đã hết.
    // Không để mỗi request lỗi lại kéo dài backoff, khiến Redis không bao giờ được thăm dò.
    long previous = retryAt.getAndUpdate(old -> old == 0 || old <= now ? until : old);
    // Hạn chế log lặp và không in payload/secret; log lớp lỗi đủ để biết đang fallback.
    if (previous == 0 || previous <= now)
      log.warn(
          "Redis cache unavailable ({}); bypassing cache for {} ms; reads use primary database",
          failure.getClass().getSimpleName(),
          backoff.toMillis());
  }
}
