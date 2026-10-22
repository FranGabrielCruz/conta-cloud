package com.citacloud.springboot.contacloud.app.errors;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String traceId,
    String path,
    List<FieldErrorResponse> errors
) {
    public ErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
