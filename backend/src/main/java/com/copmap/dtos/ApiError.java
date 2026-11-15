package com.copmap.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        OffsetDateTime timestamp,
        Map<String, String> validationErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, path, OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    public static ApiError of(int status, String error, String message, String path, boolean notDateTime) {
        return new ApiError(status, error, message, path, notDateTime ? null : OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    public static ApiError ofValidation(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ApiError(status, error, message, path, OffsetDateTime.now(ZoneOffset.UTC), validationErrors);
    }
}
