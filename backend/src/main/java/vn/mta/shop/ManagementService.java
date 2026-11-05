package vn.mta.shop;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.security.AccountService;
import static vn.mta.shop.ShopController.error;

@Service
public class ManagementService {
    private final JdbcTemplate db;private final PasswordEncoder passwords;private final OrderService orders;
    public ManagementService(JdbcTemplate db,PasswordEncoder passwords,OrderService orders){this.db=db;this.passwords=passwords;this.orders=orders;}
    public record Product(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=80) String brand,@Size(max=10000) String description,
        @Min(1) @Max(4096) int storageGb,@NotBlank @Size(max=40) String color,@NotNull @DecimalMin("0.01") @Digits(integer=13,fraction=2) BigDecimal price,@Min(0) int onHand){}
    @Transactional public long product(Long id,Product p,long actor){
        long product;
        if(id==null){db.update("INSERT INTO products(name,brand,description,color) VALUES(?,?,?,?)",p.name(),p.brand(),p.description(),p.color());product=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);db.update("INSERT INTO product_skus(product_id,code,storage_gb,color,price) VALUES(?,?,?,?,?)",product,"SKU-"+UUID.randomUUID(),p.storageGb(),p.color(),p.price());id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);db.update("INSERT INTO inventory(sku_id,on_hand) VALUES(?,?)",id,p.onHand());}
        else {var rows=db.queryForList("SELECT s.product_id,i.reserved FROM product_skus s JOIN inventory i ON i.sku_id=s.id WHERE s.id=? FOR UPDATE",id);if(rows.isEmpty())throw error(404,"Không tìm thấy SKU");product=((Number)rows.get(0).get("product_id")).longValue();if(p.onHand()<((Number)rows.get(0).get("reserved")).intValue())throw error(409,"Tồn kho không được nhỏ hơn số lượng đang giữ");db.update("UPDATE products SET name=?,brand=?,description=?,color=? WHERE id=?",p.name(),p.brand(),p.description(),p.color(),product);db.update("UPDATE product_skus SET storage_gb=?,color=?,price=? WHERE id=?",p.storageGb(),p.color(),p.price(),id);db.update("UPDATE inventory SET on_hand=? WHERE sku_id=?",p.onHand(),id);}
        audit(actor,"SAVE_PRODUCT","SKU",id);return id;
    }
    @Transactional public void productDeleted(long sku,boolean deleted,long actor){var rows=db.queryForList("SELECT product_id FROM product_skus WHERE id=?",sku);if(rows.isEmpty())throw error(404,"Không tìm thấy SKU");db.update("UPDATE products SET deleted_at="+(deleted?"UTC_TIMESTAMP()":"NULL")+",status=? WHERE id=?",deleted?"ARCHIVED":"ACTIVE",rows.get(0).get("product_id"));audit(actor,deleted?"DELETE_PRODUCT":"RESTORE_PRODUCT","SKU",sku);}
    public record Account(@NotBlank @Email @Size(max=190) String email,@NotBlank @Pattern(regexp="ADMIN|CUSTOMER") String role,
        @NotBlank @Pattern(regexp="ACTIVE|LOCKED") String status,@NotBlank @Size(max=150) String name,@Size(max=72) String password){}
    private void passwordValid(String p){if(p==null||p.length()<10||p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw error(400,"Mật khẩu từ 10 ký tự, tối đa 72 bytes UTF-8");}
    private void adminGuard(long target,long actor,String nextRole,String nextStatus,boolean deleted){
        var active=db.queryForList("SELECT id FROM accounts WHERE role='ADMIN' AND status='ACTIVE' AND deleted_at IS NULL ORDER BY id FOR UPDATE");
        if(target==actor&&active.stream().anyMatch(a->((Number)a.get("id")).longValue()==target)&&(deleted||!nextRole.equals("ADMIN")||!nextStatus.equals("ACTIVE")))throw error(409,"Không tự khóa, xóa hoặc hạ quyền tài khoản quản trị đang dùng");
        if(active.stream().anyMatch(a->((Number)a.get("id")).longValue()==target)&&active.size()<=1&&(deleted||!nextRole.equals("ADMIN")||!nextStatus.equals("ACTIVE")))throw error(409,"Phải giữ ít nhất một quản trị viên hoạt động");
    }
    @Transactional public long account(Long id,Account input,long actor){
        if(id==null){passwordValid(input.password());db.update("INSERT INTO accounts(email,password_hash,role,status,display_name) VALUES(?,?,?,?,?)",AccountService.email(input.email()),passwords.encode(input.password()),input.role(),input.status(),input.name());id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
        else {adminGuard(id,actor,input.role(),input.status(),false);var a=db.queryForList("SELECT id,deleted_at FROM accounts WHERE id=? FOR UPDATE",id);if(a.isEmpty())throw error(404,"Không tìm thấy tài khoản");if(a.get(0).get("deleted_at")!=null)throw error(409,"Khôi phục tài khoản trước khi sửa");
            db.update("UPDATE accounts SET email=?,role=?,status=?,display_name=?,session_version=session_version+1 WHERE id=?",AccountService.email(input.email()),input.role(),input.status(),input.name(),id);
            if(input.password()!=null&&!input.password().isBlank()){passwordValid(input.password());db.update("UPDATE accounts SET password_hash=? WHERE id=?",passwords.encode(input.password()),id);}}
        var customer=db.queryForList("SELECT id FROM customers WHERE account_id=?",id);
        if(input.role().equals("CUSTOMER")&&customer.isEmpty())db.update("INSERT INTO customers(account_id,full_name) VALUES(?,?)",id,input.name());
        else if(!customer.isEmpty())db.update("UPDATE customers SET full_name=? WHERE account_id=?",input.name(),id);
        audit(actor,"SAVE_ACCOUNT","ACCOUNT",id);return id;
    }
    @Transactional public void accountDeleted(long id,boolean deleted,long actor){var rows=db.queryForList("SELECT role,status FROM accounts WHERE id=?",id);if(rows.isEmpty())throw error(404,"Không tìm thấy tài khoản");adminGuard(id,actor,rows.get(0).get("role").toString(),deleted?"DELETED":"ACTIVE",deleted);
        db.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE",id);
        db.update("UPDATE accounts SET deleted_at="+(deleted?"UTC_TIMESTAMP()":"NULL")+",status=?,session_version=session_version+1 WHERE id=?",deleted?"DELETED":"ACTIVE",id);
        for(var c:db.queryForList("SELECT id FROM customers WHERE account_id=? FOR UPDATE",id)){long customer=((Number)c.get("id")).longValue();if(deleted)orders.cancelPendingCustomer(customer);db.update("UPDATE customers SET deleted_at="+(deleted?"UTC_TIMESTAMP()":"NULL")+" WHERE id=?",customer);}
        audit(actor,deleted?"DELETE_ACCOUNT":"RESTORE_ACCOUNT","ACCOUNT",id);
    }
    public record Customer(@NotBlank @Size(max=150) String name,@Size(max=30) String phone){}
    @Transactional public void customer(long id,Customer input,long actor){if(db.update("UPDATE customers SET full_name=?,phone=? WHERE id=? AND deleted_at IS NULL",input.name(),input.phone(),id)==0)throw error(404,"Không tìm thấy khách hàng đang hoạt động");audit(actor,"SAVE_CUSTOMER","CUSTOMER",id);}
    @Transactional public void customerDeleted(long id,boolean deleted,long actor){var rows=db.queryForList("SELECT account_id FROM customers WHERE id=?",id);if(rows.isEmpty())throw error(404,"Không tìm thấy khách hàng");Object account=rows.get(0).get("account_id");
        if(account!=null){var a=db.queryForMap("SELECT role,status,deleted_at FROM accounts WHERE id=?",account);adminGuard(((Number)account).longValue(),actor,a.get("role").toString(),deleted?"LOCKED":a.get("status").toString(),false);if(!deleted&&a.get("deleted_at")!=null)throw error(409,"Hãy khôi phục tài khoản liên kết trước");if(deleted)db.update("UPDATE accounts SET status='LOCKED',session_version=session_version+1 WHERE id=?",account);}
        db.queryForList("SELECT id FROM customers WHERE id=? FOR UPDATE",id);if(deleted)orders.cancelPendingCustomer(id);db.update("UPDATE customers SET deleted_at="+(deleted?"UTC_TIMESTAMP()":"NULL")+" WHERE id=?",id);audit(actor,deleted?"DELETE_CUSTOMER":"RESTORE_CUSTOMER","CUSTOMER",id);
    }
    @Transactional public void deleteSelf(String email,String password){var a=db.queryForMap("SELECT id,password_hash,role FROM accounts WHERE email=? FOR UPDATE",email);if(!"CUSTOMER".equals(a.get("role")))throw error(403,"Quản trị viên không thể tự xóa ở đây");if(password==null||!passwords.matches(password,a.get("password_hash").toString()))throw error(401,"Mật khẩu xác nhận không đúng");accountDeleted(((Number)a.get("id")).longValue(),true,((Number)a.get("id")).longValue());}
    public record SaleItem(@Min(1) long skuId,@NotNull @DecimalMin("0.01") @Digits(integer=13,fraction=2) BigDecimal price,@Min(1) int quota,@Min(1) @Max(5) int perCustomerLimit){}
    public record Campaign(@NotBlank @Size(max=150) String name,@NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,
        @Pattern(regexp="DRAFT|SCHEDULED|ACTIVE") @NotNull String status,@NotEmpty @Size(max=100) List<@Valid SaleItem> items){}
    @Transactional public long campaign(Long id,Campaign c,long actor){if(!c.endsAt().isAfter(c.startsAt()))throw error(400,"Kết thúc phải sau thời điểm bắt đầu");if(c.items().stream().map(SaleItem::skuId).distinct().count()!=c.items().size())throw error(400,"SKU bị lặp trong chiến dịch");
        var items=new ArrayList<>(c.items());items.sort(Comparator.comparingLong(SaleItem::skuId));
        // Cùng khóa inventory với luồng đặt hàng để ngăn tạo hai lịch sale giao nhau cho một SKU.
        for(var item:items)if(db.queryForList("SELECT sku_id FROM inventory WHERE sku_id=? FOR UPDATE",item.skuId()).isEmpty())throw error(404,"SKU không tồn tại");
        if(id!=null){var existing=db.queryForList("SELECT id FROM sale_campaigns WHERE id=? AND deleted_at IS NULL FOR UPDATE",id);if(existing.isEmpty())throw error(404,"Không tìm thấy chiến dịch");if(db.queryForObject("SELECT COUNT(*) FROM orders WHERE campaign_id=?",Integer.class,id)>0)throw error(409,"Chiến dịch có đơn: chỉ có thể kết thúc/hủy để giữ lịch sử và quota");}
        for(var item:items){if(!c.status().equals("DRAFT")&&db.queryForObject("SELECT COUNT(*) FROM sale_items si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE si.sku_id=? AND c.id<>? AND c.deleted_at IS NULL AND c.status IN ('SCHEDULED','ACTIVE') AND c.starts_at<? AND c.ends_at>?",Integer.class,item.skuId(),id==null?-1:id,c.endsAt(),c.startsAt())>0)throw error(409,"SKU có lịch sale trùng thời gian");}
        if(id==null){db.update("INSERT INTO sale_campaigns(name,starts_at,ends_at,status) VALUES(?,?,?,?)",c.name(),c.startsAt(),c.endsAt(),c.status());id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}else{db.update("UPDATE sale_campaigns SET name=?,starts_at=?,ends_at=?,status=? WHERE id=?",c.name(),c.startsAt(),c.endsAt(),c.status(),id);db.update("DELETE FROM sale_items WHERE campaign_id=?",id);}
        for(var item:items)db.update("INSERT INTO sale_items(campaign_id,sku_id,sale_price,quota,per_customer_limit) VALUES(?,?,?,?,?)",id,item.skuId(),item.price(),item.quota(),item.perCustomerLimit());audit(actor,"SAVE_CAMPAIGN","CAMPAIGN",id);return id;
    }
    @Transactional public void stopCampaign(long id,String status,long actor){if(!Set.of("ENDED","CANCELLED").contains(status))throw error(400,"Trạng thái không hợp lệ");var skus=db.queryForList("SELECT sku_id FROM sale_items WHERE campaign_id=? ORDER BY sku_id",id);for(var s:skus)db.queryForList("SELECT sku_id FROM inventory WHERE sku_id=? FOR UPDATE",s.get("sku_id"));if(db.update("UPDATE sale_campaigns SET status=? WHERE id=? AND deleted_at IS NULL",status,id)==0)throw error(404,"Không tìm thấy chiến dịch");audit(actor,"STOP_CAMPAIGN","CAMPAIGN",id);}
    @Transactional public void deleteCampaign(long id,long actor){stopCampaign(id,"CANCELLED",actor);db.update("UPDATE sale_campaigns SET deleted_at=UTC_TIMESTAMP() WHERE id=?",id);audit(actor,"DELETE_CAMPAIGN","CAMPAIGN",id);}
    public void audit(long actor,String action,String entity,long id){db.update("INSERT INTO audit_logs(created_at,actor_id,action,entity_type,entity_id,details) VALUES(UTC_TIMESTAMP(),?,?,?,?,JSON_OBJECT())",actor,action,entity,id);}
}
