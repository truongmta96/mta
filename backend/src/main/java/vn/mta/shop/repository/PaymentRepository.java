package vn.mta.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Payment; transaction do ServiceImpl quản lý. */
@Repository
public class PaymentRepository {
  private final JdbcTemplate jdbc;

  public PaymentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> findHistoryByOrder(long orderId) {
    return jdbc.queryForList(
        "SELECT id,provider,status,amount,created_at FROM payment_attempts WHERE order_id=? ORDER"
            + " BY id DESC",
        orderId);
  }

  public List<Map<String, Object>> findPendingForOrder(long orderId) {
    return jdbc.queryForList(
        "SELECT id,provider,status,amount FROM payment_attempts WHERE order_id=? AND"
            + " provider='SANDBOX' AND status='PENDING' ORDER BY id DESC LIMIT 1",
        orderId);
  }

  public int insertSandbox(long orderId, String reference, BigDecimal amount) {
    return jdbc.update(
        "INSERT INTO payment_attempts(order_id,provider,provider_reference,status,amount)"
            + " VALUES(?,'SANDBOX',?,'PENDING',?)",
        orderId,
        reference,
        amount);
  }

  public List<Map<String, Object>> findOwnedSandbox(long paymentId, long customerId) {
    return jdbc.queryForList(
        "SELECT p.* FROM payment_attempts p JOIN orders o ON o.id=p.order_id WHERE p.id=? AND"
            + " o.customer_id=? AND p.provider='SANDBOX'",
        paymentId,
        customerId);
  }

  public List<Map<String, Object>> findOrderByReference(String reference) {
    return jdbc.queryForList(
        "SELECT order_id FROM payment_attempts WHERE provider_reference=? AND provider='SANDBOX'",
        reference);
  }

  public Map<String, Object> findByReferenceForUpdate(String reference) {
    return jdbc.queryForMap(
        "SELECT * FROM payment_attempts WHERE provider_reference=? FOR UPDATE", reference);
  }

  public List<Map<String, Object>> findWebhookForUpdate(String eventId) {
    return jdbc.queryForList(
        "SELECT payload FROM payment_webhook_events WHERE provider='SANDBOX' AND event_id=? FOR"
            + " UPDATE",
        eventId);
  }

  public int insertWebhook(String eventId, String payload) {
    return jdbc.update(
        "INSERT INTO payment_webhook_events(provider,event_id,payload,status)"
            + " VALUES('SANDBOX',?,?,'PROCESSED')",
        eventId,
        payload);
  }

  public int markFailed(long paymentId) {
    return jdbc.update("UPDATE payment_attempts SET status='FAILED' WHERE id=?", paymentId);
  }

  public int markSucceeded(long paymentId) {
    return jdbc.update("UPDATE payment_attempts SET status='SUCCEEDED' WHERE id=?", paymentId);
  }

  public int markRefunded(long paymentId) {
    return jdbc.update("UPDATE payment_attempts SET status='REFUNDED' WHERE id=?", paymentId);
  }

  public List<Map<String, Object>> findSucceededSandboxForOrder(long orderId) {
    return jdbc.queryForList(
        "SELECT id,amount FROM payment_attempts WHERE order_id=? AND status='SUCCEEDED' AND"
            + " provider='SANDBOX'",
        orderId);
  }
}
