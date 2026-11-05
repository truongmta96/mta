package vn.mta.shop.service.impl;

import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.cache.CacheRegion;
import vn.mta.shop.dto.PlaceOrderRequest;
import vn.mta.shop.dto.ProfileRequest;
import vn.mta.shop.dto.RefundRequest;
import vn.mta.shop.repository.CampaignRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.repository.InventoryRepository;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.ProductRepository;
import vn.mta.shop.repository.SystemRepository;
import vn.mta.shop.service.AccountService;
import vn.mta.shop.service.OrderService;
import vn.mta.shop.service.PaymentService;
import vn.mta.shop.service.ReadCacheService;
import vn.mta.shop.service.ShopService;

@Service
public class ShopServiceImpl implements ShopService {
  private final CampaignRepository campaignRepository;
  private final ReadCacheService cache;
  private final InventoryRepository inventoryRepository;
  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final SystemRepository systemRepository;
  private final AccountService accounts;
  private final OrderService orders;
  private final PaymentService payments;

  public ShopServiceImpl(
      CampaignRepository campaignRepository,
      ReadCacheService cache,
      InventoryRepository inventoryRepository,
      CustomerRepository customerRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      SystemRepository systemRepository,
      AccountService accounts,
      OrderService orders,
      PaymentService payments) {
    this.campaignRepository = campaignRepository;
    this.cache = cache;
    this.inventoryRepository = inventoryRepository;
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
    // Chỉ tên/biến thể/giá cơ bản đi qua cache; method reference chưa đọc DB cho tới khi .get() được gọi.
    // Nếu Redis lỗi, ReadCacheServiceImpl gọi findCatalogMetadata trực tiếp để fallback.
    var rows = copy(cache.getOrLoad(CacheRegion.PRODUCTS, productRepository::findCatalogMetadata));
    var ids = rows.stream().map(p -> ((Number) p.get("id")).longValue()).toList();
    // Tồn kho phải mới: lấy theo lô từ MySQL cho các SKU, không dùng số lượng từ cache.
    var available =
        inventoryRepository.findAvailableForSkus(ids).stream()
            .collect(
                Collectors.toMap(
                    p -> ((Number) p.get("sku_id")).longValue(),
                    p -> ((Number) p.get("available")).intValue()));
    rows.removeIf(p -> !available.containsKey(((Number) p.get("id")).longValue()));
    for (var p : rows) p.put("available", available.get(((Number) p.get("id")).longValue()));
    for (var p : rows) {
      // Giá/quota sale hiện tại tiếp tục đọc DB, không lấy theo snapshot metadata có TTL.
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
    // Chỉ cache header chiến dịch. Item/quota/tồn bên dưới vẫn đọc DB mỗi lần trả response.
    var rows = copy(cache.getOrLoad(CacheRegion.CAMPAIGNS, campaignRepository::findPublicMetadata));
    // Không dùng giờ server cũ nằm trong cache; lọc lại chiến dịch hết hạn theo giờ hiện tại.
    Instant now = Instant.now();
    rows.removeIf(
        c ->
            !instant(c.get("ends_at")).isAfter(now)
                || !java.util.Set.of("SCHEDULED", "ACTIVE").contains(c.get("status")));
    for (var c : rows) c.put("server_time", now.toString());
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

  // Không bổ sung giá sale/tồn/quota lên đối tượng metadata dùng chung trong cache.
  // Sao chép Map trước khi ghép dữ liệu động, tránh làm metadata bị nhiễm dữ liệu của request trước.
  private List<Map<String, Object>> copy(List<Map<String, Object>> rows) {
    var result = new ArrayList<Map<String, Object>>();
    for (var row : rows) result.add(new LinkedHashMap<>(row));
    return result;
  }

  private Instant instant(Object value) {
    if (value instanceof LocalDateTime date) return date.toInstant(ZoneOffset.UTC);
    if (value instanceof java.util.Date date) return date.toInstant();
    return OffsetDateTime.parse(value.toString()).toInstant();
  }
}
