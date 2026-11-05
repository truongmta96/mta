package vn.mta.shop.service;

import java.util.Map;

/** Hợp đồng nghiệp vụ; controller và service khác chỉ phụ thuộc interface này. */
public interface PaymentService {
  Map<String, Object> config();

  Map<String, Object> create(long id, long customer);

  Map<String, Object> completeSandbox(long attempt, String email, String result) throws Exception;

  Map<String, Object> webhook(String payload, String signature) throws Exception;

  Map<String, Object> requestRefund(long id, Long customer, String reason);

  void decideRefund(long refund, boolean approve, boolean returnReceived);
}
