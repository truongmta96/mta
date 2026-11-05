package vn.mta.shop.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.mta.shop.dto.SandboxResultRequest;
import vn.mta.shop.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return payments.config();
  }

  @PostMapping("/sandbox/{id}/complete")
  public Map<String, Object> complete(
      @PathVariable long id, @Valid @RequestBody SandboxResultRequest input, Principal principal)
      throws Exception {
    return payments.completeSandbox(id, principal.getName(), input.result());
  }

  @PostMapping("/webhook")
  public Map<String, Object> webhook(
      @RequestBody String payload, @RequestHeader("X-Payment-Signature") String signature)
      throws Exception {
    return payments.webhook(payload, signature);
  }
}
