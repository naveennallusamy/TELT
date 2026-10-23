package com.telt.error;

import com.telt.config.CorrelationIdFilter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

public final class ApiErrorFactory {

    private ApiErrorFactory() {
    }

    public static ApiError of(ErrorCode code, String message) {
        return of(code, message, List.of());
    }

    public static ApiError of(ErrorCode code, String message, List<ApiError.FieldIssue> details) {
        return new ApiError(code.name(), message, MDC.get(CorrelationIdFilter.MDC_KEY), Instant.now(), details);
    }
}
