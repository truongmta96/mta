package vn.mta.shop.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.mta.shop.dto.PlaceOrderRequest;
import vn.mta.shop.dto.ProfileRequest;
import vn.mta.shop.dto.RefundRequest;
import vn.mta.shop.service.ShopService;

/** HTTP boundary: khai báo API và chuyển request xuống service interface. */
@RestController
@RequestMapping("/api")
public class ShopController {
  private final ShopService service;

  public ShopController(ShopService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return service.health();
  }

  @GetMapping("/products")
  public List<Map<String, Object>> products() {
    return service.products();
  }

  @GetMapping("/campaigns")
  public List<Map<String, Object>> campaigns() {
    return service.campaigns();
  }

  @GetMapping("/orders")
  public List<Map<String, Object>> orders(Principal principal) {
    return service.list(principal.getName());
  }

  @GetMapping("/orders/{id}")
  public Map<String, Object> detail(@PathVariable long id, Principal principal) {
    return service.detail(id, principal.getName());
  }

  @PostMapping("/orders")
  public Map<String, Object> place(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PlaceOrderRequest input,
      Principal principal)
      throws Exception {
    return service.place(key, input, principal.getName());
  }

  @PostMapping("/orders/{id}/cancel")
  public Map<String, String> cancel(@PathVariable long id, Principal principal) {
    return service.cancel(id, principal.getName());
  }

  @PostMapping("/orders/{id}/payments")
  public Map<String, Object> pay(@PathVariable long id, Principal principal) {
    return service.pay(id, principal.getName());
  }

  @PostMapping("/orders/{id}/refunds")
  public Map<String, Object> refund(
      @PathVariable long id, @Valid @RequestBody RefundRequest input, Principal principal) {
    return service.refund(id, input, principal.getName());
  }

  @GetMapping("/customer")
  public Map<String, Object> customer(Principal principal) {
    return service.customer(principal.getName());
  }

  @PutMapping("/customer")
  public Map<String, String> profile(
      @Valid @RequestBody ProfileRequest input, Principal principal) {
    return service.profile(input, principal.getName());
  }
}
