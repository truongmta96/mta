package vn.mta.shop.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Outbox; transaction do ServiceImpl quản lý. */
@Repository
public class OutboxRepository {
  private final JdbcTemplate jdbc;

  public OutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> findUnpublishedBatch() {
    return jdbc.queryForList(
        "SELECT id,aggregate_id,event_type,payload FROM outbox_events WHERE published_at IS NULL"
            + " ORDER BY id LIMIT 100");
  }

  public int markPublished(long eventId) {
    return jdbc.update("UPDATE outbox_events SET published_at=UTC_TIMESTAMP() WHERE id=?", eventId);
  }

  public int insertOrderEvent(long orderId, String eventType) {
    return jdbc.update(
        "INSERT INTO outbox_events(aggregate_id,event_type,payload)"
            + " VALUES(?,?,JSON_OBJECT('orderId',?))",
        orderId,
        eventType,
        orderId);
  }
}
