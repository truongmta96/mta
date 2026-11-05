package vn.mta.shop.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Identity; transaction do ServiceImpl quản lý. */
@Repository
public class IdentityRepository {
  private final JdbcTemplate jdbc;

  public IdentityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Long lastInsertedId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }
}
