package vn.mta.shop.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.mta.shop.dto.AccountRequest;
import vn.mta.shop.dto.CampaignRequest;
import vn.mta.shop.dto.CustomerRequest;
import vn.mta.shop.dto.OrderStatusRequest;
import vn.mta.shop.dto.ProductRequest;
import vn.mta.shop.dto.RefundDecisionRequest;
import vn.mta.shop.dto.RefundRequest;
import vn.mta.shop.service.AdminService;

/** HTTP boundary: khai báo API và chuyển request xuống service interface. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final AdminService service;

  public AdminController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/products")
  public List<Map<String, Object>> products(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.products(page, q);
  }

  @PostMapping("/products")
  public Map<String, Long> addProduct(
      @Valid @RequestBody ProductRequest input, Principal principal) {
    return service.addProduct(input, principal.getName());
  }

  @PutMapping("/products/{id}")
  public Map<String, Long> editProduct(
      @PathVariable long id, @Valid @RequestBody ProductRequest input, Principal principal) {
    return service.editProduct(id, input, principal.getName());
  }

  @DeleteMapping("/products/{id}")
  public Map<String, String> deleteProduct(@PathVariable long id, Principal principal) {
    return service.deleteProduct(id, principal.getName());
  }

  @PostMapping("/products/{id}/restore")
  public Map<String, String> restoreProduct(@PathVariable long id, Principal principal) {
    return service.restoreProduct(id, principal.getName());
  }

  @GetMapping("/orders")
  public List<Map<String, Object>> listOrders(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.listOrders(page, q);
  }

  @GetMapping("/orders/{id}")
  public Map<String, Object> detail(@PathVariable long id) {
    return service.detail(id);
  }

  @PatchMapping("/orders/{id}")
  public Map<String, String> progress(
      @PathVariable long id, @Valid @RequestBody OrderStatusRequest input) {
    return service.progress(id, input);
  }

  @PostMapping("/orders/{id}/cancel")
  public Map<String, String> cancel(@PathVariable long id) {
    return service.cancel(id);
  }

  @PostMapping("/orders/{id}/refunds")
  public Map<String, Object> refund(
      @PathVariable long id, @Valid @RequestBody RefundRequest input) {
    return service.refund(id, input);
  }

  @GetMapping("/accounts")
  public List<Map<String, Object>> accountList(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.accountList(page, q);
  }

  @PostMapping("/accounts")
  public Map<String, Long> addAccount(
      @Valid @RequestBody AccountRequest input, Principal principal) {
    return service.addAccount(input, principal.getName());
  }

  @PutMapping("/accounts/{id}")
  public Map<String, Long> editAccount(
      @PathVariable long id, @Valid @RequestBody AccountRequest input, Principal principal) {
    return service.editAccount(id, input, principal.getName());
  }

  @DeleteMapping("/accounts/{id}")
  public Map<String, String> deleteAccount(@PathVariable long id, Principal principal) {
    return service.deleteAccount(id, principal.getName());
  }

  @PostMapping("/accounts/{id}/restore")
  public Map<String, String> restoreAccount(@PathVariable long id, Principal principal) {
    return service.restoreAccount(id, principal.getName());
  }

  @GetMapping("/customers")
  public List<Map<String, Object>> customers(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.customers(page, q);
  }

  @PutMapping("/customers/{id}")
  public Map<String, String> customer(
      @PathVariable long id, @Valid @RequestBody CustomerRequest input, Principal principal) {
    return service.customer(id, input, principal.getName());
  }

  @DeleteMapping("/customers/{id}")
  public Map<String, String> deleteCustomer(@PathVariable long id, Principal principal) {
    return service.deleteCustomer(id, principal.getName());
  }

  @PostMapping("/customers/{id}/restore")
  public Map<String, String> restoreCustomer(@PathVariable long id, Principal principal) {
    return service.restoreCustomer(id, principal.getName());
  }

  @GetMapping("/campaigns")
  public List<Map<String, Object>> campaigns(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.campaigns(page, q);
  }

  @PostMapping("/campaigns")
  public Map<String, Long> addSale(@Valid @RequestBody CampaignRequest input, Principal principal) {
    return service.addSale(input, principal.getName());
  }

  @PutMapping("/campaigns/{id}")
  public Map<String, Long> editSale(
      @PathVariable long id, @Valid @RequestBody CampaignRequest input, Principal principal) {
    return service.editSale(id, input, principal.getName());
  }

  @PatchMapping("/campaigns/{id}")
  public Map<String, String> stopSale(
      @PathVariable long id, @Valid @RequestBody OrderStatusRequest input, Principal principal) {
    return service.stopSale(id, input, principal.getName());
  }

  @DeleteMapping("/campaigns/{id}")
  public Map<String, String> deleteSale(@PathVariable long id, Principal principal) {
    return service.deleteSale(id, principal.getName());
  }

  @GetMapping("/refunds")
  public List<Map<String, Object>> refunds(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.refunds(page, q);
  }

  @PostMapping("/refunds/{id}/decision")
  public Map<String, String> decide(
      @PathVariable long id, @RequestBody RefundDecisionRequest input) {
    return service.decide(id, input);
  }

  @GetMapping("/audit")
  public List<Map<String, Object>> audit(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
    return service.audit(page, q);
  }
}
