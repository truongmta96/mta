package vn.mta.shop;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
@Component
public class BackgroundJobs {
 private final JdbcTemplate db; private final KafkaTemplate<String,String> kafka;
 public BackgroundJobs(JdbcTemplate db,KafkaTemplate<String,String> kafka){this.db=db;this.kafka=kafka;}
 @Scheduled(fixedDelay=10000) @Transactional
 public void expire(){
  var orders=db.queryForList("SELECT id FROM orders WHERE status='PENDING_PAYMENT' AND expires_at<=UTC_TIMESTAMP() ORDER BY expires_at,id LIMIT 100 FOR UPDATE SKIP LOCKED");
  for(var o:orders){Object id=o.get("id");
   var rows=db.queryForList("SELECT sku_id,quantity FROM inventory_reservations WHERE order_id=? AND status='HELD'",id);
   for(var r:rows)db.update("UPDATE inventory SET reserved=reserved-? WHERE sku_id=?",r.get("quantity"),r.get("sku_id"));
   db.update("UPDATE inventory_reservations SET status='RELEASED' WHERE order_id=? AND status='HELD'",id);
   db.update("UPDATE orders SET status='EXPIRED' WHERE id=?",id);
   db.update("INSERT INTO outbox_events(aggregate_id,event_type,payload) VALUES(?,'OrderExpired',JSON_OBJECT('orderId',?))",id,id);
  }
 }
 @Scheduled(fixedDelay=5000)
 public void publish(){
  for(var e:db.queryForList("SELECT id,aggregate_id,event_type,payload FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT 100")){
   try {String body="{\"eventId\":"+e.get("id")+",\"type\":\""+e.get("event_type")+"\",\"data\":"+e.get("payload")+"}";
    kafka.send("shop.events",e.get("aggregate_id").toString(),body).get(10,java.util.concurrent.TimeUnit.SECONDS);
    db.update("UPDATE outbox_events SET published_at=UTC_TIMESTAMP() WHERE id=?",e.get("id"));
   } catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox {} remains pending",e.get("id"));break;}
  }
 }
}
