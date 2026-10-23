package com.telt.error;

import java.time.Instant;
import java.util.List;

/**
 * Uniform JSON error body returned by every endpoint.
 */
public record ApiError(String code, String message, String correlationId, Instant timestamp, List<FieldIssue> details) {

    public record FieldIssue(String field, String message) {
    }
}
