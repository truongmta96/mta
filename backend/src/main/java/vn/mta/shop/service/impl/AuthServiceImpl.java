package vn.mta.shop.service.impl;

import static vn.mta.shop.common.BusinessException.error;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mta.shop.common.EmailNormalizer;
import vn.mta.shop.dto.DeleteAccountRequest;
import vn.mta.shop.dto.LoginRequest;
import vn.mta.shop.dto.RegistrationRequest;
import vn.mta.shop.repository.AccountRepository;
import vn.mta.shop.repository.CustomerRepository;
import vn.mta.shop.security.AuthenticatedAccount;
import vn.mta.shop.service.AccountService;
import vn.mta.shop.service.AuthService;
import vn.mta.shop.service.ManagementService;

@Service
public class AuthServiceImpl implements AuthService {
  private final AccountRepository accountRepository;
  private final CustomerRepository customerRepository;
  private final AuthenticationManager manager;
  private final AccountService accounts;
  private final PasswordEncoder encoder;
  private final ManagementService management;

  public AuthServiceImpl(
      AccountRepository accountRepository,
      CustomerRepository customerRepository,
      AuthenticationManager manager,
      AccountService accounts,
      PasswordEncoder encoder,
      ManagementService management) {
    this.accountRepository = accountRepository;
    this.customerRepository = customerRepository;
    this.manager = manager;
    this.accounts = accounts;
    this.encoder = encoder;
    this.management = management;
  }

  @Override
  public AuthenticatedAccount authenticate(LoginRequest input) {
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw error(401, "Email hoặc mật khẩu không đúng, hoặc tài khoản không khả dụng");
    Authentication authentication;
    try {
      authentication =
          manager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  EmailNormalizer.normalize(input.email()), input.password()));
    } catch (AuthenticationException ex) {
      throw error(401, "Email hoặc mật khẩu không đúng, hoặc tài khoản không khả dụng");
    }
    if (input.portal() != null
        && authentication.getAuthorities().stream()
            .noneMatch(g -> g.getAuthority().equals("ROLE_" + input.portal())))
      throw error(403, "Tài khoản không có quyền đăng nhập khu vực này");
    return new AuthenticatedAccount(authentication, accounts.profile(authentication.getName()));
  }

  @Override
  public Map<String, Object> me(String email) {
    return accounts.profile(email);
  }

  @Override
  public Map<String, String> deleteAccount(DeleteAccountRequest input, String email) {
    management.deleteSelf(email, input.password());
    return Map.of("message", "Đã xóa mềm tài khoản. Lịch sử đơn được giữ lại.");
  }

  @Transactional
  @Override
  public Map<String, String> register(RegistrationRequest body) {
    // BCrypt giới hạn 72 bytes, không chỉ 72 ký tự Unicode.
    if (body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw error(400, "Mật khẩu tối đa 72 bytes UTF-8");
    String email = vn.mta.shop.common.EmailNormalizer.normalize(body.email());
    try {
      // Không có trường role trong input; đăng ký công khai luôn là CUSTOMER.
      accountRepository.insertRegistered(email, encoder.encode(body.password()));
      Long id = accountRepository.findIdByEmail(email);
      customerRepository.insert(id, body.name().strip());
    } catch (DuplicateKeyException ex) {
      throw error(409, "Email không thể sử dụng để đăng ký");
    }
    return Map.of("message", "Đăng ký thành công. Bạn có thể đăng nhập.");
  }
}
