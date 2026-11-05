package vn.mta.shop;
import java.security.Principal;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import vn.mta.shop.security.AccountService;
@RestController @RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService payments;private final AccountService accounts;
    public PaymentController(PaymentService payments,AccountService accounts){this.payments=payments;this.accounts=accounts;}
    @GetMapping("/config") public Map<String,Object> config(){return payments.config();}
    public record Result(@NotBlank String result){}
    @PostMapping("/sandbox/{id}/complete") public Map<String,Object> complete(@PathVariable long id,@Valid @RequestBody Result result,Principal principal) throws Exception{var signed=payments.signedSandbox(id,accounts.customerId(principal.getName()),result.result());return payments.webhook(signed.get("payload"),signed.get("signature"));}
    // Ngoại lệ CSRF duy nhất: xác thực bằng HMAC + timestamp, không dựa vào cookie trình duyệt.
    @PostMapping("/webhook") public Map<String,Object> webhook(@RequestBody String payload,@RequestHeader("X-Payment-Signature") String signature) throws Exception{return payments.webhook(payload,signature);}
}
