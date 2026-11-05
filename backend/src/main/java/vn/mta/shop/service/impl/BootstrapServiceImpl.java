package vn.mta.shop.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.repository.AccountRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.service.BootstrapService;

@Service
public class BootstrapServiceImpl implements BootstrapService {
  private final AccountRepository accountRepository;
  private final CustomerRepository customerRepository;
  private final org.springframework.security.crypto.password.PasswordEncoder encoder;

  public BootstrapServiceImpl(
      AccountRepository accountRepository,
      CustomerRepository customerRepository,
      org.springframework.security.crypto.password.PasswordEncoder encoder) {
    this.accountRepository = accountRepository;
    this.customerRepository = customerRepository;
    this.encoder = encoder;
  }

  @Transactional
  @Override
  public void ensureDemoAccounts(String adminPassword, String userPassword) {
    create("admin@mta.local", adminPassword, "ADMIN", "Quản trị viên");
    create("user@mta.local", userPassword, "CUSTOMER", "Nguyễn Minh Anh");
  }

  private void create(String email, String password, String role, String name) {
    if (accountRepository.countByEmail(email) > 0) return;
    if (password.length() < 10
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "Mật khẩu tài khoản demo cần từ 10 ký tự, tối đa 72 bytes UTF-8");
    accountRepository.insertDemo(email, encoder.encode(password), role);
    Long id = accountRepository.findIdByEmail(email);
    if (role.equals("CUSTOMER")) customerRepository.insert(id, name);
  }
}
