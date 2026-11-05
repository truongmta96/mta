package vn.mta.shop.service.impl;

import static vn.mta.shop.common.BusinessException.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.dto.PlaceOrderRequest;
import vn.mta.shop.model.QuotaCounter;
import vn.mta.shop.repository.CampaignRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.repository.IdentityRepository;
import vn.mta.shop.repository.InventoryRepository;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.OutboxRepository;
import vn.mta.shop.repository.PaymentRepository;
import vn.mta.shop.repository.ProductRepository;
import vn.mta.shop.repository.RefundRepository;
import vn.mta.shop.service.OrderService;

/** Mọi thay đổi đơn/kho/quota chạy cùng transaction. Khóa inventory trước sale/quota. */
@Service
public class OrderServiceImpl implements OrderService {
  private final CampaignRepository campaignRepository;
  private final CustomerRepository customerRepository;
  private final IdentityRepository identityRepository;
  private final InventoryRepository inventoryRepository;
  private final OrderRepository orderRepository;
  private final OutboxRepository outboxRepository;
  private final PaymentRepository paymentRepository;
  private final ProductRepository productRepository;
  private final RefundRepository refundRepository;
  private final ObjectMapper json;

  public OrderServiceImpl(
      CampaignRepository campaignRepository,
      CustomerRepository customerRepository,
      IdentityRepository identityRepository,
      InventoryRepository inventoryRepository,
      OrderRepository orderRepository,
      OutboxRepository outboxRepository,
      PaymentRepository paymentRepository,
      ProductRepository productRepository,
      RefundRepository refundRepository,
      ObjectMapper json) {
    this.campaignRepository = campaignRepository;
    this.customerRepository = customerRepository;
    this.identityRepository = identityRepository;
    this.inventoryRepository = inventoryRepository;
    this.orderRepository = orderRepository;
    this.outboxRepository = outboxRepository;
    this.paymentRepository = paymentRepository;
    this.productRepository = productRepository;
    this.refundRepository = refundRepository;
    this.json = json;
  }

