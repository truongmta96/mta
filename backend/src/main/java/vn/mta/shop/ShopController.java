package vn.mta.shop;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Local development API. Fixed demo customer; never expose publicly without authentication. */
@RestController @RequestMapping("/api")
public class ShopController {
 private final JdbcTemplate db;
 public ShopController(JdbcTemplate db) { this.db=db; }
 @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","database",db.queryForObject("SELECT 1",Integer.class));}
 @GetMapping("/products") public List<Map<String,Object>> products(){return db.queryForList("SELECT s.id,p.name,p.brand,p.description,s.color,s.storage_gb,s.price,i.on_hand-i.reserved AS available FROM products p JOIN product_skus s ON s.product_id=p.id JOIN inventory i ON i.sku_id=s.id WHERE p.deleted_at IS NULL AND p.status='ACTIVE' ORDER BY p.id LIMIT 100");}
 @GetMapping("/orders") public List<Map<String,Object>> orders(){return db.queryForList("SELECT id,status,total,created_at,expires_at FROM orders WHERE customer_id=1 ORDER BY id DESC LIMIT 50");}
 @GetMapping("/campaigns") public List<Map<String,Object>> campaigns(){return db.queryForList("SELECT id,name,starts_at,ends_at,status FROM sale_campaigns ORDER BY starts_at DESC LIMIT 20");}
 @GetMapping("/customer") public Map<String,Object> customer(){return db.queryForMap("SELECT c.id,c.full_name,c.phone,a.email FROM customers c JOIN accounts a ON a.id=c.account_id WHERE c.id=1 AND c.deleted_at IS NULL");}
 public record PlaceOrder(@Min(1) long skuId,@Min(1) @Max(5) int quantity,@NotBlank @Size(max=150) String recipient,@NotBlank @Size(max=30) String phone,@NotBlank @Size(max=500) String address){}
 @PostMapping("/orders") @Transactional
 public Map<String,Object> place(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PlaceOrder r) throws Exception {
  if(key.isBlank()||key.length()>80) throw error(400,"Khóa yêu cầu không hợp lệ");
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(r.toString().getBytes(StandardCharsets.UTF_8)));
  // Serialize demo customer requests, including retries, in the same transaction.
  db.queryForObject("SELECT id FROM customers WHERE id=1 AND deleted_at IS NULL FOR UPDATE",Long.class);
  var previous=db.queryForList("SELECT id,status,total,request_hash FROM orders WHERE customer_id=1 AND idempotency_key=?",key);
  if(!previous.isEmpty()) {if(!hash.equals(previous.get(0).get("request_hash")))throw error(409,"Khóa đã được dùng với nội dung khác");return previous.get(0);}
  // Fail closed until transactional campaign quota enforcement is implemented.
  Integer active=db.queryForObject("SELECT COUNT(*) FROM sale_items si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE si.sku_id=? AND c.status IN ('ACTIVE','SCHEDULED') AND UTC_TIMESTAMP()>=c.starts_at AND UTC_TIMESTAMP()<c.ends_at",Integer.class,r.skuId());
  if(active>0) throw error(409,"Đặt hàng flash sale chưa mở trong bản khởi tạo");
  var skus=db.queryForList("SELECT s.price,p.name FROM product_skus s JOIN products p ON p.id=s.product_id WHERE s.id=? AND p.deleted_at IS NULL AND p.status='ACTIVE'",r.skuId());
  if(skus.isEmpty())throw error(404,"Không tìm thấy sản phẩm");
  if(db.update("UPDATE inventory SET reserved=reserved+? WHERE sku_id=? AND on_hand-reserved>=?",r.quantity(),r.skuId(),r.quantity())!=1)throw error(409,"Không đủ hàng");
  BigDecimal price=(BigDecimal)skus.get(0).get("price"), total=price.multiply(BigDecimal.valueOf(r.quantity()));
  db.update("INSERT INTO orders(customer_id,idempotency_key,request_hash,status,total,recipient,phone,address,expires_at) VALUES(1,?,?,'PENDING_PAYMENT',?,?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE))",key,hash,total,r.recipient(),r.phone(),r.address());
  Long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  db.update("INSERT INTO order_items(order_id,sku_id,product_name,quantity,unit_price) VALUES(?,?,?,?,?)",id,r.skuId(),skus.get(0).get("name"),r.quantity(),price);
  db.update("INSERT INTO inventory_reservations(order_id,sku_id,quantity,status,expires_at) VALUES(?,?,?,'HELD',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE))",id,r.skuId(),r.quantity());
  db.update("INSERT INTO outbox_events(aggregate_id,event_type,payload) VALUES(?,'OrderCreated',JSON_OBJECT('orderId',?))",id,id);
  return Map.of("id",id,"status","PENDING_PAYMENT","total",total);
 }
 @PostMapping("/orders/{id}/payments") public void payment(@PathVariable long id){throw error(501,"Chưa kết nối cổng thanh toán. Đơn chưa được thu tiền.");}
 static ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}
