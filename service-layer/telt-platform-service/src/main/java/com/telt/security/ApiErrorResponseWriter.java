package com.telt.security;

import com.telt.config.CorrelationIdFilter;
import com.telt.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes {@link com.telt.error.ApiError}-shaped JSON from the security filter chain, where MVC exception
 * handlers are not reached. Messages are fixed strings, so only minimal escaping is required.
 */
@Component
public class ApiErrorResponseWriter {

    public void commenceAuthentication(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (ex instanceof InvalidBearerTokenException) {
            write(response, ErrorCode.INVALID_TOKEN, "Access token is invalid or expired");
        } else {
            write(response, ErrorCode.UNAUTHORIZED, "Authentication required");
        }
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + escape(message)
                + "\",\"correlationId\":" + (correlationId == null ? "null" : "\"" + escape(correlationId) + "\"")
                + ",\"timestamp\":\"" + Instant.now() + "\",\"details\":[]}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
