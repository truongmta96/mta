package vn.mta.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Order; transaction do ServiceImpl quản lý. */
@Repository
public class OrderRepository {
  private final JdbcTemplate jdbc;

  public OrderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchForAdmin(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT o.id,c.full_name AS customer,o.status,o.total,o.created_at FROM orders o JOIN"
            + " customers c ON c.id=o.customer_id WHERE c.full_name LIKE ? ESCAPE '!' ORDER BY o.id"
            + " DESC LIMIT 50 OFFSET ?",
        prefix,
        offset);
  }

  public List<Map<String, Object>> findExpiredBatchForUpdate() {
    return jdbc.queryForList(
        "SELECT id FROM orders WHERE status='PENDING_PAYMENT' AND expires_at<=UTC_TIMESTAMP() ORDER"
            + " BY expires_at,id LIMIT 100 FOR UPDATE SKIP LOCKED");
  }

  public Integer countByCampaign(long campaignId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM orders WHERE campaign_id=?", Integer.class, campaignId);
  }

  public List<Map<String, Object>> findByIdempotencyKey(long customerId, String key) {
    return jdbc.queryForList(
        "SELECT id,status,total,request_hash FROM orders WHERE customer_id=? AND idempotency_key=?",
        customerId,
        key);
  }

  public int insert(
      long customerId,
      String key,
      String hash,
      BigDecimal total,
      String recipient,
      String phone,
      String address,
      Long campaignId) {
    return jdbc.update(
        "INSERT INTO"
            + " orders(customer_id,idempotency_key,request_hash,status,total,recipient,phone,address,expires_at,campaign_id)"
            + " VALUES(?,?,?,'PENDING_PAYMENT',?,?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15"
            + " MINUTE),?)",
        customerId,
        key,
        hash,
        total,
        recipient,
        phone,
        address,
        campaignId);
  }

  public int insertItem(long orderId, long skuId, String name, int quantity, BigDecimal price) {
    return jdbc.update(
        "INSERT INTO order_items(order_id,sku_id,product_name,quantity,unit_price)"
            + " VALUES(?,?,?,?,?)",
        orderId,
        skuId,
        name,
        quantity,
        price);
  }

  public List<Map<String, Object>> findOwned(long orderId, long customerId) {
    return jdbc.queryForList(
        "SELECT * FROM orders WHERE id=? AND customer_id=?", orderId, customerId);
  }

  public List<Map<String, Object>> findForUpdate(long orderId) {
    return jdbc.queryForList("SELECT * FROM orders WHERE id=? FOR UPDATE", orderId);
  }

  public List<Map<String, Object>> findItems(long orderId) {
    return jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?", orderId);
  }

  public int markCancelled(long orderId) {
    return jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=?", orderId);
  }

  public int markExpired(long orderId) {
    return jdbc.update("UPDATE orders SET status='EXPIRED' WHERE id=?", orderId);
  }

  public int markPaid(long orderId) {
    return jdbc.update("UPDATE orders SET status='PAID' WHERE id=?", orderId);
  }

  public int updateStatus(String status, long orderId) {
    return jdbc.update("UPDATE orders SET status=? WHERE id=?", status, orderId);
  }

  public List<Map<String, Object>> findPendingForCustomerForUpdate(long customerId) {
    return jdbc.queryForList(
        "SELECT id FROM orders WHERE customer_id=? AND status='PENDING_PAYMENT' ORDER BY id FOR"
            + " UPDATE",
        customerId);
  }

  public int markRefunded(long orderId) {
    return jdbc.update("UPDATE orders SET status='REFUNDED' WHERE id=?", orderId);
  }

  public List<Map<String, Object>> findCustomerHistory(long customerId) {
    return jdbc.queryForList(
        "SELECT id,status,total,created_at,expires_at,campaign_id FROM orders WHERE customer_id=?"
            + " ORDER BY id DESC LIMIT 100",
        customerId);
  }
}
