package com.payshield.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String correlationId,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String errorCode, String message, String correlationId, String path) {
        return new ApiError(Instant.now(), status, errorCode, message, correlationId, path, Map.of());
    }
}
