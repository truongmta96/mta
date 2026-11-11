package vn.mta.shop.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.mta.shop.common.BusinessException;
import vn.mta.shop.common.EmailNormalizer;
import vn.mta.shop.repository.AccountRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.security.AccountPrincipal;
import vn.mta.shop.service.AccountService;

/** Đọc trạng thái/quyền từ database, không nhận role hay customerId từ client. */
@Service
public class AccountServiceImpl implements AccountService {
  private final AccountRepository accountRepository;
  private final CustomerRepository customerRepository;

  public AccountServiceImpl(
      AccountRepository accountRepository, CustomerRepository customerRepository) {
    this.accountRepository = accountRepository;
    this.customerRepository = customerRepository;
  }

  @Override
  public Map<String, Object> account(String email) {
    var rows = accountRepository.findByEmail(EmailNormalizer.normalize(email));
    if (rows.isEmpty()) throw new UsernameNotFoundException("Tài khoản không khả dụng");
    return rows.get(0);
  }

  @Override
  public boolean active(Map<String, Object> row) {
    return "ACTIVE".equals(row.get("status"))
        && row.get("deleted_at") == null
        && Set.of("ADMIN", "CUSTOMER").contains(row.get("role"));
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    var a = account(email);
    return new AccountPrincipal(a, active(a));
  }

  @Override
  public long customerId(String email) {
    return customerRepository
        .findActiveIdByEmail(EmailNormalizer.normalize(email))
        .orElseThrow(() -> BusinessException.error(403, "Hồ sơ khách hàng không khả dụng"));
  }

  @Override
  public Map<String, Object> profile(String email) {
    var a = account(email);
    var result = new LinkedHashMap<String, Object>();
    result.put("id", a.get("id"));
    result.put("email", a.get("email"));
    result.put("role", a.get("role"));
    var customers = customerRepository.findActiveNameByAccount(((Number) a.get("id")).longValue());
    result.put(
        "name",
        customers.isEmpty()
            ? (a.get("display_name") == null ? "Quản trị viên" : a.get("display_name"))
            : customers.get(0).get("full_name"));
    return result;
  }

  @Override
  public long actorId(String email) {
    return ((Number) account(email).get("id")).longValue();
  }
}
