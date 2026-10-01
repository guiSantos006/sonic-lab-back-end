package com.soniclab.soniclabbackend.user.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> details
) {
    public ApiErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
