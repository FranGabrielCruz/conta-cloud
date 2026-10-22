package com.citacloud.springboot.contacloud.app.errors;

import org.springframework.http.HttpStatus;

public record ClassifiedError(
    ErrorCategory category,
    ErrorCode code,
    HttpStatus status,
    String message,
    boolean expected
) {}
