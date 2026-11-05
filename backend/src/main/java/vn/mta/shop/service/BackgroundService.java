package vn.mta.shop.service;

public interface BackgroundService {
  void expireOrders();

  void publishEvents();
}
