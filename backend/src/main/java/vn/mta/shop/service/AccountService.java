package vn.mta.shop.service;

import java.util.Map;

/** Hợp đồng nghiệp vụ; controller và service khác chỉ phụ thuộc interface này. */
public interface AccountService
    extends org.springframework.security.core.userdetails.UserDetailsService {
  Map<String, Object> account(String email);

  boolean active(Map<String, Object> row);

  long customerId(String email);

  Map<String, Object> profile(String email);

  long actorId(String email);
}
