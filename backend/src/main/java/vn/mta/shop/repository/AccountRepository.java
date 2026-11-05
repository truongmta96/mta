package vn.mta.shop.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Account; transaction do ServiceImpl quản lý. */
@Repository
public class AccountRepository {
  private final JdbcTemplate jdbc;

  public AccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchForAdmin(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT"
            + " a.id,a.email,a.role,a.status,a.deleted_at,a.created_at,COALESCE(c.full_name,a.display_name,'Quản"
            + " trị viên') AS name FROM accounts a LEFT JOIN customers c ON c.account_id=a.id WHERE"
            + " a.email LIKE ? ESCAPE '!' ORDER BY a.id DESC LIMIT 50 OFFSET ?",
        prefix,
        offset);
  }

  public List<Map<String, Object>> findActiveAdminsForUpdate() {
    return jdbc.queryForList(
        "SELECT id FROM accounts WHERE role='ADMIN' AND status='ACTIVE' AND deleted_at IS NULL"
            + " ORDER BY id FOR UPDATE");
  }

  public int insertManaged(
      String email, String passwordHash, String role, String status, String displayName) {
    return jdbc.update(
        "INSERT INTO accounts(email,password_hash,role,status,display_name) VALUES(?,?,?,?,?)",
        email,
        passwordHash,
        role,
        status,
        displayName);
  }

  public List<Map<String, Object>> findDeletionStateForUpdate(long accountId) {
    return jdbc.queryForList("SELECT id,deleted_at FROM accounts WHERE id=? FOR UPDATE", accountId);
  }

  public int updateManaged(
      String email, String role, String status, String displayName, long accountId) {
    return jdbc.update(
        "UPDATE accounts SET"
            + " email=?,role=?,status=?,display_name=?,session_version=session_version+1 WHERE"
            + " id=?",
        email,
        role,
        status,
        displayName,
        accountId);
  }

  public int updatePassword(String passwordHash, long accountId) {
    return jdbc.update("UPDATE accounts SET password_hash=? WHERE id=?", passwordHash, accountId);
  }

  public List<Map<String, Object>> findRoleAndStatus(long accountId) {
    return jdbc.queryForList("SELECT role,status FROM accounts WHERE id=?", accountId);
  }

  public List<Map<String, Object>> lockById(long accountId) {
    return jdbc.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", accountId);
  }

  public int setDeleted(boolean deleted, String status, long accountId) {
    return jdbc.update(
        "UPDATE accounts SET deleted_at="
            + (deleted ? "UTC_TIMESTAMP()" : "NULL")
            + ",status=?,session_version=session_version+1 WHERE id=?",
        status,
        accountId);
  }

  public Map<String, Object> findState(long accountId) {
    return jdbc.queryForMap("SELECT role,status,deleted_at FROM accounts WHERE id=?", accountId);
  }

  public int lockAndRevokeSessions(long accountId) {
    return jdbc.update(
        "UPDATE accounts SET status='LOCKED',session_version=session_version+1 WHERE id=?",
        accountId);
  }

  public Map<String, Object> findCredentialsForUpdate(String email) {
    return jdbc.queryForMap(
        "SELECT id,password_hash,role FROM accounts WHERE email=? FOR UPDATE", email);
  }

  public List<Map<String, Object>> findByEmail(String email) {
    return jdbc.queryForList(
        "SELECT id,email,password_hash,role,status,deleted_at,session_version,display_name FROM"
            + " accounts WHERE email=?",
        email);
  }

  public int insertRegistered(String email, String passwordHash) {
    return jdbc.update(
        "INSERT INTO accounts(email,password_hash,role,status) VALUES(?,?,'CUSTOMER','ACTIVE')",
        email,
        passwordHash);
  }

  public Long findIdByEmail(String email) {
    return jdbc.queryForObject("SELECT id FROM accounts WHERE email=?", Long.class, email);
  }

  public Integer countByEmail(String email) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE email=?", Integer.class, email);
  }

  public int insertDemo(String email, String passwordHash, String role) {
    return jdbc.update(
        "INSERT INTO accounts(email,password_hash,role) VALUES(?,?,?)", email, passwordHash, role);
  }
}
