package vn.mta.shop.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.dto.PlaceOrderRequest;
import vn.mta.shop.dto.ProfileRequest;
import vn.mta.shop.dto.RefundRequest;
import vn.mta.shop.repository.CampaignRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.ProductRepository;
import vn.mta.shop.repository.SystemRepository;
import vn.mta.shop.service.AccountService;
import vn.mta.shop.service.OrderService;
import vn.mta.shop.service.PaymentService;
import vn.mta.shop.service.ShopService;

@Service
public class ShopServiceImpl implements ShopService {
  private final CampaignRepository campaignRepository;
  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final SystemRepository systemRepository;
  private final AccountService accounts;
  private final OrderService orders;
  private final PaymentService payments;

  public ShopServiceImpl(
      CampaignRepository campaignRepository,
      CustomerRepository customerRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      SystemRepository systemRepository,
      AccountService accounts,
      OrderService orders,
      PaymentService payments) {
    this.campaignRepository = campaignRepository;
    this.customerRepository = customerRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.systemRepository = systemRepository;
    this.accounts = accounts;
    this.orders = orders;
    this.payments = payments;
  }

  @Override
  public Map<String, Object> health() {
    return Map.of("status", "UP", "database", systemRepository.ping());
  }

  @Override
  public List<Map<String, Object>> products() {
    var rows = productRepository.findCatalog();
    for (var p : rows) {
      var active = campaignRepository.findCurrentPriceForSku(((Number) p.get("id")).longValue());
      if (!active.isEmpty()) {
        var a = active.get(0);
        p.put("original_price", p.get("price"));
        p.put("price", a.get("sale_price"));
        p.put("campaign_id", a.get("id"));
        p.put("campaign_name", a.get("name"));
        p.put(
            "available",
            Math.min(
                ((Number) p.get("available")).intValue(),
                ((Number) a.get("remaining")).intValue()));
      }
    }
    return rows;
  }

  @Override
  public List<Map<String, Object>> campaigns() {
    var rows = campaignRepository.findPublicUpcoming();
    for (var c : rows)
      c.put("items", campaignRepository.findPublicItems(((Number) c.get("id")).longValue()));
    return rows;
  }

  @Override
  public List<Map<String, Object>> list(String email) {
    return orderRepository.findCustomerHistory(accounts.customerId(email));
  }

  @Override
  public Map<String, Object> detail(long id, String email) {
    return orders.customerDetail(id, accounts.customerId(email));
  }

  @Override
  public Map<String, Object> place(String key, PlaceOrderRequest input, String email)
      throws Exception {
    return orders.place(accounts.customerId(email), key, input);
  }

  @Override
  public Map<String, String> cancel(long id, String email) {
    orders.cancel(id, accounts.customerId(email));
    return Map.of("message", "Đã hủy đơn");
  }

  @Override
  public Map<String, Object> pay(long id, String email) {
    return payments.create(id, accounts.customerId(email));
  }

  @Override
  public Map<String, Object> refund(long id, RefundRequest input, String email) {
    return payments.requestRefund(id, accounts.customerId(email), input.reason());
  }

  @Override
  public Map<String, Object> customer(String email) {
    return customerRepository.getProfile(accounts.customerId(email));
  }

  @Transactional
  @Override
  public Map<String, String> profile(ProfileRequest input, String email) {
    customerRepository.updateProfile(
        input.name().strip(), input.phone(), accounts.customerId(email));
    return Map.of("message", "Đã cập nhật hồ sơ");
  }
}
