package vn.mta.shop.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.mta.shop.dto.DeleteAccountRequest;
import vn.mta.shop.dto.LoginRequest;
import vn.mta.shop.dto.RegistrationRequest;
import vn.mta.shop.security.SessionManager;
import vn.mta.shop.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;
  private final SessionManager sessions;

  public AuthController(AuthService service, SessionManager sessions) {
    this.service = service;
    this.sessions = sessions;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public Map<String, Object> me(Principal principal) {
    return service.me(principal.getName());
  }

  @PostMapping("/login")
  public Map<String, Object> login(
      @Valid @RequestBody LoginRequest input,
      HttpServletRequest request,
      HttpServletResponse response) {
    var account = service.authenticate(input);
    sessions.start(account.authentication(), request, response);
    return account.profile();
  }

  @PostMapping("/register")
  public Map<String, String> register(@Valid @RequestBody RegistrationRequest input) {
    return service.register(input);
  }

  @DeleteMapping("/account")
  public Map<String, String> deleteAccount(
      @Valid @RequestBody DeleteAccountRequest input,
      Principal principal,
      HttpServletRequest request) {
    var result = service.deleteAccount(input, principal.getName());
    sessions.end(request);
    return result;
  }
}
