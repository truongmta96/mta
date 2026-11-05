package vn.mta.shop.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import vn.mta.shop.repository.RedisCacheRepository;
import vn.mta.shop.service.impl.ReadCacheServiceImpl;

class ReadCacheFallbackTest {
  private final RedisCacheRepository redis = mock(RedisCacheRepository.class);
  private final ObjectMapper json = new ObjectMapper();

  private ReadCacheServiceImpl cache(boolean enabled, Duration backoff) {
    return new ReadCacheServiceImpl(redis, json, enabled, Duration.ofSeconds(10), backoff);
  }

  private List<Map<String, Object>> rows() {
    return List.of(
        Map.of(
            "id",
            1L,
            "name",
            "Phone",
            "brand",
            "Brand",
            "storage_gb",
            128,
            "color",
            "Green",
            "price",
            new BigDecimal("123456.78")));
  }

  private String payload() throws Exception {
    return json.writeValueAsString(new ReadCacheServiceImpl.Payload(1, rows()));
  }

  @Test
  void hitDoesNotReadDatabaseAndPreservesMoney() throws Exception {
    when(redis.get(CacheRegion.PRODUCTS.key())).thenReturn(payload());
    var result =
        cache(true, Duration.ofSeconds(5))
            .getOrLoad(
                CacheRegion.PRODUCTS,
                () -> {
                  throw new AssertionError("DB must not be read on cache hit");
                });
    assertEquals(new BigDecimal("123456.78"), result.get(0).get("price"));
    verify(redis, never()).put(anyString(), anyString(), any());
  }

  @Test
  void missReadsDatabaseAndWritesWithTtl() {
    var expected = rows();
    var calls = new AtomicInteger();
    assertSame(
        expected,
        cache(true, Duration.ofSeconds(5))
            .getOrLoad(
                CacheRegion.PRODUCTS,
                () -> {
                  calls.incrementAndGet();
                  return expected;
                }));
    assertEquals(1, calls.get());
    verify(redis).put(eq(CacheRegion.PRODUCTS.key()), anyString(), eq(Duration.ofSeconds(10)));
  }

  @Test
  void connectionFailureFallsBackAndCooldownSkipsSubsequentRedisCalls() {
    when(redis.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
    var service = cache(true, Duration.ofSeconds(5));
    var count = new AtomicInteger();
    for (int n = 0; n < 3; n++)
      assertEquals(
          rows(),
          service.getOrLoad(
              CacheRegion.PRODUCTS,
              () -> {
                count.incrementAndGet();
                return rows();
              }));
    assertEquals(3, count.get());
    verify(redis, times(1)).get(anyString());
    verify(redis, never()).put(anyString(), anyString(), any());
  }

  @Test
  void commandTimeoutFallsBackWithoutSecondWriteTimeout() {
    when(redis.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
    assertEquals(
        rows(), cache(true, Duration.ofSeconds(5)).getOrLoad(CacheRegion.PRODUCTS, this::rows));
    verify(redis, never()).put(anyString(), anyString(), any());
  }

  @Test
  void cacheWriteFailureDoesNotFailSuccessfulDatabaseRead() {
    doThrow(new RedisConnectionFailureException("offline"))
        .when(redis)
        .put(anyString(), anyString(), any());
    assertEquals(
        rows(), cache(true, Duration.ofSeconds(5)).getOrLoad(CacheRegion.PRODUCTS, this::rows));
  }

  @Test
  void corruptJsonAndInvalidDatesFallBack() {
    when(redis.get(CacheRegion.PRODUCTS.key())).thenReturn("not-json");
    assertEquals(
        rows(), cache(true, Duration.ofSeconds(5)).getOrLoad(CacheRegion.PRODUCTS, this::rows));
    when(redis.get(CacheRegion.CAMPAIGNS.key()))
        .thenReturn(
            "{\"schema\":1,\"rows\":[{\"id\":1,\"name\":\"Sale\",\"status\":\"ACTIVE\",\"starts_at\":\"invalid\",\"ends_at\":null}]}");
    assertEquals(
        List.of(), cache(true, Duration.ofSeconds(5)).getOrLoad(CacheRegion.CAMPAIGNS, List::of));
  }

  @Test
  void databaseFailureIsNotSwallowedOrWrittenToCache() {
    when(redis.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
    var dbFailure = new IllegalStateException("database down");
    var thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                cache(true, Duration.ofSeconds(5))
                    .getOrLoad(
                        CacheRegion.PRODUCTS,
                        () -> {
                          throw dbFailure;
                        }));
    assertSame(dbFailure, thrown);
    verify(redis, never()).put(anyString(), anyString(), any());
  }

  @Test
  void disabledCacheNeverTouchesRedis() {
    assertEquals(
        rows(), cache(false, Duration.ofSeconds(5)).getOrLoad(CacheRegion.PRODUCTS, this::rows));
    verifyNoInteractions(redis);
  }

  @Test
  void invalidationFailureDoesNotEscapeAfterCommit() {
    doThrow(new RedisConnectionFailureException("offline")).when(redis).delete(anyCollection());
    assertDoesNotThrow(() -> cache(true, Duration.ofSeconds(5)).invalidatePublicMetadata());
  }

  @Test
  void onlyOneHalfOpenProbeAndSuccessfulProbeRecovers() throws Exception {
    var service = cache(true, Duration.ofMillis(20));
    when(redis.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
    service.getOrLoad(CacheRegion.PRODUCTS, this::rows);
    Thread.sleep(40);
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    doAnswer(
            call -> {
              started.countDown();
              assertTrue(finish.await(2, TimeUnit.SECONDS));
              return payload();
            })
        .when(redis)
        .get(anyString());
    var pool = Executors.newSingleThreadExecutor();
    try {
      var probe = pool.submit(() -> service.getOrLoad(CacheRegion.PRODUCTS, this::rows));
      assertTrue(started.await(2, TimeUnit.SECONDS));
      assertEquals(rows(), service.getOrLoad(CacheRegion.PRODUCTS, this::rows));
      verify(redis, times(2)).get(anyString());
      finish.countDown();
      assertEquals("Phone", probe.get().get(0).get("name"));
      service.getOrLoad(
          CacheRegion.PRODUCTS,
          () -> {
            throw new AssertionError("must recover cache hit");
          });
      verify(redis, times(3)).get(anyString());
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
  }
}
