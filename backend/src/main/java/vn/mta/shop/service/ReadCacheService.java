package vn.mta.shop.service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import vn.mta.shop.cache.CacheRegion;

public interface ReadCacheService {
  List<Map<String, Object>> getOrLoad(
      CacheRegion region, Supplier<List<Map<String, Object>>> databaseLoader);

  void invalidatePublicMetadata();
}
