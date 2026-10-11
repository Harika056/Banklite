package com.banklite.dto;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record ApiError(Instant timestamp, int status, String error, String message, List<String> details) {

    public static ApiError of(HttpStatusCode status, String message, List<String> details) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        return new ApiError(Instant.now(), status.value(), reason, message, details);
    }
}
