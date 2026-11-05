package vn.mta.shop.service.impl;

import static vn.mta.shop.common.BusinessException.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.repository.IdentityRepository;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.PaymentRepository;
import vn.mta.shop.repository.RefundRepository;
import vn.mta.shop.service.AccountService;
import vn.mta.shop.service.OrderService;
import vn.mta.shop.service.PaymentService;

/** Nhà cung cấp SANDBOX mô phỏng giao dịch; không thu hoặc chuyển tiền thật. */
@Service
public class PaymentServiceImpl implements PaymentService {
  private final IdentityRepository identityRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final RefundRepository refundRepository;
  private final OrderService orders;
  private final AccountService accounts;
  private final ObjectMapper json;
  private final boolean enabled;
  private final byte[] secret;

  public PaymentServiceImpl(
      IdentityRepository identityRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      RefundRepository refundRepository,
      OrderService orders,
      AccountService accounts,
      ObjectMapper json,
      @Value("${app.payment.sandbox-enabled:false}") boolean enabled,
      @Value("${app.payment.webhook-secret:}") String secret) {
    this.identityRepository = identityRepository;
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.refundRepository = refundRepository;
    this.orders = orders;
    this.accounts = accounts;
    this.json = json;
    this.enabled = enabled;
    this.secret = secret.isBlank() ? randomSecret() : secret.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] randomSecret() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return b;
  }

  @Override
  public Map<String, Object> config() {
    return Map.of(
        "enabled", enabled, "provider", enabled ? "SANDBOX" : "NOT_CONFIGURED", "realMoney", false);
  }

  private void available() {
    if (!enabled) throw error(503, "Chưa cấu hình nhà cung cấp thanh toán. Sandbox đang tắt.");
  }

  @Transactional
  @Override
  public Map<String, Object> create(long id, long customer) {
    available();
    orders.owned(id, customer);
    var o = orders.lock(id);
    orders.expireLocked(o);
    if (!"PENDING_PAYMENT".equals(o.get("status")))
      return Map.of("status", o.get("status"), "message", "Đơn không còn chờ thanh toán");
    var pending = paymentRepository.findPendingForOrder(id);
    if (!pending.isEmpty()) return pending.get(0);
    String ref = "SB-" + UUID.randomUUID();
    paymentRepository.insertSandbox(id, ref, (BigDecimal) o.get("total"));
    Long attempt = identityRepository.lastInsertedId();
    return Map.of(
        "id", attempt, "provider", "SANDBOX", "status", "PENDING", "amount", o.get("total"));
  }

  // Tạo payload và ký ở server; không gửi webhook secret cho trình duyệt.
  private Map<String, String> signedSandbox(long attempt, long customer, String result)
      throws Exception {
    available();
    if (!Set.of("SUCCEEDED", "FAILED").contains(result))
      throw error(400, "Kết quả sandbox không hợp lệ");
    var rows = paymentRepository.findOwnedSandbox(attempt, customer);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy giao dịch");
    var p = rows.get(0);
    String payload =
        json.writeValueAsString(
            Map.of(
                "eventId",
                "SB-" + attempt + "-" + result,
                "reference",
                p.get("provider_reference"),
                "status",
                result,
                "amount",
                p.get("amount"),
                "timestamp",
                Instant.now().getEpochSecond()));
    return Map.of("payload", payload, "signature", HexFormat.of().formatHex(hmac(payload)));
  }

  @Transactional(rollbackFor = Exception.class)
  @Override
  public Map<String, Object> completeSandbox(long attempt, String email, String result)
      throws Exception {
    var signed = signedSandbox(attempt, accounts.customerId(email), result);
    // webhook được gọi nội bộ, transaction của completeSandbox bao trùm toàn bộ xử lý.
    return webhook(signed.get("payload"), signed.get("signature"));
  }

  private byte[] hmac(String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
  }

  public record Notification(
      String eventId, String reference, String status, BigDecimal amount, long timestamp) {}

  @Transactional
  @Override
  public Map<String, Object> webhook(String payload, String signature) throws Exception {
    available();
    if (payload.length() > 64000) throw error(413, "Payload webhook quá lớn");
    byte[] received;
    try {
      received = HexFormat.of().parseHex(signature);
    } catch (IllegalArgumentException ex) {
      throw error(401, "Chữ ký webhook không hợp lệ");
    }
    if (!MessageDigest.isEqual(received, hmac(payload)))
      throw error(401, "Chữ ký webhook không hợp lệ");
    Notification n;
    try {
      n = json.readValue(payload, Notification.class);
    } catch (Exception ex) {
      throw error(400, "Payload webhook không hợp lệ");
    }
    long now = Instant.now().getEpochSecond();
    if (n == null
        || n.eventId() == null
        || n.eventId().isBlank()
        || n.eventId().length() > 100
        || n.reference() == null
        || n.reference().length() > 100
        || n.amount() == null
        || n.amount().signum() < 0
        || !Set.of("SUCCEEDED", "FAILED").contains(Objects.toString(n.status(), ""))
        || n.timestamp() < now - 300
        || n.timestamp() > now + 300)
      throw error(400, "Nội dung hoặc thời gian webhook không hợp lệ");
    var found = paymentRepository.findOrderByReference(n.reference());
    if (found.isEmpty()) throw error(404, "Không tìm thấy giao dịch");
    long order = ((Number) found.get(0).get("order_id")).longValue();
    var o = orders.lock(order);
    var p = paymentRepository.findByReferenceForUpdate(n.reference());
    if (((BigDecimal) p.get("amount")).compareTo(n.amount()) != 0)
      throw error(400, "Số tiền webhook không khớp");
    var old = paymentRepository.findWebhookForUpdate(n.eventId());
    if (!old.isEmpty()) {
      var previous = json.readValue(old.get(0).get("payload").toString(), Notification.class);
      if (!previous.reference().equals(n.reference())
          || !previous.status().equals(n.status())
          || previous.amount().compareTo(n.amount()) != 0)
        throw error(409, "Event ID đã dùng cho nội dung khác");
      return Map.of("duplicate", true, "orderId", order);
    }
    paymentRepository.insertWebhook(n.eventId(), payload);
    String state = p.get("status").toString();
    if (!state.equals("PENDING")) return Map.of("ignored", true, "orderId", order);
    if (n.status().equals("FAILED")) {
      paymentRepository.markFailed(((Number) p.get("id")).longValue());
      return Map.of("status", "FAILED", "orderId", order);
    }
    orders.expireLocked(o);
    if ("PENDING_PAYMENT".equals(o.get("status"))) {
      paymentRepository.markSucceeded(((Number) p.get("id")).longValue());
      orders.paid(o);
      return Map.of("status", "PAID", "orderId", order);
    }
    // Sandbox hoàn tự động tiền đến sau hủy/hết hạn. Không tái giữ hoặc lấy hàng của người khác.
    paymentRepository.markRefunded(((Number) p.get("id")).longValue());
    refundRepository.insertLatePayment(
        order, ((Number) p.get("id")).longValue(), (BigDecimal) p.get("amount"));
    orders.event(order, "LatePaymentRefunded");
    return Map.of("status", "REFUNDED", "orderId", order);
  }

  @Transactional
  @Override
  public Map<String, Object> requestRefund(long id, Long customer, String reason) {
    available();
    if (customer != null) orders.owned(id, customer);
    var o = orders.lock(id);
    var existing = refundRepository.findByOrder(id);
    if (!existing.isEmpty()) {
      var old = existing.get(0);
      if ("REJECTED".equals(old.get("status"))) {
        refundRepository.reopen(reason, ((Number) old.get("id")).longValue());
        return refundRepository.getById(((Number) old.get("id")).longValue());
      }
      return old;
    }
    if (!Set.of("PAID", "PROCESSING", "SHIPPED", "COMPLETED").contains(o.get("status")))
      throw error(409, "Chỉ yêu cầu hoàn tiền đơn đã thanh toán");
    var paid = paymentRepository.findSucceededSandboxForOrder(id);
    if (paid.isEmpty()) throw error(409, "Không tìm thấy giao dịch đã thu tiền");
    refundRepository.insertRequest(
        id,
        ((Number) paid.get(0).get("id")).longValue(),
        (BigDecimal) paid.get(0).get("amount"),
        reason);
    orders.event(id, "RefundRequested");
    return refundRepository.getByOrder(id);
  }

  @Transactional
  @Override
  public void decideRefund(long refund, boolean approve, boolean returnReceived) {
    available();
    var rows = refundRepository.findOrderId(refund);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy yêu cầu hoàn tiền");
    long id = ((Number) rows.get(0).get("order_id")).longValue();
    var o = orders.lock(id);
    var r = refundRepository.findForUpdate(refund);
    if ("SUCCEEDED".equals(r.get("status"))) return;
    if (!"REQUESTED".equals(r.get("status"))) throw error(409, "Yêu cầu không còn chờ xử lý");
    if (!approve) {
      refundRepository.markRejected(refund);
      return;
    }
    orders.release(o, returnReceived);
    refundRepository.markSucceeded(returnReceived, refund);
    paymentRepository.markRefunded(((Number) r.get("payment_attempt_id")).longValue());
    orderRepository.markRefunded(id);
    orders.event(id, "RefundSucceeded");
  }
}
