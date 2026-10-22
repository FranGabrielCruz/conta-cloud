package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.errors.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.CriticalFailureAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final CriticalFailureAuditService failureAudit;

    public ApiExceptionHandler(CriticalFailureAuditService failureAudit){this.failureAudit=failureAudit;}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorResponse(error.getField(), "INVALID", safe(error.getDefaultMessage())))
            .toList();
        return response(new ClassifiedError(ErrorCategory.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR,
            org.springframework.http.HttpStatus.BAD_REQUEST, "Revisa los datos enviados.", true), request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> invalidParameters(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getConstraintViolations().stream()
            .map(error -> new FieldErrorResponse(last(error.getPropertyPath().toString()), "INVALID", safe(error.getMessage())))
            .toList();
        return response(new ClassifiedError(ErrorCategory.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR,
            org.springframework.http.HttpStatus.BAD_REQUEST, "Revisa los datos enviados.", true), request, fields);
    }

    @ExceptionHandler(Throwable.class)
    ResponseEntity<ErrorResponse> handle(Throwable exception, HttpServletRequest request) {
        ClassifiedError classified = ErrorClassifier.classify(exception);
        String traceId = TraceContext.currentOrCreate();
        if (classified.expected()) {
            log.warn("event=REQUEST_REJECTED traceId={} tenantId={} companyId={} userId={} endpoint={} code={} result=REJECTED exceptionType={}",
                traceId, tenant(), company(), user(), request.getRequestURI(), classified.code(), exception.getClass().getName());
        } else {
            log.error("event=REQUEST_FAILED traceId={} tenantId={} companyId={} userId={} endpoint={} code={} result=ERROR exceptionType={}",
                traceId, tenant(), company(), user(), request.getRequestURI(), classified.code(), exception.getClass().getName(), exception);
        }
        try{failureAudit.record(classified,request.getRequestURI(),traceId);}
        catch(RuntimeException auditError){log.error("event=CRITICAL_FAILURE_AUDIT_FAILED traceId={} endpoint={}",traceId,request.getRequestURI(),auditError);}
        return response(classified, request, List.of());
    }

    private ResponseEntity<ErrorResponse> response(ClassifiedError classified, HttpServletRequest request,
                                                    List<FieldErrorResponse> fields) {
        String traceId = TraceContext.currentOrCreate();
        ErrorResponse body = new ErrorResponse(Instant.now(), classified.status().value(), classified.code().name(),
            classified.message(), traceId, request.getRequestURI(), fields);
        return ResponseEntity.status(classified.status()).header(TraceContext.HEADER, traceId).body(body);
    }

    private static String safe(String message) { return message == null || message.isBlank() ? "Valor inválido." : message; }
    private static String last(String path) { int index = path.lastIndexOf('.'); return index < 0 ? path : path.substring(index + 1); }
    private static String tenant() { try { return TenantContext.principalActual().tenantId().toString(); } catch (RuntimeException ignored) { return "anonymous"; } }
    private static String company() { try { return TenantContext.principalActual().empresaId().toString(); } catch (RuntimeException ignored) { return "anonymous"; } }
    private static String user() { try { return TenantContext.principalActual().usuarioId().toString(); } catch (RuntimeException ignored) { return "anonymous"; } }
}
