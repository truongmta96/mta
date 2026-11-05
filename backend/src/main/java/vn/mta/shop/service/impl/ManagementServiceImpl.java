package vn.mta.shop.service.impl;

import static vn.mta.shop.common.BusinessException.error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.common.EmailNormalizer;
import vn.mta.shop.dto.AccountRequest;
import vn.mta.shop.dto.CampaignRequest;
import vn.mta.shop.dto.CustomerRequest;
import vn.mta.shop.dto.ProductRequest;
import vn.mta.shop.dto.SaleItemRequest;
import vn.mta.shop.repository.AccountRepository;
import vn.mta.shop.repository.AuditRepository;
import vn.mta.shop.repository.CampaignRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.repository.IdentityRepository;
import vn.mta.shop.repository.InventoryRepository;
import vn.mta.shop.repository.OrderRepository;
import vn.mta.shop.repository.ProductRepository;
import vn.mta.shop.service.ManagementService;
import vn.mta.shop.service.OrderService;

@Service
public class ManagementServiceImpl implements ManagementService {
  private final AccountRepository accountRepository;
  private final AuditRepository auditRepository;
  private final CampaignRepository campaignRepository;
  private final CustomerRepository customerRepository;
  private final IdentityRepository identityRepository;
  private final InventoryRepository inventoryRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final PasswordEncoder passwords;
  private final OrderService orders;

  public ManagementServiceImpl(
      AccountRepository accountRepository,
      AuditRepository auditRepository,
      CampaignRepository campaignRepository,
      CustomerRepository customerRepository,
      IdentityRepository identityRepository,
      InventoryRepository inventoryRepository,
      OrderRepository orderRepository,
      ProductRepository productRepository,
      PasswordEncoder passwords,
      OrderService orders) {
    this.accountRepository = accountRepository;
    this.auditRepository = auditRepository;
    this.campaignRepository = campaignRepository;
    this.customerRepository = customerRepository;
    this.identityRepository = identityRepository;
    this.inventoryRepository = inventoryRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.passwords = passwords;
    this.orders = orders;
  }

  @Transactional
  @Override
  public long product(Long id, ProductRequest p, long actor) {
    long product;
    if (id == null) {
      productRepository.insertProduct(p.name(), p.brand(), p.description(), p.color());
      product = identityRepository.lastInsertedId();
      productRepository.insertSku(
          product, "SKU-" + UUID.randomUUID(), p.storageGb(), p.color(), p.price());
      id = identityRepository.lastInsertedId();
      inventoryRepository.insert(id, p.onHand());
    } else {
      var rows = productRepository.findSkuInventoryForUpdate(id);
      if (rows.isEmpty()) throw error(404, "Không tìm thấy SKU");
      product = ((Number) rows.get(0).get("product_id")).longValue();
      if (p.onHand() < ((Number) rows.get(0).get("reserved")).intValue())
        throw error(409, "Tồn kho không được nhỏ hơn số lượng đang giữ");
      productRepository.updateProduct(p.name(), p.brand(), p.description(), p.color(), product);
      productRepository.updateSku(p.storageGb(), p.color(), p.price(), id);
      inventoryRepository.updateOnHand(p.onHand(), id);
    }
    audit(actor, "SAVE_PRODUCT", "SKU", id);
    return id;
  }

  @Transactional
  @Override
  public void productDeleted(long sku, boolean deleted, long actor) {
    var rows = productRepository.findProductIdBySku(sku);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy SKU");
    productRepository.setDeleted(
        deleted,
        deleted ? "ARCHIVED" : "ACTIVE",
        ((Number) rows.get(0).get("product_id")).longValue());
    audit(actor, deleted ? "DELETE_PRODUCT" : "RESTORE_PRODUCT", "SKU", sku);
  }

