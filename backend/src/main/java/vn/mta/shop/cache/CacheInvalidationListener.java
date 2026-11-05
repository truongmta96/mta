package vn.mta.shop.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.mta.shop.service.ReadCacheService;

@Component
public class CacheInvalidationListener {
  private final ReadCacheService cache;

  public CacheInvalidationListener(ReadCacheService cache) {
    this.cache = cache;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(PublicCatalogChanged event) {
    cache.invalidatePublicMetadata();
  }
}
