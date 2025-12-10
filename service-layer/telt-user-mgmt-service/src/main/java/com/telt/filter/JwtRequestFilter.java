package com.telt.filter;

import com.telt.constants.RoleEnum;
import com.telt.entity.user.User;
import com.telt.repository.UserRepository;
import com.telt.util.JwtUtil;
import com.telt.util.TenantContext;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    /**
     * Filters incoming requests to validate JWT tokens and set up security context.
     * <p>
     * This method extracts the JWT token from the request, validates it, and sets up
     * the Spring Security authentication context. It also sets the tenant context
     * based on the user's role and tenant information.
     *
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        if (requestPath.equals("/telt/api/auth/login") || requestPath.contains("swagger-ui") || requestPath.contains("api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = jwtUtil.getTokenFromRequest(request);

        if (token != null) {
            String username = jwtUtil.extractUsername(token);
            User user;
            try {
                user = userRepository.findByEmail(username).orElseThrow(() -> new IllegalArgumentException("User Not Found"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtUtil.isTokenValid(token, user.getEmail())) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    throw new RuntimeException("Invalid token");
                }
            }
            if (RoleEnum.SUPER_ADMIN.name().equals(user.getRole().getName())) {
                TenantContext.setCurrentRole(RoleEnum.SUPER_ADMIN.name());
            } else {
                TenantContext.setCurrentRole(user.getRole().getName());
                TenantContext.setCurrentTenant(user.getTenant() != null ? user.getTenant().getTenantId() : null);
            }
        } else {
            throw new RuntimeException("JWT token is missing");
        }

        filterChain.doFilter(request, response);
    }
}