  @Transactional
  @Override
  public Map<String, Object> place(long customer, String key, PlaceOrderRequest input)
      throws Exception {
    if (key.isBlank() || key.length() > 80) throw error(400, "Khóa yêu cầu không hợp lệ");
    if (customerRepository.lockActiveById(customer).isEmpty())
      throw error(403, "Hồ sơ khách hàng không khả dụng");
    String hash =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));
    var previous = orderRepository.findByIdempotencyKey(customer, key);
    if (!previous.isEmpty()) {
      if (!hash.equals(previous.get(0).get("request_hash")))
        throw error(409, "Khóa đã dùng với nội dung khác");
      return previous.get(0);
    }
    var rows = productRepository.findAvailableSku(input.skuId());
    if (rows.isEmpty()) throw error(404, "Không tìm thấy sản phẩm");
    if (inventoryRepository.reserve(input.quantity(), input.skuId()) != 1)
      throw error(409, "Không đủ hàng");
    BigDecimal price = (BigDecimal) rows.get(0).get("price");
    Long campaign = null;
    var sales = campaignRepository.findCurrentForSkuForUpdate(input.skuId());
    if (input.campaignId() != null) {
      sales =
          sales.stream()
              .filter(s -> ((Number) s.get("id")).longValue() == input.campaignId())
              .toList();
      if (sales.isEmpty()) throw error(409, "Chiến dịch chưa mở bán hoặc đã kết thúc");
    }
    if (!sales.isEmpty()) {
      var sale = sales.get(0);
      campaign = ((Number) sale.get("id")).longValue();
      price = (BigDecimal) sale.get("sale_price");
      if (campaignRepository.reserveItem(input.quantity(), campaign, input.skuId()) != 1)
        throw error(409, "Đã hết suất ưu đãi");
      campaignRepository.ensureCustomerQuota(campaign, input.skuId(), customer);
      if (campaignRepository.reserveCustomerQuota(
              input.quantity(),
              campaign,
              input.skuId(),
              customer,
              ((Number) sale.get("per_customer_limit")).intValue())
          != 1) throw error(409, "Bạn đã đạt giới hạn mua trong chiến dịch");
    }
    if (input.expectedPrice() != null && input.expectedPrice().compareTo(price) != 0)
      throw error(409, "Giá đã thay đổi. Vui lòng tải lại sản phẩm và xác nhận lại.");
    BigDecimal total = price.multiply(BigDecimal.valueOf(input.quantity()));
    orderRepository.insert(
        customer, key, hash, total, input.recipient(), input.phone(), input.address(), campaign);
    Long id = identityRepository.lastInsertedId();
    orderRepository.insertItem(
        id, input.skuId(), rows.get(0).get("name").toString(), input.quantity(), price);
    inventoryRepository.insertReservation(id, input.skuId(), input.quantity());
    event(id, "OrderCreated");
    return Map.of("id", id, "status", "PENDING_PAYMENT", "total", total);
  }

  @Override
  public Map<String, Object> owned(long id, long customer) {
    var rows = orderRepository.findOwned(id, customer);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy đơn hàng");
    return rows.get(0);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public Map<String, Object> lock(long id) {
    var rows = orderRepository.findForUpdate(id);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy đơn hàng");
    return rows.get(0);
  }

  @Override
  public boolean expired(Map<String, Object> o) {
    Object value = o.get("expires_at");
    LocalDateTime end =
        value instanceof LocalDateTime date ? date : ((java.sql.Timestamp) value).toLocalDateTime();
    return !end.isAfter(LocalDateTime.now(Clock.systemUTC()));
  }

  @Transactional
  @Override
  public Map<String, Object> detail(long id) {
    var out = new LinkedHashMap<String, Object>(lock(id));
    out.remove("request_hash");
    out.put("items", orderRepository.findItems(id));
    out.put("payments", paymentRepository.findHistoryByOrder(id));
    out.put("refunds", refundRepository.findByOrder(id));
    return out;
  }

  @Transactional
  @Override
  public Map<String, Object> customerDetail(long id, long customer) {
    owned(id, customer);
    return detail(id);
  }

  @Transactional
  @Override
  public void cancel(long id, Long customer) {
    var o = lock(id);
    if (customer != null && ((Number) o.get("customer_id")).longValue() != customer)
      throw error(404, "Không tìm thấy đơn hàng");
    if (Set.of("CANCELLED", "EXPIRED").contains(o.get("status"))) return;
    if (!"PENDING_PAYMENT".equals(o.get("status")))
      throw error(409, "Đơn đã thanh toán: hãy yêu cầu hoàn tiền");
    release(o, false);
    orderRepository.markCancelled(id);
    event(id, "OrderCancelled");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void expireLocked(Map<String, Object> o) {
    if ("PENDING_PAYMENT".equals(o.get("status")) && expired(o)) {
      release(o, false);
      orderRepository.markExpired(((Number) o.get("id")).longValue());
      o.put("status", "EXPIRED");
      event(((Number) o.get("id")).longValue(), "OrderExpired");
    }
  }

  // Chỉ gọi trong transaction đã khóa order. Mỗi reservation chỉ giải phóng một lần.
  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void release(Map<String, Object> o, boolean returnReceived) {
    for (var r :
        inventoryRepository.findReleasableReservations(((Number) o.get("id")).longValue())) {
      String state = r.get("status").toString();
      int q = ((Number) r.get("quantity")).intValue();
      long sku = ((Number) r.get("sku_id")).longValue();
      if (state.equals("FULFILLED")) {
        if (!returnReceived)
          throw error(409, "Cần xác nhận đã nhận hàng hoàn trước khi hoàn tiền đơn đã giao");
        inventoryRepository.returnStock(q, sku);
      } else inventoryRepository.release(q, sku);
      if (o.get("campaign_id") != null) {
        QuotaCounter field = state.equals("HELD") ? QuotaCounter.RESERVED : QuotaCounter.SOLD;
        campaignRepository.releaseItemCounter(
            field, q, ((Number) o.get("campaign_id")).longValue(), sku);
        campaignRepository.releaseCustomerCounter(
            field,
            q,
            ((Number) o.get("campaign_id")).longValue(),
            sku,
            ((Number) o.get("customer_id")).longValue());
      }
      inventoryRepository.updateReservationStatus(
          state.equals("FULFILLED") ? "RETURNED" : "RELEASED", ((Number) r.get("id")).longValue());
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void paid(Map<String, Object> o) {
    for (var r : inventoryRepository.findHeldReservations(((Number) o.get("id")).longValue())) {
      if (o.get("campaign_id") != null) {
        campaignRepository.confirmItem(
            ((Number) r.get("quantity")).intValue(),
            ((Number) o.get("campaign_id")).longValue(),
            ((Number) r.get("sku_id")).longValue());
        campaignRepository.confirmCustomerQuota(
            ((Number) r.get("quantity")).intValue(),
            ((Number) o.get("campaign_id")).longValue(),
            ((Number) r.get("sku_id")).longValue(),
            ((Number) o.get("customer_id")).longValue());
      }
      inventoryRepository.confirmReservation(((Number) r.get("id")).longValue());
    }
    orderRepository.markPaid(((Number) o.get("id")).longValue());
    event(((Number) o.get("id")).longValue(), "PaymentSucceeded");
  }

  @Transactional
  @Override
  public void progress(long id, String next) {
    var o = lock(id);
    String current = o.get("status").toString();
    if (!Map.of("PAID", "PROCESSING", "PROCESSING", "SHIPPED", "SHIPPED", "COMPLETED")
        .getOrDefault(current, "")
        .equals(next)) throw error(409, "Chuyển trạng thái đơn không hợp lệ");
    if (refundRepository.countRequestedForOrder(id) > 0)
      throw error(409, "Đơn đang chờ xử lý hoàn tiền");
    if (next.equals("SHIPPED")) {
      for (var r : inventoryRepository.findConfirmedReservations(id)) {
        inventoryRepository.ship(
            ((Number) r.get("quantity")).intValue(), ((Number) r.get("sku_id")).longValue());
        inventoryRepository.fulfillReservation(((Number) r.get("id")).longValue());
      }
    }
    orderRepository.updateStatus(next, id);
    event(id, "Order" + next);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void cancelPendingCustomer(long customer) {
    for (var o : orderRepository.findPendingForCustomerForUpdate(customer))
      cancel(((Number) o.get("id")).longValue(), customer);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void event(long id, String type) {
    outboxRepository.insertOrderEvent(id, type);
  }
}
