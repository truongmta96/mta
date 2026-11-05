package vn.mta.shop;
import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.mta.shop.security.AccountService;
@RestController @RequestMapping("/api")
public class ShopController {
    private final JdbcTemplate db;private final AccountService accounts;private final OrderService orders;private final PaymentService payments;
    public ShopController(JdbcTemplate db,AccountService accounts,OrderService orders,PaymentService payments){this.db=db;this.accounts=accounts;this.orders=orders;this.payments=payments;}
    @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","database",db.queryForObject("SELECT 1",Integer.class));}
    @GetMapping("/products") public List<Map<String,Object>> products(){var rows=db.queryForList("SELECT s.id,p.name,p.brand,p.description,s.color,s.storage_gb,s.price,i.on_hand-i.reserved AS available FROM products p JOIN product_skus s ON s.product_id=p.id JOIN inventory i ON i.sku_id=s.id WHERE p.deleted_at IS NULL AND p.status='ACTIVE' ORDER BY p.id DESC LIMIT 100");
        for(var p:rows){var active=db.queryForList("SELECT c.id,c.name,si.sale_price,si.quota-si.reserved-si.sold AS remaining FROM sale_items si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE si.sku_id=? AND c.deleted_at IS NULL AND c.status IN ('ACTIVE','SCHEDULED') AND c.starts_at<=UTC_TIMESTAMP() AND c.ends_at>UTC_TIMESTAMP() ORDER BY c.id LIMIT 1",p.get("id"));if(!active.isEmpty()){var a=active.get(0);p.put("original_price",p.get("price"));p.put("price",a.get("sale_price"));p.put("campaign_id",a.get("id"));p.put("campaign_name",a.get("name"));p.put("available",Math.min(((Number)p.get("available")).intValue(),((Number)a.get("remaining")).intValue()));}}
        return rows;}
    @GetMapping("/campaigns") public List<Map<String,Object>> campaigns(){var rows=db.queryForList("SELECT id,name,starts_at,ends_at,status,UTC_TIMESTAMP() AS server_time FROM sale_campaigns WHERE deleted_at IS NULL AND status IN ('SCHEDULED','ACTIVE') AND ends_at>UTC_TIMESTAMP() ORDER BY starts_at,id LIMIT 30");for(var c:rows)c.put("items",db.queryForList("SELECT si.*,p.name,s.storage_gb,s.color,i.on_hand-i.reserved AS stock_available FROM sale_items si JOIN product_skus s ON s.id=si.sku_id JOIN products p ON p.id=s.product_id JOIN inventory i ON i.sku_id=s.id WHERE si.campaign_id=? AND p.deleted_at IS NULL AND p.status='ACTIVE'",c.get("id")));return rows;}
    @GetMapping("/orders") public List<Map<String,Object>> list(Principal p){return db.queryForList("SELECT id,status,total,created_at,expires_at,campaign_id FROM orders WHERE customer_id=? ORDER BY id DESC LIMIT 100",accounts.customerId(p.getName()));}
    @GetMapping("/orders/{id}") public Map<String,Object> detail(@PathVariable long id,Principal p){return orders.customerDetail(id,accounts.customerId(p.getName()));}
    @PostMapping("/orders") public Map<String,Object> place(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody OrderService.Place input,Principal p)throws Exception{return orders.place(accounts.customerId(p.getName()),key,input);}
    @PostMapping("/orders/{id}/cancel") public Map<String,String> cancel(@PathVariable long id,Principal p){orders.cancel(id,accounts.customerId(p.getName()));return Map.of("message","Đã hủy đơn");}
    @PostMapping("/orders/{id}/payments") public Map<String,Object> pay(@PathVariable long id,Principal p){return payments.create(id,accounts.customerId(p.getName()));}
    public record Refund(@NotBlank @Size(max=500) String reason){}
    @PostMapping("/orders/{id}/refunds") public Map<String,Object> refund(@PathVariable long id,@Valid @RequestBody Refund input,Principal p){return payments.requestRefund(id,accounts.customerId(p.getName()),input.reason());}
    @GetMapping("/customer") public Map<String,Object> customer(Principal p){return db.queryForMap("SELECT c.id,c.full_name,c.phone,a.email FROM customers c JOIN accounts a ON a.id=c.account_id WHERE c.id=?",accounts.customerId(p.getName()));}
    public record Profile(@NotBlank @Size(max=150) String name,@Size(max=30) String phone){}
    @PutMapping("/customer") public Map<String,String> profile(@Valid @RequestBody Profile input,Principal p){db.update("UPDATE customers SET full_name=?,phone=? WHERE id=?",input.name().strip(),input.phone(),accounts.customerId(p.getName()));return Map.of("message","Đã cập nhật hồ sơ");}
    public static ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}
