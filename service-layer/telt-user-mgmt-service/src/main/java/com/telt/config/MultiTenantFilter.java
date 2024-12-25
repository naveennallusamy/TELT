package com.telt.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MultiTenantFilter implements Filter {

    private static final ThreadLocal<String> tenantContext = new ThreadLocal<>();

    public static String getTenant() {
        return tenantContext.get();
    }

    public static void setTenant(String tenant) {
        tenantContext.set(tenant);
    }

    public static void clear() {
        tenantContext.remove();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenant = httpRequest.getHeader("X-Tenant-ID");

        if (tenant == null) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant ID is missing");
            return;
        }

        setTenant(tenant);

        try {
            chain.doFilter(request, response);
        } finally {
            clear();
        }
    }
}

