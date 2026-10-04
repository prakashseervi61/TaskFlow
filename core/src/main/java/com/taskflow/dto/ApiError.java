package com.taskflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Standard RFC 7807-ish error payload returned by the global exception handler. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, Map.of());
    }

    public static ApiError of(
            int status, String error, String message, String path, List<String> details) {
        return new ApiError(
                Instant.now(),
                status,
                error,
                message,
                path,
                details == null || details.isEmpty() ? Map.of() : Map.of("errors", String.join("; ", details)));
    }
}