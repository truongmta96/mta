package vn.mta.shop.service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import vn.mta.shop.cache.CacheRegion;

/** Hợp đồng cache đọc: consumer không cần biết GET/SET Redis hoặc cách xử lý timeout. */
public interface ReadCacheService {
  // Ưu tiên cache; miss/lỗi thì gọi databaseLoader. Lỗi DB không bị che đi.
  List<Map<String, Object>> getOrLoad(
      CacheRegion region, Supplier<List<Map<String, Object>>> databaseLoader);

  // Vô hiệu hóa metadata sau commit; lỗi Redis không được làm thất bại kết quả ghi DB.
  void invalidatePublicMetadata();
}
