package vn.mta.shop.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Audit; transaction do ServiceImpl quản lý. */
@Repository
public class AuditRepository {
  private final JdbcTemplate jdbc;

  public AuditRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchRecent(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT id,created_at,actor_id,action,entity_type,entity_id FROM audit_logs WHERE"
            + " created_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 DAY) AND action LIKE ? ESCAPE '!'"
            + " ORDER BY created_at DESC,id DESC LIMIT 50 OFFSET ?",
        prefix,
        offset);
  }

  public int insert(long actorId, String action, String entityType, long entityId) {
    return jdbc.update(
        "INSERT INTO audit_logs(created_at,actor_id,action,entity_type,entity_id,details)"
            + " VALUES(UTC_TIMESTAMP(),?,?,?,?,JSON_OBJECT())",
        actorId,
        action,
        entityType,
        entityId);
  }
}
