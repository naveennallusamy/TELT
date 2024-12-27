package com.telt.filter;

import com.telt.constants.RoleEnum;
import com.telt.entity.User;
import com.telt.repository.UserRepository;
import com.telt.util.JwtUtil;
import com.telt.util.TenantContext;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
     * @param request
     * @param response
     * @param filterChain
     * @throws jakarta.servlet.ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        if (requestPath.equals("/telt/api/auth/login") || requestPath.contains("swagger-ui") || requestPath.contains("api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = getTokenFromRequest(request);

        if (token != null) {
            String username = jwtUtil.extractUsername(token);
            User user = null;
            try {
                user = userRepository.findByEmail(username).orElseThrow(() -> new IllegalArgumentException("User Not Found"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Set user roles and tenant context
            if (RoleEnum.SUPER_ADMIN.name().equals(user.getRole().getName())) {
                TenantContext.setCurrentRole(RoleEnum.SUPER_ADMIN.name());
            } else {
                TenantContext.setCurrentRole(user.getRole().getName());
                TenantContext.setCurrentTenant(user.getTenant() != null ? user.getTenant().getTenantId() : null);
            }
        }

        filterChain.doFilter(request, response);
    }
    /*@Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        final String authorizationHeader = request.getHeader("Authorization");
            Long tenantId = Long.valueOf(request.getHeader("X-Tenant-ID"));
        String username = null;
        String jwt = null;

        TenantContext.setCurrentTenant(tenantId);

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            username = jwtUtil.extractUsername(jwt);
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            if (jwtUtil.isTokenValid(jwt, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }*/

    public String getTokenFromRequest(HttpServletRequest request) {
        final String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        throw new IllegalArgumentException("Missing or invalid Authorization header");
    }
}