  private void passwordValid(String p) {
    if (p == null
        || p.length() < 10
        || p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw error(400, "Mật khẩu từ 10 ký tự, tối đa 72 bytes UTF-8");
  }

  private void adminGuard(
      long target, long actor, String nextRole, String nextStatus, boolean deleted) {
    var active = accountRepository.findActiveAdminsForUpdate();
    if (target == actor
        && active.stream().anyMatch(a -> ((Number) a.get("id")).longValue() == target)
        && (deleted || !nextRole.equals("ADMIN") || !nextStatus.equals("ACTIVE")))
      throw error(409, "Không tự khóa, xóa hoặc hạ quyền tài khoản quản trị đang dùng");
    if (active.stream().anyMatch(a -> ((Number) a.get("id")).longValue() == target)
        && active.size() <= 1
        && (deleted || !nextRole.equals("ADMIN") || !nextStatus.equals("ACTIVE")))
      throw error(409, "Phải giữ ít nhất một quản trị viên hoạt động");
  }

  @Transactional
  @Override
  public long account(Long id, AccountRequest input, long actor) {
    if (id == null) {
      passwordValid(input.password());
      accountRepository.insertManaged(
          EmailNormalizer.normalize(input.email()),
          passwords.encode(input.password()),
          input.role(),
          input.status(),
          input.name());
      id = identityRepository.lastInsertedId();
    } else {
      adminGuard(id, actor, input.role(), input.status(), false);
      var a = accountRepository.findDeletionStateForUpdate(id);
      if (a.isEmpty()) throw error(404, "Không tìm thấy tài khoản");
      if (a.get(0).get("deleted_at") != null) throw error(409, "Khôi phục tài khoản trước khi sửa");
      accountRepository.updateManaged(
          EmailNormalizer.normalize(input.email()), input.role(), input.status(), input.name(), id);
      if (input.password() != null && !input.password().isBlank()) {
        passwordValid(input.password());
        accountRepository.updatePassword(passwords.encode(input.password()), id);
      }
    }
    var customer = customerRepository.findIdsByAccount(id);
    if (input.role().equals("CUSTOMER") && customer.isEmpty())
      customerRepository.insert(id, input.name());
    else if (!customer.isEmpty()) customerRepository.updateNameByAccount(input.name(), id);
    audit(actor, "SAVE_ACCOUNT", "ACCOUNT", id);
    return id;
  }

  @Transactional
  @Override
  public void accountDeleted(long id, boolean deleted, long actor) {
    var rows = accountRepository.findRoleAndStatus(id);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy tài khoản");
    adminGuard(
        id, actor, rows.get(0).get("role").toString(), deleted ? "DELETED" : "ACTIVE", deleted);
    accountRepository.lockById(id);
    accountRepository.setDeleted(deleted, deleted ? "DELETED" : "ACTIVE", id);
    for (var c : customerRepository.findIdsByAccountForUpdate(id)) {
      long customer = ((Number) c.get("id")).longValue();
      if (deleted) orders.cancelPendingCustomer(customer);
      customerRepository.setDeleted(deleted, customer);
    }
    audit(actor, deleted ? "DELETE_ACCOUNT" : "RESTORE_ACCOUNT", "ACCOUNT", id);
  }

  @Transactional
  @Override
  public void customer(long id, CustomerRequest input, long actor) {
    if (customerRepository.updateActive(input.name(), input.phone(), id) == 0)
      throw error(404, "Không tìm thấy khách hàng đang hoạt động");
    audit(actor, "SAVE_CUSTOMER", "CUSTOMER", id);
  }

  @Transactional
  @Override
  public void customerDeleted(long id, boolean deleted, long actor) {
    var rows = customerRepository.findAccountId(id);
    if (rows.isEmpty()) throw error(404, "Không tìm thấy khách hàng");
    Object account = rows.get(0).get("account_id");
    if (account != null) {
      var a = accountRepository.findState(((Number) account).longValue());
      adminGuard(
          ((Number) account).longValue(),
          actor,
          a.get("role").toString(),
          deleted ? "LOCKED" : a.get("status").toString(),
          false);
      if (!deleted && a.get("deleted_at") != null)
        throw error(409, "Hãy khôi phục tài khoản liên kết trước");
      if (deleted) accountRepository.lockAndRevokeSessions(((Number) account).longValue());
    }
    customerRepository.lockById(id);
    if (deleted) orders.cancelPendingCustomer(id);
    customerRepository.setDeleted(deleted, id);
    audit(actor, deleted ? "DELETE_CUSTOMER" : "RESTORE_CUSTOMER", "CUSTOMER", id);
  }

  @Transactional
  @Override
  public void deleteSelf(String email, String password) {
    var a = accountRepository.findCredentialsForUpdate(email);
    if (!"CUSTOMER".equals(a.get("role"))) throw error(403, "Quản trị viên không thể tự xóa ở đây");
    if (password == null || !passwords.matches(password, a.get("password_hash").toString()))
      throw error(401, "Mật khẩu xác nhận không đúng");
    accountDeleted(((Number) a.get("id")).longValue(), true, ((Number) a.get("id")).longValue());
  }

  @Transactional
  @Override
  public long campaign(Long id, CampaignRequest c, long actor) {
    if (!c.endsAt().isAfter(c.startsAt())) throw error(400, "Kết thúc phải sau thời điểm bắt đầu");
    if (c.items().stream().map(SaleItemRequest::skuId).distinct().count() != c.items().size())
      throw error(400, "SKU bị lặp trong chiến dịch");
    var items = new ArrayList<>(c.items());
    items.sort(Comparator.comparingLong(SaleItemRequest::skuId));
    // Cùng khóa inventory với luồng đặt hàng để ngăn tạo hai lịch sale giao nhau cho một SKU.
    for (var item : items)
      if (inventoryRepository.lockBySku(item.skuId()).isEmpty())
        throw error(404, "SKU không tồn tại");
    if (id != null) {
      var existing = campaignRepository.findActiveRecordForUpdate(id);
      if (existing.isEmpty()) throw error(404, "Không tìm thấy chiến dịch");
      if (orderRepository.countByCampaign(id) > 0)
        throw error(409, "Chiến dịch có đơn: chỉ có thể kết thúc/hủy để giữ lịch sử và quota");
    }
    for (var item : items) {
      if (!c.status().equals("DRAFT")
          && campaignRepository.countOverlapping(
                  item.skuId(), id == null ? -1 : id, c.endsAt(), c.startsAt())
              > 0) throw error(409, "SKU có lịch sale trùng thời gian");
    }
    if (id == null) {
      campaignRepository.insert(c.name(), c.startsAt(), c.endsAt(), c.status());
      id = identityRepository.lastInsertedId();
    } else {
      campaignRepository.update(c.name(), c.startsAt(), c.endsAt(), c.status(), id);
      campaignRepository.deleteItems(id);
    }
    for (var item : items)
      campaignRepository.insertItem(
          id, item.skuId(), item.price(), item.quota(), item.perCustomerLimit());
    audit(actor, "SAVE_CAMPAIGN", "CAMPAIGN", id);
    return id;
  }

  @Transactional
  @Override
  public void stopCampaign(long id, String status, long actor) {
    if (!Set.of("ENDED", "CANCELLED").contains(status)) throw error(400, "Trạng thái không hợp lệ");
    var skus = campaignRepository.findSkuIds(id);
    for (var s : skus) inventoryRepository.lockBySku(((Number) s.get("sku_id")).longValue());
    if (campaignRepository.updateStatus(status, id) == 0)
      throw error(404, "Không tìm thấy chiến dịch");
    audit(actor, "STOP_CAMPAIGN", "CAMPAIGN", id);
  }

  @Transactional
  @Override
  public void deleteCampaign(long id, long actor) {
    stopCampaign(id, "CANCELLED", actor);
    campaignRepository.markDeleted(id);
    audit(actor, "DELETE_CAMPAIGN", "CAMPAIGN", id);
  }

  @Override
  public void audit(long actor, String action, String entity, long id) {
    auditRepository.insert(actor, action, entity, id);
  }
}
