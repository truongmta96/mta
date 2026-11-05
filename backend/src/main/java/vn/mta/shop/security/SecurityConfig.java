package vn.mta.shop.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationManager authenticationManager(AccountService accounts, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean SecurityFilterChain security(HttpSecurity http, AccountService accounts, SecurityContextRepository repository, CsrfTokenRepository csrfTokens) throws Exception {
        // CSRF mặc định dùng token trong session; React lấy token qua /api/auth/csrf.
        // Chỉ permit đúng method/endpoint cần thiết. Các đường dẫn còn lại bị từ chối.
        http.csrf(c -> c.csrfTokenRepository(csrfTokens).ignoringRequestMatchers("/api/payments/webhook")).authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET,"/api/health","/api/products","/api/campaigns","/api/auth/csrf","/api/payments/config").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/auth/register","/api/payments/webhook").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/orders","/api/orders/**","/api/customer","/api/auth/account","/api/payments/sandbox/**").hasRole("CUSTOMER")
            .requestMatchers("/api/auth/me").authenticated()
            .requestMatchers("/error").permitAll()
            .anyRequest().denyAll())
            .securityContext(c -> c.securityContextRepository(repository))
            .requestCache(c -> c.requestCache(new NullRequestCache()))
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> json(res,401,"Vui lòng đăng nhập"))
                .accessDeniedHandler((req,res,ex) -> json(res,403,"Không đủ quyền hoặc mã CSRF không hợp lệ")))
            .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> json(res,200,"Đã đăng xuất")));

        // Kiểm tra lại mỗi request: khóa/xóa tài khoản hoặc đổi role phải vô hiệu hóa phiên cũ.
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    boolean valid;
                    try {
                        var a = accounts.account(auth.getName());
                        valid = accounts.active(a) && auth.getPrincipal() instanceof AccountService.AccountUser user
                            && user.version()==((Number)a.get("session_version")).intValue() && auth.getAuthorities().stream()
                            .anyMatch(g -> g.getAuthority().equals("ROLE_"+a.get("role")));
                    } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) { valid=false; }
                    if (!valid) {
                        SecurityContextHolder.clearContext();
                        var session=req.getSession(false); if(session!=null)session.invalidate();
                        json(res,401,"Phiên đăng nhập không còn hiệu lực"); return;
                    }
                }
                chain.doFilter(req,res);
            }
        }, AnonymousAuthenticationFilter.class);
        return http.build();
    }
    static void json(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\""+message+"\"}");
    }
}
