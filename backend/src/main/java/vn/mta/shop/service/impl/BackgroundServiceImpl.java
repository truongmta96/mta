package vn.mta.shop.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.OutboxRepository;
import vn.mta.shop.service.BackgroundService;
import vn.mta.shop.service.OrderService;

@Service
public class BackgroundServiceImpl implements BackgroundService {
  private final OrderRepository orderRepository;
  private final OutboxRepository outboxRepository;
  private final org.springframework.kafka.core.KafkaTemplate<String, String> kafka;
  private final OrderService orderService;

  public BackgroundServiceImpl(
      OrderRepository orderRepository,
      OutboxRepository outboxRepository,
      org.springframework.kafka.core.KafkaTemplate<String, String> kafka,
      OrderService orderService) {
    this.orderRepository = orderRepository;
    this.outboxRepository = outboxRepository;
    this.kafka = kafka;
    this.orderService = orderService;
  }

  @Transactional
  @Override
  public void expireOrders() {
    var orders = orderRepository.findExpiredBatchForUpdate();
    for (var o : orders)
      orderService.expireLocked(orderService.lock(((Number) o.get("id")).longValue()));
  }

  @Override
  public void publishEvents() {
    for (var e : outboxRepository.findUnpublishedBatch()) {
      try {
        String body =
            "{\"eventId\":"
                + e.get("id")
                + ",\"type\":\""
                + e.get("event_type")
                + "\",\"data\":"
                + e.get("payload")
                + "}";
        kafka
            .send("shop.events", e.get("aggregate_id").toString(), body)
            .get(10, java.util.concurrent.TimeUnit.SECONDS);
        outboxRepository.markPublished(((Number) e.get("id")).longValue());
      } catch (Exception failure) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Outbox {} remains pending", e.get("id"));
        break;
      }
    }
  }
}
