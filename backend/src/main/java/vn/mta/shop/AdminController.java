package vn.mta.shop;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** SecurityConfig yêu cầu ADMIN cho toàn bộ /api/admin/**. Không trả password_hash. */
@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate db;
    public AdminController(JdbcTemplate db) {this.db=db;}
    @GetMapping("/products") public List<Map<String,Object>> products() {
        return db.queryForList("SELECT s.id,p.name,p.brand,s.storage_gb,s.color,s.price,i.on_hand,i.reserved,i.on_hand-i.reserved AS available FROM product_skus s JOIN products p ON p.id=s.product_id JOIN inventory i ON i.sku_id=s.id WHERE p.deleted_at IS NULL ORDER BY s.id LIMIT 100");
    }
    @GetMapping("/orders") public List<Map<String,Object>> orders() {
        return db.queryForList("SELECT o.id,c.full_name AS customer,o.status,o.total,o.created_at FROM orders o JOIN customers c ON c.id=o.customer_id ORDER BY o.id DESC LIMIT 100");
    }
    @GetMapping("/accounts") public List<Map<String,Object>> accounts() {
        return db.queryForList("SELECT id,email,role,status,deleted_at,created_at FROM accounts ORDER BY id DESC LIMIT 100");
    }
    @GetMapping("/customers") public List<Map<String,Object>> customers() {
        return db.queryForList("SELECT c.id,c.full_name,c.phone,a.email,c.deleted_at FROM customers c LEFT JOIN accounts a ON a.id=c.account_id ORDER BY c.id DESC LIMIT 100");
    }
    @GetMapping("/campaigns") public List<Map<String,Object>> campaigns() {
        return db.queryForList("SELECT id,name,starts_at,ends_at,status FROM sale_campaigns ORDER BY id DESC LIMIT 100");
    }
}
