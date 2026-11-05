package vn.mta.shop.security;

import java.util.Map;
import org.springframework.security.core.Authentication;

public record AuthenticatedAccount(Authentication authentication, Map<String, Object> profile) {}
