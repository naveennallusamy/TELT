package com.telt.security;

import com.telt.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Users whose token carries {@code pcr=true} (e.g. admin-created accounts on first login) may only change their
 * password, view their profile or log out.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String CLAIM = "pcr";
    private static final Set<String> ALLOWED = Set.of(
            "POST /auth/change-password", "POST /auth/logout", "POST /auth/logout-all", "GET /me", "GET /me/authorization");

    private final ApiErrorResponseWriter errorWriter;

    public PasswordChangeRequiredFilter(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(CLAIM))) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!ALLOWED.contains(request.getMethod() + " " + path)) {
                errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                        "Password change required before continuing");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
