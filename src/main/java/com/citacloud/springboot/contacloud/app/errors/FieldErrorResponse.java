package com.citacloud.springboot.contacloud.app.errors;

public record FieldErrorResponse(String field, String code, String message) {}
