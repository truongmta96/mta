package vn.mta.shop.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.mta.shop.service.ReadCacheService;

/** Adapter sự kiện: chỉ yêu cầu xóa cache sau khi dữ liệu chính đã được lưu chắc chắn. */
@Component
public class CacheInvalidationListener {
  private final ReadCacheService cache;

  public CacheInvalidationListener(ReadCacheService cache) {
    this.cache = cache;
  }

  // AFTER_COMMIT: nếu transaction rollback thì listener này không chạy.
  // Không xóa trước commit, vì request khác có thể nạp lại dữ liệu DB cũ vào cache.
  // Cơ chế vẫn có tính nhất quán cuối cùng; TTL giới hạn cửa sổ stale của cache-aside.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(PublicCatalogChanged event) {
    cache.invalidatePublicMetadata();
  }
}
