package vn.mta.shop.service;

import java.util.Map;
import vn.mta.shop.dto.DeleteAccountRequest;
import vn.mta.shop.dto.LoginRequest;
import vn.mta.shop.dto.RegistrationRequest;
import vn.mta.shop.security.AuthenticatedAccount;

public interface AuthService {
  AuthenticatedAccount authenticate(LoginRequest input);

  Map<String, Object> me(String email);

  Map<String, String> register(RegistrationRequest input);

  Map<String, String> deleteAccount(DeleteAccountRequest input, String email);
}
