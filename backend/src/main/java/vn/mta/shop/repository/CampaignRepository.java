package vn.mta.shop.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.mta.shop.model.QuotaCounter;

/** Truy cập dữ liệu Campaign; transaction do ServiceImpl quản lý. */
@Repository
public class CampaignRepository {
  private final JdbcTemplate jdbc;

  public CampaignRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchForAdmin(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT * FROM sale_campaigns WHERE name LIKE ? ESCAPE '!' ORDER BY id DESC LIMIT 50 OFFSET"
            + " ?",
        prefix,
        offset);
  }

  public List<Map<String, Object>> findItems(long campaignId) {
    return jdbc.queryForList("SELECT * FROM sale_items WHERE campaign_id=?", campaignId);
  }

  public List<Map<String, Object>> findActiveRecordForUpdate(long campaignId) {
    return jdbc.queryForList(
        "SELECT id FROM sale_campaigns WHERE id=? AND deleted_at IS NULL FOR UPDATE", campaignId);
  }

  public Integer countOverlapping(
      long skuId, long excludedCampaignId, LocalDateTime endsAt, LocalDateTime startsAt) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM sale_items si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE"
            + " si.sku_id=? AND c.id<>? AND c.deleted_at IS NULL AND c.status IN"
            + " ('SCHEDULED','ACTIVE') AND c.starts_at<? AND c.ends_at>?",
        Integer.class,
        skuId,
        excludedCampaignId,
        endsAt,
        startsAt);
  }

  public int insert(String name, LocalDateTime startsAt, LocalDateTime endsAt, String status) {
    return jdbc.update(
        "INSERT INTO sale_campaigns(name,starts_at,ends_at,status) VALUES(?,?,?,?)",
        name,
        startsAt,
        endsAt,
        status);
  }

  public int update(
      String name, LocalDateTime startsAt, LocalDateTime endsAt, String status, long campaignId) {
    return jdbc.update(
        "UPDATE sale_campaigns SET name=?,starts_at=?,ends_at=?,status=? WHERE id=?",
        name,
        startsAt,
        endsAt,
        status,
        campaignId);
  }

  public int deleteItems(long campaignId) {
    return jdbc.update("DELETE FROM sale_items WHERE campaign_id=?", campaignId);
  }

  public int insertItem(
      long campaignId, long skuId, BigDecimal price, int quota, int perCustomerLimit) {
    return jdbc.update(
        "INSERT INTO sale_items(campaign_id,sku_id,sale_price,quota,per_customer_limit)"
            + " VALUES(?,?,?,?,?)",
        campaignId,
        skuId,
        price,
        quota,
        perCustomerLimit);
  }

  public List<Map<String, Object>> findSkuIds(long campaignId) {
    return jdbc.queryForList(
        "SELECT sku_id FROM sale_items WHERE campaign_id=? ORDER BY sku_id", campaignId);
  }

  public int updateStatus(String status, long campaignId) {
    return jdbc.update(
        "UPDATE sale_campaigns SET status=? WHERE id=? AND deleted_at IS NULL", status, campaignId);
  }

  public int markDeleted(long campaignId) {
    return jdbc.update(
        "UPDATE sale_campaigns SET deleted_at=UTC_TIMESTAMP() WHERE id=?", campaignId);
  }

  public List<Map<String, Object>> findCurrentForSkuForUpdate(long skuId) {
    return jdbc.queryForList(
        "SELECT si.*,c.id FROM sale_items si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE"
            + " si.sku_id=? AND c.deleted_at IS NULL AND c.status IN ('ACTIVE','SCHEDULED') AND"
            + " c.starts_at<=UTC_TIMESTAMP() AND c.ends_at>UTC_TIMESTAMP() ORDER BY c.id FOR"
            + " UPDATE",
        skuId);
  }

  public int reserveItem(int quantity, long campaignId, long skuId) {
    return jdbc.update(
        "UPDATE sale_items SET reserved=reserved+? WHERE campaign_id=? AND sku_id=? AND"
            + " quota-reserved-sold>=?",
        quantity,
        campaignId,
        skuId,
        quantity);
  }

  public int ensureCustomerQuota(long campaignId, long skuId, long customerId) {
    return jdbc.update(
        "INSERT INTO sale_customer_quotas(campaign_id,sku_id,customer_id) VALUES(?,?,?) ON"
            + " DUPLICATE KEY UPDATE customer_id=customer_id",
        campaignId,
        skuId,
        customerId);
  }

  public int reserveCustomerQuota(
      int quantity, long campaignId, long skuId, long customerId, int limit) {
    return jdbc.update(
        "UPDATE sale_customer_quotas SET reserved=reserved+? WHERE campaign_id=? AND sku_id=? AND"
            + " customer_id=? AND reserved+sold+?<=?",
        quantity,
        campaignId,
        skuId,
        customerId,
        quantity,
        limit);
  }

  public int releaseItemCounter(QuotaCounter field, int quantity, long campaignId, long skuId) {
    return jdbc.update(
        "UPDATE sale_items SET " + field + "=" + field + "-? WHERE campaign_id=? AND sku_id=?",
        quantity,
        campaignId,
        skuId);
  }

  public int releaseCustomerCounter(
      QuotaCounter field, int quantity, long campaignId, long skuId, long customerId) {
    return jdbc.update(
        "UPDATE sale_customer_quotas SET "
            + field
            + "="
            + field
            + "-? WHERE campaign_id=? AND sku_id=? AND customer_id=?",
        quantity,
        campaignId,
        skuId,
        customerId);
  }

  public int confirmItem(int quantity, long campaignId, long skuId) {
    return jdbc.update(
        "UPDATE sale_items SET reserved=reserved-?,sold=sold+? WHERE campaign_id=? AND sku_id=?",
        quantity,
        quantity,
        campaignId,
        skuId);
  }

  public int confirmCustomerQuota(int quantity, long campaignId, long skuId, long customerId) {
    return jdbc.update(
        "UPDATE sale_customer_quotas SET reserved=reserved-?,sold=sold+? WHERE campaign_id=? AND"
            + " sku_id=? AND customer_id=?",
        quantity,
        quantity,
        campaignId,
        skuId,
        customerId);
  }

  public List<Map<String, Object>> findCurrentPriceForSku(long skuId) {
    return jdbc.queryForList(
        "SELECT c.id,c.name,si.sale_price,si.quota-si.reserved-si.sold AS remaining FROM sale_items"
            + " si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE si.sku_id=? AND c.deleted_at"
            + " IS NULL AND c.status IN ('ACTIVE','SCHEDULED') AND c.starts_at<=UTC_TIMESTAMP() AND"
            + " c.ends_at>UTC_TIMESTAMP() ORDER BY c.id LIMIT 1",
        skuId);
  }

  public List<Map<String, Object>> findPublicUpcoming() {
    return jdbc.queryForList(
        "SELECT id,name,starts_at,ends_at,status,UTC_TIMESTAMP() AS server_time FROM sale_campaigns"
            + " WHERE deleted_at IS NULL AND status IN ('SCHEDULED','ACTIVE') AND"
            + " ends_at>UTC_TIMESTAMP() ORDER BY starts_at,id LIMIT 30");
  }

  public List<Map<String, Object>> findPublicItems(long campaignId) {
    return jdbc.queryForList(
        "SELECT si.*,p.name,s.storage_gb,s.color,i.on_hand-i.reserved AS stock_available FROM"
            + " sale_items si JOIN product_skus s ON s.id=si.sku_id JOIN products p ON"
            + " p.id=s.product_id JOIN inventory i ON i.sku_id=s.id WHERE si.campaign_id=? AND"
            + " p.deleted_at IS NULL AND p.status='ACTIVE'",
        campaignId);
  }

  // Chỉ header chiến dịch được cache; item/quota/tồn được service đọc DB riêng trên mỗi request.
  public List<Map<String, Object>> findPublicMetadata() {
    return jdbc.queryForList(
        "SELECT id,name,starts_at,ends_at,status FROM sale_campaigns WHERE deleted_at IS NULL AND"
            + " status IN ('SCHEDULED','ACTIVE') AND ends_at>UTC_TIMESTAMP() ORDER BY starts_at,id"
            + " LIMIT 30");
  }
}
