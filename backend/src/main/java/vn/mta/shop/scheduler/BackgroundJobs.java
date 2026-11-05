package vn.mta.shop.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.mta.shop.service.BackgroundService;

/** Lịch chạy tách khỏi transaction và xử lý nghiệp vụ. */
@Component
public class BackgroundJobs {
  private final BackgroundService service;

  public BackgroundJobs(BackgroundService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 10000)
  public void expire() {
    service.expireOrders();
  }

  @Scheduled(fixedDelay = 5000)
  public void publish() {
    service.publishEvents();
  }
}
