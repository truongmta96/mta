package vn.mta.shop;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
@Component
public class BackgroundJobs {
 private final JdbcTemplate db; private final KafkaTemplate<String,String> kafka; private final OrderService orderService;
 public BackgroundJobs(JdbcTemplate db,KafkaTemplate<String,String> kafka,OrderService orderService){this.db=db;this.kafka=kafka;this.orderService=orderService;}
 @Scheduled(fixedDelay=10000) @Transactional
 public void expire(){
  var orders=db.queryForList("SELECT id FROM orders WHERE status='PENDING_PAYMENT' AND expires_at<=UTC_TIMESTAMP() ORDER BY expires_at,id LIMIT 100 FOR UPDATE SKIP LOCKED");
  for(var o:orders)orderService.expireLocked(orderService.lock(((Number)o.get("id")).longValue()));
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
