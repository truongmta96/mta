package vn.mta.shop.security;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager; private final SecurityContextRepository contexts;
    private final AccountService accounts; private final JdbcTemplate db; private final PasswordEncoder encoder; private final CsrfTokenRepository csrfTokens;
    public AuthController(AuthenticationManager manager,SecurityContextRepository contexts,AccountService accounts,JdbcTemplate db,PasswordEncoder encoder,CsrfTokenRepository csrfTokens) {
        this.manager=manager;this.contexts=contexts;this.accounts=accounts;this.db=db;this.encoder=encoder;this.csrfTokens=csrfTokens;
    }
    public record Credentials(@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(max=72) String password, @Pattern(regexp="ADMIN|CUSTOMER") String portal) {}
    public record Registration(@NotBlank @Email @Size(max=190) String email,
        @NotBlank @Size(min=10,max=72) String password,@NotBlank @Size(max=150) String name) {}

    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @GetMapping("/me") public Map<String,Object> me(Authentication authentication) {return accounts.profile(authentication.getName());}
    @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody Credentials body,HttpServletRequest request,HttpServletResponse response) {
        if(body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email hoặc mật khẩu không đúng, hoặc tài khoản không khả dụng");
        Authentication authenticated;
        try { authenticated=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(AccountService.email(body.email()),body.password())); }
        catch (AuthenticationException ex) {throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email hoặc mật khẩu không đúng, hoặc tài khoản không khả dụng");}
        if(body.portal()!=null && authenticated.getAuthorities().stream().noneMatch(g -> g.getAuthority().equals("ROLE_"+body.portal())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản không có quyền đăng nhập khu vực này");
        // Tạo/đổi session ID sau đăng nhập để tránh session fixation.
        if(request.getSession(false)!=null)request.changeSessionId(); else request.getSession(true);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
        // Đổi CSRF token sau khi đổi danh tính; client lấy token mới trước lần POST kế tiếp.
        csrfTokens.saveToken(null,request,response);
        return accounts.profile(authenticated.getName());
    }
    @PostMapping("/register") @Transactional public Map<String,String> register(@Valid @RequestBody Registration body) {
        // BCrypt giới hạn 72 bytes, không chỉ 72 ký tự Unicode.
        if(body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mật khẩu tối đa 72 bytes UTF-8");
        String email=AccountService.email(body.email());
        try {
            // Không có trường role trong input; đăng ký công khai luôn là CUSTOMER.
            db.update("INSERT INTO accounts(email,password_hash,role,status) VALUES(?,?,'CUSTOMER','ACTIVE')",email,encoder.encode(body.password()));
            Long id=db.queryForObject("SELECT id FROM accounts WHERE email=?",Long.class,email);
            db.update("INSERT INTO customers(account_id,full_name) VALUES(?,?)",id,body.name().strip());
        } catch (DuplicateKeyException ex) {throw new ResponseStatusException(HttpStatus.CONFLICT,"Email không thể sử dụng để đăng ký");}
        return Map.of("message","Đăng ký thành công. Bạn có thể đăng nhập.");
    }
}
