package vn.mta.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập dữ liệu Product; transaction do ServiceImpl quản lý. */
@Repository
public class ProductRepository {
  private final JdbcTemplate jdbc;

  public ProductRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> searchForAdmin(String prefix, int offset) {
    return jdbc.queryForList(
        "SELECT"
            + " s.id,p.name,p.brand,p.description,p.deleted_at,s.storage_gb,s.color,s.price,i.on_hand,i.reserved,i.on_hand-i.reserved"
            + " AS available FROM product_skus s JOIN products p ON p.id=s.product_id JOIN"
            + " inventory i ON i.sku_id=s.id WHERE p.name LIKE ? ESCAPE '!' ORDER BY s.id DESC"
            + " LIMIT 50 OFFSET ?",
        prefix,
        offset);
  }

  public int insertProduct(String name, String brand, String description, String color) {
    return jdbc.update(
        "INSERT INTO products(name,brand,description,color) VALUES(?,?,?,?)",
        name,
        brand,
        description,
        color);
  }

  public int insertSku(long productId, String code, int storageGb, String color, BigDecimal price) {
    return jdbc.update(
        "INSERT INTO product_skus(product_id,code,storage_gb,color,price) VALUES(?,?,?,?,?)",
        productId,
        code,
        storageGb,
        color,
        price);
  }

  public List<Map<String, Object>> findSkuInventoryForUpdate(long skuId) {
    return jdbc.queryForList(
        "SELECT s.product_id,i.reserved FROM product_skus s JOIN inventory i ON i.sku_id=s.id WHERE"
            + " s.id=? FOR UPDATE",
        skuId);
  }

  public int updateProduct(
      String name, String brand, String description, String color, long productId) {
    return jdbc.update(
        "UPDATE products SET name=?,brand=?,description=?,color=? WHERE id=?",
        name,
        brand,
        description,
        color,
        productId);
  }

  public int updateSku(int storageGb, String color, BigDecimal price, long skuId) {
    return jdbc.update(
        "UPDATE product_skus SET storage_gb=?,color=?,price=? WHERE id=?",
        storageGb,
        color,
        price,
        skuId);
  }

  public List<Map<String, Object>> findProductIdBySku(long skuId) {
    return jdbc.queryForList("SELECT product_id FROM product_skus WHERE id=?", skuId);
  }

  public int setDeleted(boolean deleted, String status, long productId) {
    return jdbc.update(
        "UPDATE products SET deleted_at="
            + (deleted ? "UTC_TIMESTAMP()" : "NULL")
            + ",status=? WHERE id=?",
        status,
        productId);
  }

  public List<Map<String, Object>> findAvailableSku(long skuId) {
    return jdbc.queryForList(
        "SELECT s.price,p.name FROM product_skus s JOIN products p ON p.id=s.product_id WHERE"
            + " s.id=? AND p.deleted_at IS NULL AND p.status='ACTIVE'",
        skuId);
  }

  public List<Map<String, Object>> findCatalog() {
    return jdbc.queryForList(
        "SELECT s.id,p.name,p.brand,p.description,s.color,s.storage_gb,s.price,i.on_hand-i.reserved"
            + " AS available FROM products p JOIN product_skus s ON s.product_id=p.id JOIN"
            + " inventory i ON i.sku_id=s.id WHERE p.deleted_at IS NULL AND p.status='ACTIVE' ORDER"
            + " BY p.id DESC LIMIT 100");
  }

  public List<Map<String, Object>> findCatalogMetadata() {
    return jdbc.queryForList(
        "SELECT s.id,p.name,p.brand,p.description,s.color,s.storage_gb,s.price FROM products p JOIN"
            + " product_skus s ON s.product_id=p.id WHERE p.deleted_at IS NULL AND"
            + " p.status='ACTIVE' ORDER BY p.id DESC LIMIT 100");
  }
}
