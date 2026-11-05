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

  // GET trả null nếu key không có/hết TTL. Lỗi/timeout được ném lên ReadCacheServiceImpl để fallback.
  public String get(String key) {
    return redis.opsForValue().get(key);
  }

  // SET kèm TTL trong cùng một lệnh, tránh tạo key không hết hạn do quên EXPIRE riêng.
  public void put(String key, String json, Duration ttl) {
    redis.opsForValue().set(key, json, ttl);
  }

  // Xóa đúng tập key của catalog/sale; không đụng dữ liệu Redis khác bằng FLUSHALL.
  public void delete(Collection<String> keys) {
    redis.delete(keys);
  }
}
