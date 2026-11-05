package vn.mta.shop.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Inventory; transaction do ServiceImpl quản lý. */
@Repository
public class InventoryRepository {
  private final JdbcTemplate jdbc;

  public InventoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int insert(long skuId, int onHand) {
    return jdbc.update("INSERT INTO inventory(sku_id,on_hand) VALUES(?,?)", skuId, onHand);
  }

  public int updateOnHand(int onHand, long skuId) {
    return jdbc.update("UPDATE inventory SET on_hand=? WHERE sku_id=?", onHand, skuId);
  }

  public List<Map<String, Object>> lockBySku(long skuId) {
    return jdbc.queryForList("SELECT sku_id FROM inventory WHERE sku_id=? FOR UPDATE", skuId);
  }

  public int reserve(int quantity, long skuId) {
    return jdbc.update(
        "UPDATE inventory SET reserved=reserved+? WHERE sku_id=? AND on_hand-reserved>=?",
        quantity,
        skuId,
        quantity);
  }

  public int insertReservation(long orderId, long skuId, int quantity) {
    return jdbc.update(
        "INSERT INTO inventory_reservations(order_id,sku_id,quantity,status,expires_at)"
            + " VALUES(?,?,?,'HELD',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE))",
        orderId,
        skuId,
        quantity);
  }

  public List<Map<String, Object>> findReleasableReservations(long orderId) {
    return jdbc.queryForList(
        "SELECT * FROM inventory_reservations WHERE order_id=? AND status IN"
            + " ('HELD','CONFIRMED','FULFILLED') ORDER BY sku_id",
        orderId);
  }

  public int returnStock(int quantity, long skuId) {
    return jdbc.update("UPDATE inventory SET on_hand=on_hand+? WHERE sku_id=?", quantity, skuId);
  }

  public int release(int quantity, long skuId) {
    return jdbc.update("UPDATE inventory SET reserved=reserved-? WHERE sku_id=?", quantity, skuId);
  }

  public int updateReservationStatus(String status, long reservationId) {
    return jdbc.update(
        "UPDATE inventory_reservations SET status=? WHERE id=?", status, reservationId);
  }

  public List<Map<String, Object>> findHeldReservations(long orderId) {
    return jdbc.queryForList(
        "SELECT * FROM inventory_reservations WHERE order_id=? AND status='HELD' ORDER BY sku_id",
        orderId);
  }

  public int confirmReservation(long reservationId) {
    return jdbc.update(
        "UPDATE inventory_reservations SET status='CONFIRMED' WHERE id=?", reservationId);
  }

  public List<Map<String, Object>> findConfirmedReservations(long orderId) {
    return jdbc.queryForList(
        "SELECT * FROM inventory_reservations WHERE order_id=? AND status='CONFIRMED' ORDER BY"
            + " sku_id",
        orderId);
  }

  public int ship(int quantity, long skuId) {
    return jdbc.update(
        "UPDATE inventory SET on_hand=on_hand-?,reserved=reserved-? WHERE sku_id=?",
        quantity,
        quantity,
        skuId);
  }

  public int fulfillReservation(long reservationId) {
    return jdbc.update(
        "UPDATE inventory_reservations SET status='FULFILLED' WHERE id=?", reservationId);
  }

  // Metadata có thể lấy từ cache, nhưng tồn khả dụng luôn đọc DB theo lô SKU để không bị đóng băng.
  public List<Map<String, Object>> findAvailableForSkus(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    // Chỉ tạo số lượng dấu ?; ID vẫn bind qua tham số, không nối giá trị ID vào câu SQL.
    String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    return jdbc.queryForList(
        "SELECT sku_id,on_hand-reserved AS available FROM inventory WHERE sku_id IN ("
            + placeholders
            + ")",
        ids.toArray());
  }
}
