package vn.mta.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Refund; transaction do ServiceImpl quản lý. */
@Repository
public class RefundRepository {
  private final JdbcTemplate jdbc;

  public RefundRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchForAdmin(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT r.*,c.full_name AS customer FROM refunds r JOIN orders o ON o.id=r.order_id JOIN"
            + " customers c ON c.id=o.customer_id WHERE c.full_name LIKE ? ESCAPE '!' ORDER BY r.id"
            + " DESC LIMIT 50 OFFSET ?",
        prefix,
        offset);
  }

  public List<Map<String, Object>> findByOrder(long orderId) {
    return jdbc.queryForList("SELECT * FROM refunds WHERE order_id=?", orderId);
  }

  public Integer countRequestedForOrder(long orderId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM refunds WHERE order_id=? AND status='REQUESTED'",
        Integer.class,
        orderId);
  }

  public int insertLatePayment(long orderId, long paymentId, BigDecimal amount) {
    return jdbc.update(
        "INSERT INTO refunds(order_id,payment_attempt_id,amount,status,reason)"
            + " VALUES(?,?,?,'SUCCEEDED','Tự hoàn sandbox: thanh toán đến sau khi đơn kết thúc')",
        orderId,
        paymentId,
        amount);
  }

  public int reopen(String reason, long refundId) {
    return jdbc.update(
        "UPDATE refunds SET status='REQUESTED',reason=? WHERE id=?", reason, refundId);
  }

  public Map<String, Object> getById(long refundId) {
    return jdbc.queryForMap("SELECT * FROM refunds WHERE id=?", refundId);
  }

  public int insertRequest(long orderId, long paymentId, BigDecimal amount, String reason) {
    return jdbc.update(
        "INSERT INTO refunds(order_id,payment_attempt_id,amount,status,reason)"
            + " VALUES(?,?,?,'REQUESTED',?)",
        orderId,
        paymentId,
        amount,
        reason);
  }

  public Map<String, Object> getByOrder(long orderId) {
    return jdbc.queryForMap("SELECT * FROM refunds WHERE order_id=?", orderId);
  }

  public List<Map<String, Object>> findOrderId(long refundId) {
    return jdbc.queryForList("SELECT order_id FROM refunds WHERE id=?", refundId);
  }

  public Map<String, Object> findForUpdate(long refundId) {
    return jdbc.queryForMap("SELECT * FROM refunds WHERE id=? FOR UPDATE", refundId);
  }

  public int markRejected(long refundId) {
    return jdbc.update("UPDATE refunds SET status='REJECTED' WHERE id=?", refundId);
  }

  public int markSucceeded(boolean returnReceived, long refundId) {
    return jdbc.update(
        "UPDATE refunds SET status='SUCCEEDED',return_received=? WHERE id=?",
        returnReceived,
        refundId);
  }
}
