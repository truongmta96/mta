package vn.mta.shop;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static vn.mta.shop.ShopController.error;

/** Nhà cung cấp SANDBOX mô phỏng giao dịch; không thu hoặc chuyển tiền thật. */
@Service
public class PaymentService {
    private final JdbcTemplate db;private final OrderService orders;private final ObjectMapper json;
    private final boolean enabled;private final byte[] secret;
    public PaymentService(JdbcTemplate db,OrderService orders,ObjectMapper json,@Value("${app.payment.sandbox-enabled:false}") boolean enabled,@Value("${app.payment.webhook-secret:}") String secret){
        this.db=db;this.orders=orders;this.json=json;this.enabled=enabled;
        this.secret=secret.isBlank()?randomSecret():secret.getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] randomSecret(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b;}
    public Map<String,Object> config(){return Map.of("enabled",enabled,"provider",enabled?"SANDBOX":"NOT_CONFIGURED","realMoney",false);}
    private void available(){if(!enabled)throw error(503,"Chưa cấu hình nhà cung cấp thanh toán. Sandbox đang tắt.");}
    @Transactional public Map<String,Object> create(long id,long customer){available();orders.owned(id,customer);var o=orders.lock(id);orders.expireLocked(o);
        if(!"PENDING_PAYMENT".equals(o.get("status")))return Map.of("status",o.get("status"),"message","Đơn không còn chờ thanh toán");
        var pending=db.queryForList("SELECT id,provider,status,amount FROM payment_attempts WHERE order_id=? AND provider='SANDBOX' AND status='PENDING' ORDER BY id DESC LIMIT 1",id);
        if(!pending.isEmpty())return pending.get(0);
        String ref="SB-"+UUID.randomUUID();db.update("INSERT INTO payment_attempts(order_id,provider,provider_reference,status,amount) VALUES(?,'SANDBOX',?,'PENDING',?)",id,ref,o.get("total"));
        Long attempt=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);return Map.of("id",attempt,"provider","SANDBOX","status","PENDING","amount",o.get("total"));
    }
    // Tạo payload và ký ở server; không gửi webhook secret cho trình duyệt.
    public Map<String,String> signedSandbox(long attempt,long customer,String result) throws Exception {
        available();if(!Set.of("SUCCEEDED","FAILED").contains(result))throw error(400,"Kết quả sandbox không hợp lệ");
        var rows=db.queryForList("SELECT p.* FROM payment_attempts p JOIN orders o ON o.id=p.order_id WHERE p.id=? AND o.customer_id=? AND p.provider='SANDBOX'",attempt,customer);
        if(rows.isEmpty())throw error(404,"Không tìm thấy giao dịch");var p=rows.get(0);
        String payload=json.writeValueAsString(Map.of("eventId","SB-"+attempt+"-"+result,"reference",p.get("provider_reference"),"status",result,"amount",p.get("amount"),"timestamp",Instant.now().getEpochSecond()));
        return Map.of("payload",payload,"signature",HexFormat.of().formatHex(hmac(payload)));
    }
    private byte[] hmac(String payload) throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));}
    public record Notification(String eventId,String reference,String status,BigDecimal amount,long timestamp){}
    @Transactional public Map<String,Object> webhook(String payload,String signature) throws Exception {
        available();if(payload.length()>64000)throw error(413,"Payload webhook quá lớn");byte[] received;try{received=HexFormat.of().parseHex(signature);}catch(IllegalArgumentException ex){throw error(401,"Chữ ký webhook không hợp lệ");}
        if(!MessageDigest.isEqual(received,hmac(payload)))throw error(401,"Chữ ký webhook không hợp lệ");
        Notification n;try{n=json.readValue(payload,Notification.class);}catch(Exception ex){throw error(400,"Payload webhook không hợp lệ");}
        long now=Instant.now().getEpochSecond();
        if(n==null||n.eventId()==null||n.eventId().isBlank()||n.eventId().length()>100||n.reference()==null||n.reference().length()>100||n.amount()==null||n.amount().signum()<0||!Set.of("SUCCEEDED","FAILED").contains(Objects.toString(n.status(),""))||n.timestamp()<now-300||n.timestamp()>now+300)throw error(400,"Nội dung hoặc thời gian webhook không hợp lệ");
        var found=db.queryForList("SELECT order_id FROM payment_attempts WHERE provider_reference=? AND provider='SANDBOX'",n.reference());if(found.isEmpty())throw error(404,"Không tìm thấy giao dịch");
        long order=((Number)found.get(0).get("order_id")).longValue();var o=orders.lock(order);
        var p=db.queryForMap("SELECT * FROM payment_attempts WHERE provider_reference=? FOR UPDATE",n.reference());
        if(((BigDecimal)p.get("amount")).compareTo(n.amount())!=0)throw error(400,"Số tiền webhook không khớp");
        var old=db.queryForList("SELECT payload FROM payment_webhook_events WHERE provider='SANDBOX' AND event_id=? FOR UPDATE",n.eventId());
        if(!old.isEmpty()){var previous=json.readValue(old.get(0).get("payload").toString(),Notification.class);if(!previous.reference().equals(n.reference())||!previous.status().equals(n.status())||previous.amount().compareTo(n.amount())!=0)throw error(409,"Event ID đã dùng cho nội dung khác");return Map.of("duplicate",true,"orderId",order);}
        db.update("INSERT INTO payment_webhook_events(provider,event_id,payload,status) VALUES('SANDBOX',?,?,'PROCESSED')",n.eventId(),payload);
        String state=p.get("status").toString();if(!state.equals("PENDING"))return Map.of("ignored",true,"orderId",order);
        if(n.status().equals("FAILED")){db.update("UPDATE payment_attempts SET status='FAILED' WHERE id=?",p.get("id"));return Map.of("status","FAILED","orderId",order);}
        orders.expireLocked(o);
        if("PENDING_PAYMENT".equals(o.get("status"))){db.update("UPDATE payment_attempts SET status='SUCCEEDED' WHERE id=?",p.get("id"));orders.paid(o);return Map.of("status","PAID","orderId",order);}
        // Sandbox hoàn tự động tiền đến sau hủy/hết hạn. Không tái giữ hoặc lấy hàng của người khác.
        db.update("UPDATE payment_attempts SET status='REFUNDED' WHERE id=?",p.get("id"));
        db.update("INSERT INTO refunds(order_id,payment_attempt_id,amount,status,reason) VALUES(?,?,?,'SUCCEEDED','Tự hoàn sandbox: thanh toán đến sau khi đơn kết thúc')",order,p.get("id"),p.get("amount"));orders.event(order,"LatePaymentRefunded");
        return Map.of("status","REFUNDED","orderId",order);
    }
    @Transactional public Map<String,Object> requestRefund(long id,Long customer,String reason){available();if(customer!=null)orders.owned(id,customer);var o=orders.lock(id);
        var existing=db.queryForList("SELECT * FROM refunds WHERE order_id=?",id);if(!existing.isEmpty()){var old=existing.get(0);if("REJECTED".equals(old.get("status"))){db.update("UPDATE refunds SET status='REQUESTED',reason=? WHERE id=?",reason,old.get("id"));return db.queryForMap("SELECT * FROM refunds WHERE id=?",old.get("id"));}return old;}
        if(!Set.of("PAID","PROCESSING","SHIPPED","COMPLETED").contains(o.get("status")))throw error(409,"Chỉ yêu cầu hoàn tiền đơn đã thanh toán");
        var paid=db.queryForList("SELECT id,amount FROM payment_attempts WHERE order_id=? AND status='SUCCEEDED' AND provider='SANDBOX'",id);if(paid.isEmpty())throw error(409,"Không tìm thấy giao dịch đã thu tiền");
        db.update("INSERT INTO refunds(order_id,payment_attempt_id,amount,status,reason) VALUES(?,?,?,'REQUESTED',?)",id,paid.get(0).get("id"),paid.get(0).get("amount"),reason);
        orders.event(id,"RefundRequested");return db.queryForMap("SELECT * FROM refunds WHERE order_id=?",id);
    }
    @Transactional public void decideRefund(long refund,boolean approve,boolean returnReceived){available();var rows=db.queryForList("SELECT order_id FROM refunds WHERE id=?",refund);if(rows.isEmpty())throw error(404,"Không tìm thấy yêu cầu hoàn tiền");long id=((Number)rows.get(0).get("order_id")).longValue();var o=orders.lock(id);var r=db.queryForMap("SELECT * FROM refunds WHERE id=? FOR UPDATE",refund);
        if("SUCCEEDED".equals(r.get("status")))return;if(!"REQUESTED".equals(r.get("status")))throw error(409,"Yêu cầu không còn chờ xử lý");
        if(!approve){db.update("UPDATE refunds SET status='REJECTED' WHERE id=?",refund);return;}
        orders.release(o,returnReceived);db.update("UPDATE refunds SET status='SUCCEEDED',return_received=? WHERE id=?",returnReceived,refund);db.update("UPDATE payment_attempts SET status='REFUNDED' WHERE id=?",r.get("payment_attempt_id"));db.update("UPDATE orders SET status='REFUNDED' WHERE id=?",id);orders.event(id,"RefundSucceeded");
    }
}
