package vn.mta.shop.service;

import java.util.Map;
import vn.mta.shop.dto.PlaceOrderRequest;

/** Hợp đồng nghiệp vụ; controller và service khác chỉ phụ thuộc interface này. */
public interface OrderService {
  Map<String, Object> place(long customer, String key, PlaceOrderRequest input) throws Exception;

  Map<String, Object> owned(long id, long customer);

  /** Chỉ gọi trong transaction nghiệp vụ hiện có; khóa order đến khi commit/rollback. */
  Map<String, Object> lock(long id);

  boolean expired(Map<String, Object> order);

  Map<String, Object> detail(long id);

  Map<String, Object> customerDetail(long id, long customer);

  void cancel(long id, Long customer);

  /** Các thao tác vòng đời bên dưới tham gia transaction của caller. */
  void expireLocked(Map<String, Object> order);

  void release(Map<String, Object> order, boolean returnReceived);

  void paid(Map<String, Object> order);

  void progress(long id, String next);

  void cancelPendingCustomer(long customer);

  void event(long id, String type);
}
