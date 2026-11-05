package vn.mta.shop.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu System; transaction do ServiceImpl quản lý. */
@Repository
public class SystemRepository {
  private final JdbcTemplate jdbc;

  public SystemRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Integer ping() {
    return jdbc.queryForObject("SELECT 1", Integer.class);
  }
}
