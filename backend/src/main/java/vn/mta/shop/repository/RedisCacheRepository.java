package vn.mta.shop.repository;

import java.time.Duration;
import java.util.Collection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Adapter Redis: không chứa fallback database hoặc quyết định nghiệp vụ. */
@Repository
public class RedisCacheRepository {
  private final StringRedisTemplate redis;

  public RedisCacheRepository(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public String get(String key) {
    return redis.opsForValue().get(key);
  }

  public void put(String key, String json, Duration ttl) {
    redis.opsForValue().set(key, json, ttl);
  }

  public void delete(Collection<String> keys) {
    redis.delete(keys);
  }
}
