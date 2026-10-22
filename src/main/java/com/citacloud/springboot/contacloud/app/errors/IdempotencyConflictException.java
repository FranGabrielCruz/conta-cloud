package com.citacloud.springboot.contacloud.app.errors;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) { super(message); }
    public IdempotencyConflictException(String message, Throwable cause) { super(message, cause); }
}
