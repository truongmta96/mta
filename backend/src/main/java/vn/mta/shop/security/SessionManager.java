package vn.mta.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Adapter HTTP/session của Spring Security; không truy cập database. */
@Component
public class SessionManager {
  private final SecurityContextRepository contexts;
  private final CsrfTokenRepository csrfTokens;

  public SessionManager(SecurityContextRepository contexts, CsrfTokenRepository csrfTokens) {
    this.contexts = contexts;
    this.csrfTokens = csrfTokens;
  }

  public void start(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) request.changeSessionId();
    else request.getSession(true);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    csrfTokens.saveToken(null, request, response);
  }

  public void end(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
  }
}
