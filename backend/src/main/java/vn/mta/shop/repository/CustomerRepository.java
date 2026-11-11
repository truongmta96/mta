package vn.mta.shop.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Customer; transaction do ServiceImpl quản lý. */
@Repository
public class CustomerRepository {
  private final JdbcTemplate jdbc;

  public CustomerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchForAdmin(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT c.id,c.full_name,c.phone,a.email,c.deleted_at FROM customers c LEFT JOIN accounts a"
            + " ON a.id=c.account_id WHERE c.full_name LIKE ? ESCAPE '!' ORDER BY c.id DESC LIMIT"
            + " 50 OFFSET ?",
        prefix,
        offset);
  }

  public List<Map<String, Object>> findIdsByAccount(long accountId) {
    return jdbc.queryForList("SELECT id FROM customers WHERE account_id=?", accountId);
  }

  public int insert(long accountId, String name) {
    return jdbc.update("INSERT INTO customers(account_id,full_name) VALUES(?,?)", accountId, name);
  }

  public int updateNameByAccount(String name, long accountId) {
    return jdbc.update("UPDATE customers SET full_name=? WHERE account_id=?", name, accountId);
  }

  public List<Map<String, Object>> findIdsByAccountForUpdate(long accountId) {
    return jdbc.queryForList("SELECT id FROM customers WHERE account_id=? FOR UPDATE", accountId);
  }

  public int setDeleted(boolean deleted, long customerId) {
    return jdbc.update(
        "UPDATE customers SET deleted_at=" + (deleted ? "UTC_TIMESTAMP()" : "NULL") + " WHERE id=?",
        customerId);
  }

  public int updateActive(String name, String phone, long customerId) {
    return jdbc.update(
        "UPDATE customers SET full_name=?,phone=? WHERE id=? AND deleted_at IS NULL",
        name,
        phone,
        customerId);
  }

  public List<Map<String, Object>> findAccountId(long customerId) {
    return jdbc.queryForList("SELECT account_id FROM customers WHERE id=?", customerId);
  }

  public List<Map<String, Object>> lockById(long customerId) {
    return jdbc.queryForList("SELECT id FROM customers WHERE id=? FOR UPDATE", customerId);
  }

  public List<Map<String, Object>> lockActiveById(long customerId) {
    return jdbc.queryForList(
        "SELECT id FROM customers WHERE id=? AND deleted_at IS NULL FOR UPDATE", customerId);
  }

  public Map<String, Object> getProfile(long customerId) {
    return jdbc.queryForMap(
        "SELECT c.id,c.full_name,c.phone,a.email FROM customers c JOIN accounts a ON"
            + " a.id=c.account_id WHERE c.id=?",
        customerId);
  }

  public int updateProfile(String name, String phone, long customerId) {
    return jdbc.update(
        "UPDATE customers SET full_name=?,phone=? WHERE id=?", name, phone, customerId);
  }

  // Email và account_id là UNIQUE: truy vấn trả tối đa một ID, không cần Map.
  // Không có hồ sơ hợp lệ trả Optional.empty(); lỗi DB vẫn được truyền lên.
  public Optional<Long> findActiveIdByEmail(String email) {
    return DataAccessUtils.optionalResult(
        jdbc.queryForList(
            "SELECT c.id FROM customers c JOIN accounts a ON a.id=c.account_id WHERE a.email=? AND"
                + " a.status='ACTIVE' AND a.deleted_at IS NULL AND c.deleted_at IS NULL",
            Long.class,
            email));
  }

  public List<Map<String, Object>> findActiveNameByAccount(long accountId) {
    return jdbc.queryForList(
        "SELECT full_name FROM customers WHERE account_id=? AND deleted_at IS NULL", accountId);
  }
}
