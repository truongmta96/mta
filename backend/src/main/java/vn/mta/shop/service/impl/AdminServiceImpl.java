package vn.mta.shop.service.impl;

import static vn.mta.shop.common.BusinessException.error;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.mta.shop.dto.AccountRequest;
import vn.mta.shop.dto.CampaignRequest;
import vn.mta.shop.dto.CustomerRequest;
import vn.mta.shop.dto.OrderStatusRequest;
import vn.mta.shop.dto.ProductRequest;
import vn.mta.shop.dto.RefundDecisionRequest;
import vn.mta.shop.dto.RefundRequest;
import vn.mta.shop.repository.AccountRepository;
import vn.mta.shop.repository.AuditRepository;
import vn.mta.shop.repository.CampaignRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.ProductRepository;
import vn.mta.shop.repository.RefundRepository;
import vn.mta.shop.service.AccountService;
import vn.mta.shop.service.AdminService;
import vn.mta.shop.service.ManagementService;
import vn.mta.shop.service.OrderService;
import vn.mta.shop.service.PaymentService;

@Service
public class AdminServiceImpl implements AdminService {
  private final AccountRepository accountRepository;
  private final AuditRepository auditRepository;
  private final CampaignRepository campaignRepository;
  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final RefundRepository refundRepository;
  private final ManagementService management;
  private final AccountService accounts;
  private final OrderService orders;
  private final PaymentService payments;

  public AdminServiceImpl(
      AccountRepository accountRepository,
      AuditRepository auditRepository,
      CampaignRepository campaignRepository,
      CustomerRepository customerRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      RefundRepository refundRepository,
      ManagementService management,
      AccountService accounts,
      OrderService orders,
      PaymentService payments) {
    this.accountRepository = accountRepository;
    this.auditRepository = auditRepository;
    this.campaignRepository = campaignRepository;
    this.customerRepository = customerRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.refundRepository = refundRepository;
    this.management = management;
    this.accounts = accounts;
    this.orders = orders;
    this.payments = payments;
  }

  private long actor(String email) {
    return accounts.actorId(email);
  }

  @Override
  public List<Map<String, Object>> products(int page, String q) {
    return productRepository.searchForAdmin(prefix(q), offset(page));
  }

  @Override
  public Map<String, Long> addProduct(ProductRequest input, String email) {
    return Map.of("id", management.product(null, input, actor(email)));
  }

  @Override
  public Map<String, Long> editProduct(long id, ProductRequest input, String email) {
    return Map.of("id", management.product(id, input, actor(email)));
  }

  @Override
  public Map<String, String> deleteProduct(long id, String email) {
    management.productDeleted(id, true, actor(email));
    return ok();
  }

  @Override
  public Map<String, String> restoreProduct(long id, String email) {
    management.productDeleted(id, false, actor(email));
    return ok();
  }

  @Override
  public List<Map<String, Object>> listOrders(int page, String q) {
    return orderRepository.searchForAdmin(prefix(q), offset(page));
  }

  @Override
  public Map<String, Object> detail(long id) {
    return orders.detail(id);
  }

  @Override
  public Map<String, String> progress(long id, OrderStatusRequest input) {
    orders.progress(id, input.status());
    return ok();
  }

  @Override
  public Map<String, String> cancel(long id) {
    orders.cancel(id, null);
    return ok();
  }

  @Override
  public Map<String, Object> refund(long id, RefundRequest input) {
    return payments.requestRefund(id, null, input.reason());
  }

  @Override
  public List<Map<String, Object>> accountList(int page, String q) {
    return accountRepository.searchForAdmin(prefix(q), offset(page));
  }

  @Override
  public Map<String, Long> addAccount(AccountRequest input, String email) {
    return Map.of("id", management.account(null, input, actor(email)));
  }

  @Override
  public Map<String, Long> editAccount(long id, AccountRequest input, String email) {
    return Map.of("id", management.account(id, input, actor(email)));
  }

  @Override
  public Map<String, String> deleteAccount(long id, String email) {
    management.accountDeleted(id, true, actor(email));
    return ok();
  }

  @Override
  public Map<String, String> restoreAccount(long id, String email) {
    management.accountDeleted(id, false, actor(email));
    return ok();
  }

  @Override
  public List<Map<String, Object>> customers(int page, String q) {
    return customerRepository.searchForAdmin(prefix(q), offset(page));
  }

  @Override
  public Map<String, String> customer(long id, CustomerRequest input, String email) {
    management.customer(id, input, actor(email));
    return ok();
  }

  @Override
  public Map<String, String> deleteCustomer(long id, String email) {
    management.customerDeleted(id, true, actor(email));
    return ok();
  }

  @Override
  public Map<String, String> restoreCustomer(long id, String email) {
    management.customerDeleted(id, false, actor(email));
    return ok();
  }

  @Override
  public List<Map<String, Object>> campaigns(int page, String q) {
    var rows = campaignRepository.searchForAdmin(prefix(q), offset(page));
    for (var c : rows)
      c.put("items", campaignRepository.findItems(((Number) c.get("id")).longValue()));
    return rows;
  }

  @Override
  public Map<String, Long> addSale(CampaignRequest input, String email) {
    return Map.of("id", management.campaign(null, input, actor(email)));
  }

  @Override
  public Map<String, Long> editSale(long id, CampaignRequest input, String email) {
    return Map.of("id", management.campaign(id, input, actor(email)));
  }

  @Override
  public Map<String, String> stopSale(long id, OrderStatusRequest input, String email) {
    management.stopCampaign(id, input.status(), actor(email));
    return ok();
  }

  @Override
  public Map<String, String> deleteSale(long id, String email) {
    management.deleteCampaign(id, actor(email));
    return ok();
  }

  @Override
  public List<Map<String, Object>> refunds(int page, String q) {
    return refundRepository.searchForAdmin(prefix(q), offset(page));
  }

  @Override
  public Map<String, String> decide(long id, RefundDecisionRequest input) {
    payments.decideRefund(id, input.approve(), input.returnReceived());
    return ok();
  }

  @Override
  public List<Map<String, Object>> audit(int page, String q) {
    return auditRepository.searchRecent(prefix(q), offset(page));
  }

  private int offset(int page) {
    if (page < 0 || page > 100000) throw error(400, "Trang không hợp lệ");
    return page * 50;
  }

  private String prefix(String q) {
    if (q.length() > 190) throw error(400, "Từ khóa quá dài");
    return q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  private Map<String, String> ok() {
    return Map.of("message", "Đã lưu thay đổi");
  }
}
