package vn.mta.shop;
import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.mta.shop.security.AccountService;
@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate db;private final ManagementService management;private final AccountService accounts;private final OrderService orders;private final PaymentService payments;
    public AdminController(JdbcTemplate db,ManagementService management,AccountService accounts,OrderService orders,PaymentService payments){this.db=db;this.management=management;this.accounts=accounts;this.orders=orders;this.payments=payments;}
    private long actor(Principal p){return ((Number)accounts.profile(p.getName()).get("id")).longValue();}
    @GetMapping("/products") public List<Map<String,Object>> products(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){return db.queryForList("SELECT s.id,p.name,p.brand,p.description,p.deleted_at,s.storage_gb,s.color,s.price,i.on_hand,i.reserved,i.on_hand-i.reserved AS available FROM product_skus s JOIN products p ON p.id=s.product_id JOIN inventory i ON i.sku_id=s.id WHERE p.name LIKE ? ESCAPE '!' ORDER BY s.id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));}
    @PostMapping("/products") public Map<String,Long> addProduct(@Valid @RequestBody ManagementService.Product input,Principal p){return Map.of("id",management.product(null,input,actor(p)));}
    @PutMapping("/products/{id}") public Map<String,Long> editProduct(@PathVariable long id,@Valid @RequestBody ManagementService.Product input,Principal p){return Map.of("id",management.product(id,input,actor(p)));}
    @DeleteMapping("/products/{id}") public Map<String,String> deleteProduct(@PathVariable long id,Principal p){management.productDeleted(id,true,actor(p));return ok();}
    @PostMapping("/products/{id}/restore") public Map<String,String> restoreProduct(@PathVariable long id,Principal p){management.productDeleted(id,false,actor(p));return ok();}
    @GetMapping("/orders") public List<Map<String,Object>> listOrders(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){return db.queryForList("SELECT o.id,c.full_name AS customer,o.status,o.total,o.created_at FROM orders o JOIN customers c ON c.id=o.customer_id WHERE c.full_name LIKE ? ESCAPE '!' ORDER BY o.id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));}
    @GetMapping("/orders/{id}") @Transactional public Map<String,Object> detail(@PathVariable long id){return orders.detail(id);}
    public record Status(@NotBlank String status){}
    @PatchMapping("/orders/{id}") public Map<String,String> progress(@PathVariable long id,@Valid @RequestBody Status input){orders.progress(id,input.status());return ok();}
    @PostMapping("/orders/{id}/cancel") public Map<String,String> cancel(@PathVariable long id){orders.cancel(id,null);return ok();}
    @PostMapping("/orders/{id}/refunds") public Map<String,Object> refund(@PathVariable long id,@Valid @RequestBody ShopController.Refund input){return payments.requestRefund(id,null,input.reason());}
    @GetMapping("/accounts") public List<Map<String,Object>> accountList(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){return db.queryForList("SELECT a.id,a.email,a.role,a.status,a.deleted_at,a.created_at,COALESCE(c.full_name,a.display_name,'Quản trị viên') AS name FROM accounts a LEFT JOIN customers c ON c.account_id=a.id WHERE a.email LIKE ? ESCAPE '!' ORDER BY a.id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));}
    @PostMapping("/accounts") public Map<String,Long> addAccount(@Valid @RequestBody ManagementService.Account input,Principal p){return Map.of("id",management.account(null,input,actor(p)));}
    @PutMapping("/accounts/{id}") public Map<String,Long> editAccount(@PathVariable long id,@Valid @RequestBody ManagementService.Account input,Principal p){return Map.of("id",management.account(id,input,actor(p)));}
    @DeleteMapping("/accounts/{id}") public Map<String,String> deleteAccount(@PathVariable long id,Principal p){management.accountDeleted(id,true,actor(p));return ok();}
    @PostMapping("/accounts/{id}/restore") public Map<String,String> restoreAccount(@PathVariable long id,Principal p){management.accountDeleted(id,false,actor(p));return ok();}
    @GetMapping("/customers") public List<Map<String,Object>> customers(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){return db.queryForList("SELECT c.id,c.full_name,c.phone,a.email,c.deleted_at FROM customers c LEFT JOIN accounts a ON a.id=c.account_id WHERE c.full_name LIKE ? ESCAPE '!' ORDER BY c.id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));}
    @PutMapping("/customers/{id}") public Map<String,String> customer(@PathVariable long id,@Valid @RequestBody ManagementService.Customer input,Principal p){management.customer(id,input,actor(p));return ok();}
    @DeleteMapping("/customers/{id}") public Map<String,String> deleteCustomer(@PathVariable long id,Principal p){management.customerDeleted(id,true,actor(p));return ok();}
    @PostMapping("/customers/{id}/restore") public Map<String,String> restoreCustomer(@PathVariable long id,Principal p){management.customerDeleted(id,false,actor(p));return ok();}
    @GetMapping("/campaigns") public List<Map<String,Object>> campaigns(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){var rows=db.queryForList("SELECT * FROM sale_campaigns WHERE name LIKE ? ESCAPE '!' ORDER BY id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));for(var c:rows)c.put("items",db.queryForList("SELECT * FROM sale_items WHERE campaign_id=?",c.get("id")));return rows;}
    @PostMapping("/campaigns") public Map<String,Long> addSale(@Valid @RequestBody ManagementService.Campaign input,Principal p){return Map.of("id",management.campaign(null,input,actor(p)));}
    @PutMapping("/campaigns/{id}") public Map<String,Long> editSale(@PathVariable long id,@Valid @RequestBody ManagementService.Campaign input,Principal p){return Map.of("id",management.campaign(id,input,actor(p)));}
    @PatchMapping("/campaigns/{id}") public Map<String,String> stopSale(@PathVariable long id,@Valid @RequestBody Status input,Principal p){management.stopCampaign(id,input.status(),actor(p));return ok();}
    @DeleteMapping("/campaigns/{id}") public Map<String,String> deleteSale(@PathVariable long id,Principal p){management.deleteCampaign(id,actor(p));return ok();}
    @GetMapping("/refunds") public List<Map<String,Object>> refunds(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){return db.queryForList("SELECT r.*,c.full_name AS customer FROM refunds r JOIN orders o ON o.id=r.order_id JOIN customers c ON c.id=o.customer_id WHERE c.full_name LIKE ? ESCAPE '!' ORDER BY r.id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));}
    public record RefundDecision(boolean approve,boolean returnReceived){}
    @PostMapping("/refunds/{id}/decision") public Map<String,String> decide(@PathVariable long id,@RequestBody RefundDecision input){payments.decideRefund(id,input.approve(),input.returnReceived());return ok();}
    @GetMapping("/audit") public List<Map<String,Object>> audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q){return db.queryForList("SELECT id,created_at,actor_id,action,entity_type,entity_id FROM audit_logs WHERE created_at>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 DAY) AND action LIKE ? ESCAPE '!' ORDER BY created_at DESC,id DESC LIMIT 50 OFFSET ?",prefix(q),offset(page));}
    private int offset(int page){if(page<0||page>100000)throw ShopController.error(400,"Trang không hợp lệ");return page*50;}
    private String prefix(String q){if(q.length()>190)throw ShopController.error(400,"Từ khóa quá dài");return q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private Map<String,String> ok(){return Map.of("message","Đã lưu thay đổi");}
}
