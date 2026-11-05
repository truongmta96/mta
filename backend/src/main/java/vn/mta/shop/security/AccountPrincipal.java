package vn.mta.shop.security;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.userdetails.User;

/** Principal bất biến dùng kiểm tra phiên, không phải service hay dữ liệu trả về API. */
public class AccountPrincipal extends User {
  private final int version;

  public AccountPrincipal(Map<String, Object> a, boolean enabled) {
    super(
        a.get("email").toString(),
        a.get("password_hash").toString(),
        enabled,
        true,
        true,
        true,
        List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_" + a.get("role"))));
    version = ((Number) a.get("session_version")).intValue();
  }

  public int version() {
    return version;
  }
}
