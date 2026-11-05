package vn.mta.shop;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static vn.mta.shop.ShopController.error;

/** Mọi thay đổi đơn/kho/quota chạy cùng transaction. Khóa inventory trước sale/quota. */
@Service
public class OrderService {
    private final JdbcTemplate db; private final ObjectMapper json;
    public OrderService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    public record Place(@Min(1) long skuId,@Min(1) @Max(5) int quantity,
        @NotBlank @Size(max=150) String recipient,@NotBlank @Size(max=30) String phone,
        @NotBlank @Size(max=500) String address,Long campaignId,BigDecimal expectedPrice){}

    @Transactional public Map<String,Object> place(long customer,String key,Place input) throws Exception {
        if(key.isBlank()||key.length()>80)throw error(400,"Khóa yêu cầu không hợp lệ");
        if(db.queryForList("SELECT id FROM customers WHERE id=? AND deleted_at IS NULL FOR UPDATE",customer).isEmpty())throw error(403,"Hồ sơ khách hàng không khả dụng");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));
        var previous=db.queryForList("SELECT id,status,total,request_hash FROM orders WHERE customer_id=? AND idempotency_key=?",customer,key);
        if(!previous.isEmpty()){
            if(!hash.equals(previous.get(0).get("request_hash")))throw error(409,"Khóa đã dùng với nội dung khác");
            return previous.get(0);
        }
        var rows=db.queryForList("SELECT s.price,p.name FROM product_skus s JOIN products p ON p.id=s.product_id WHERE s.id=? AND p.deleted_at IS NULL AND p.status='ACTIVE'",input.skuId());
        if(rows.isEmpty())throw error(404,"Không tìm thấy sản phẩm");
        if(db.update("UPDATE inventory SET reserved=reserved+? WHERE sku_id=? AND on_hand-reserved>=?",input.quantity(),input.skuId(),input.quantity())!=1)throw error(409,"Không đủ hàng");
        BigDecimal price=(BigDecimal)rows.get(0).get("price"); Long campaign=null;
        var sales=db.queryForList("SELECT si.*,c.id FROM sale_items si JOIN sale_campaigns c ON c.id=si.campaign_id WHERE si.sku_id=? AND c.deleted_at IS NULL AND c.status IN ('ACTIVE','SCHEDULED') AND c.starts_at<=UTC_TIMESTAMP() AND c.ends_at>UTC_TIMESTAMP() ORDER BY c.id FOR UPDATE",input.skuId());
        if(input.campaignId()!=null){sales=sales.stream().filter(s->((Number)s.get("id")).longValue()==input.campaignId()).toList();if(sales.isEmpty())throw error(409,"Chiến dịch chưa mở bán hoặc đã kết thúc");}
        if(!sales.isEmpty()){
            var sale=sales.get(0);campaign=((Number)sale.get("id")).longValue();
            price=(BigDecimal)sale.get("sale_price");
            if(db.update("UPDATE sale_items SET reserved=reserved+? WHERE campaign_id=? AND sku_id=? AND quota-reserved-sold>=?",input.quantity(),campaign,input.skuId(),input.quantity())!=1)throw error(409,"Đã hết suất ưu đãi");
            db.update("INSERT INTO sale_customer_quotas(campaign_id,sku_id,customer_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE customer_id=customer_id",campaign,input.skuId(),customer);
            if(db.update("UPDATE sale_customer_quotas SET reserved=reserved+? WHERE campaign_id=? AND sku_id=? AND customer_id=? AND reserved+sold+?<=?",input.quantity(),campaign,input.skuId(),customer,input.quantity(),sale.get("per_customer_limit"))!=1)throw error(409,"Bạn đã đạt giới hạn mua trong chiến dịch");
        }
        if(input.expectedPrice()!=null&&input.expectedPrice().compareTo(price)!=0)throw error(409,"Giá đã thay đổi. Vui lòng tải lại sản phẩm và xác nhận lại.");
        BigDecimal total=price.multiply(BigDecimal.valueOf(input.quantity()));
        db.update("INSERT INTO orders(customer_id,idempotency_key,request_hash,status,total,recipient,phone,address,expires_at,campaign_id) VALUES(?,?,?,'PENDING_PAYMENT',?,?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE),?)",customer,key,hash,total,input.recipient(),input.phone(),input.address(),campaign);
        Long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        db.update("INSERT INTO order_items(order_id,sku_id,product_name,quantity,unit_price) VALUES(?,?,?,?,?)",id,input.skuId(),rows.get(0).get("name"),input.quantity(),price);
        db.update("INSERT INTO inventory_reservations(order_id,sku_id,quantity,status,expires_at) VALUES(?,?,?,'HELD',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE))",id,input.skuId(),input.quantity());
        event(id,"OrderCreated");return Map.of("id",id,"status","PENDING_PAYMENT","total",total);
    }
    public Map<String,Object> owned(long id,long customer){var rows=db.queryForList("SELECT * FROM orders WHERE id=? AND customer_id=?",id,customer);if(rows.isEmpty())throw error(404,"Không tìm thấy đơn hàng");return rows.get(0);}
    public Map<String,Object> lock(long id){var rows=db.queryForList("SELECT * FROM orders WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw error(404,"Không tìm thấy đơn hàng");return rows.get(0);}
    public boolean expired(Map<String,Object> o){Object value=o.get("expires_at");LocalDateTime end=value instanceof LocalDateTime date?date:((java.sql.Timestamp)value).toLocalDateTime();return !end.isAfter(LocalDateTime.now(Clock.systemUTC()));}
    public Map<String,Object> detail(long id){var out=new LinkedHashMap<String,Object>(lock(id));out.remove("request_hash");out.put("items",db.queryForList("SELECT * FROM order_items WHERE order_id=?",id));out.put("payments",db.queryForList("SELECT id,provider,status,amount,created_at FROM payment_attempts WHERE order_id=? ORDER BY id DESC",id));out.put("refunds",db.queryForList("SELECT * FROM refunds WHERE order_id=?",id));return out;}
    @Transactional public Map<String,Object> customerDetail(long id,long customer){owned(id,customer);return detail(id);}
    @Transactional public void cancel(long id,Long customer){var o=lock(id);if(customer!=null&&((Number)o.get("customer_id")).longValue()!=customer)throw error(404,"Không tìm thấy đơn hàng");if(Set.of("CANCELLED","EXPIRED").contains(o.get("status")))return;if(!"PENDING_PAYMENT".equals(o.get("status")))throw error(409,"Đơn đã thanh toán: hãy yêu cầu hoàn tiền");release(o,false);db.update("UPDATE orders SET status='CANCELLED' WHERE id=?",id);event(id,"OrderCancelled");}
    public void expireLocked(Map<String,Object> o){if("PENDING_PAYMENT".equals(o.get("status"))&&expired(o)){release(o,false);db.update("UPDATE orders SET status='EXPIRED' WHERE id=?",o.get("id"));o.put("status","EXPIRED");event(((Number)o.get("id")).longValue(),"OrderExpired");}}
    // Chỉ gọi trong transaction đã khóa order. Mỗi reservation chỉ giải phóng một lần.
    public void release(Map<String,Object> o,boolean returnReceived){
        for(var r:db.queryForList("SELECT * FROM inventory_reservations WHERE order_id=? AND status IN ('HELD','CONFIRMED','FULFILLED') ORDER BY sku_id",o.get("id"))){
            String state=r.get("status").toString();Object q=r.get("quantity"),sku=r.get("sku_id");
            if(state.equals("FULFILLED")){if(!returnReceived)throw error(409,"Cần xác nhận đã nhận hàng hoàn trước khi hoàn tiền đơn đã giao");db.update("UPDATE inventory SET on_hand=on_hand+? WHERE sku_id=?",q,sku);}
            else db.update("UPDATE inventory SET reserved=reserved-? WHERE sku_id=?",q,sku);
            if(o.get("campaign_id")!=null){String field=state.equals("HELD")?"reserved":"sold";
                db.update("UPDATE sale_items SET "+field+"="+field+"-? WHERE campaign_id=? AND sku_id=?",q,o.get("campaign_id"),sku);
                db.update("UPDATE sale_customer_quotas SET "+field+"="+field+"-? WHERE campaign_id=? AND sku_id=? AND customer_id=?",q,o.get("campaign_id"),sku,o.get("customer_id"));}
            db.update("UPDATE inventory_reservations SET status=? WHERE id=?",state.equals("FULFILLED")?"RETURNED":"RELEASED",r.get("id"));
        }
    }
    public void paid(Map<String,Object> o){
        for(var r:db.queryForList("SELECT * FROM inventory_reservations WHERE order_id=? AND status='HELD' ORDER BY sku_id",o.get("id"))){
            if(o.get("campaign_id")!=null){db.update("UPDATE sale_items SET reserved=reserved-?,sold=sold+? WHERE campaign_id=? AND sku_id=?",r.get("quantity"),r.get("quantity"),o.get("campaign_id"),r.get("sku_id"));db.update("UPDATE sale_customer_quotas SET reserved=reserved-?,sold=sold+? WHERE campaign_id=? AND sku_id=? AND customer_id=?",r.get("quantity"),r.get("quantity"),o.get("campaign_id"),r.get("sku_id"),o.get("customer_id"));}
            db.update("UPDATE inventory_reservations SET status='CONFIRMED' WHERE id=?",r.get("id"));}
        db.update("UPDATE orders SET status='PAID' WHERE id=?",o.get("id"));event(((Number)o.get("id")).longValue(),"PaymentSucceeded");
    }
    @Transactional public void progress(long id,String next){var o=lock(id);String current=o.get("status").toString();
        if(!Map.of("PAID","PROCESSING","PROCESSING","SHIPPED","SHIPPED","COMPLETED").getOrDefault(current,"").equals(next))throw error(409,"Chuyển trạng thái đơn không hợp lệ");
        if(db.queryForObject("SELECT COUNT(*) FROM refunds WHERE order_id=? AND status='REQUESTED'",Integer.class,id)>0)throw error(409,"Đơn đang chờ xử lý hoàn tiền");
        if(next.equals("SHIPPED")){for(var r:db.queryForList("SELECT * FROM inventory_reservations WHERE order_id=? AND status='CONFIRMED' ORDER BY sku_id",id)){db.update("UPDATE inventory SET on_hand=on_hand-?,reserved=reserved-? WHERE sku_id=?",r.get("quantity"),r.get("quantity"),r.get("sku_id"));db.update("UPDATE inventory_reservations SET status='FULFILLED' WHERE id=?",r.get("id"));}}
        db.update("UPDATE orders SET status=? WHERE id=?",next,id);event(id,"Order"+next);
    }
    public void cancelPendingCustomer(long customer){for(var o:db.queryForList("SELECT id FROM orders WHERE customer_id=? AND status='PENDING_PAYMENT' ORDER BY id FOR UPDATE",customer))cancel(((Number)o.get("id")).longValue(),customer);}
    public void event(long id,String type){db.update("INSERT INTO outbox_events(aggregate_id,event_type,payload) VALUES(?,?,JSON_OBJECT('orderId',?))",id,type,id);}
}
