package com.citacloud.springboot.contacloud.app.errors;

import com.citacloud.springboot.contacloud.app.services.RecursoNoEncontradoException;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

public final class ErrorClassifier {
    private static final String UNEXPECTED = "Ocurrió un error inesperado. Intente nuevamente.";

    private ErrorClassifier() {}

    public static ClassifiedError classify(Throwable error) {
        Throwable root = root(error);
        if (error instanceof IdempotencyConflictException) {
            return expected(ErrorCategory.IDEMPOTENCY_CONFLICT, ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                HttpStatus.CONFLICT, safe(error.getMessage(), "La clave de idempotencia ya fue utilizada por otra operación."));
        }
        if (error instanceof RecursoNoEncontradoException) {
            return expected(ErrorCategory.NOT_FOUND, ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                safe(error.getMessage(), "No se encontró el recurso solicitado."));
        }
        if (error instanceof AuthenticationException) {
            return expected(ErrorCategory.AUTHENTICATION_ERROR, ErrorCode.AUTHENTICATION_ERROR,
                HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.");
        }
        if (error instanceof AccessDeniedException) {
            return expected(ErrorCategory.AUTHORIZATION_ERROR, ErrorCode.FORBIDDEN_OPERATION,
                HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta operación.");
        }
        if (isConcurrency(error, root)) {
            return expected(ErrorCategory.CONCURRENCY_ERROR, ErrorCode.CONCURRENCY_CONFLICT,
                HttpStatus.CONFLICT, "La información cambió mientras realizabas la operación. Actualiza e inténtalo nuevamente.");
        }
        if (error instanceof ReglaNegocioException) {
            String message = safe(error.getMessage(), "No fue posible completar la operación.");
            return expected(ErrorCategory.BUSINESS_RULE_ERROR, businessCode(message),
                HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
        if (error instanceof DataIntegrityViolationException) {
            if (containsIdempotency(error)) {
                return expected(ErrorCategory.IDEMPOTENCY_CONFLICT, ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    HttpStatus.CONFLICT, "La operación ya fue procesada o se encuentra en proceso.");
            }
            return expected(ErrorCategory.CONFLICT, ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "No fue posible guardar porque la información entra en conflicto con un registro existente.");
        }
        if (error instanceof IllegalArgumentException) {
            return expected(ErrorCategory.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST, "Revisa los datos ingresados.");
        }
        if (error instanceof UncheckedIOException) {
            return unexpected(ErrorCategory.INFRASTRUCTURE_ERROR, ErrorCode.INFRASTRUCTURE_ERROR);
        }
        return unexpected(ErrorCategory.UNEXPECTED_ERROR, ErrorCode.UNEXPECTED_ERROR);
    }

    private static boolean isConcurrency(Throwable error, Throwable root) {
        return error instanceof ObjectOptimisticLockingFailureException
            || error instanceof CannotAcquireLockException
            || error instanceof ConcurrencyFailureException
            || root instanceof java.sql.SQLTransactionRollbackException
            || contains(error, "deadlock") || contains(error, "lock timeout") || contains(error, "could not obtain lock");
    }

    private static ErrorCode businessCode(String message) {
        String value = message.toLowerCase(Locale.ROOT);
        if (value.contains("existencia insuficiente") || value.contains("inventario suficiente")) return ErrorCode.INVENTORY_INSUFFICIENT;
        if (value.contains("caja") && value.contains("cerrad")) return ErrorCode.CASH_REGISTER_CLOSED;
        if (value.contains("factura") && value.contains("pagad")) return ErrorCode.INVOICE_ALREADY_PAID;
        if (value.contains("ya fue confirm") || value.contains("ya está confirm")) return ErrorCode.DOCUMENT_ALREADY_CONFIRMED;
        if (value.contains("descuento") && value.contains("super")) return ErrorCode.DISCOUNT_LIMIT_EXCEEDED;
        if (value.contains("recargo") && value.contains("super")) return ErrorCode.SURCHARGE_LIMIT_EXCEEDED;
        if (value.contains("modificad") && value.contains("otro usuario")) return ErrorCode.CONCURRENCY_CONFLICT;
        return ErrorCode.BUSINESS_RULE_ERROR;
    }

    private static ClassifiedError expected(ErrorCategory category, ErrorCode code, HttpStatus status, String message) {
        return new ClassifiedError(category, code, status, message, true);
    }

    private static ClassifiedError unexpected(ErrorCategory category, ErrorCode code) {
        return new ClassifiedError(category, code, HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED, false);
    }

    private static boolean containsIdempotency(Throwable error) { return contains(error, "idempot"); }
    private static boolean contains(Throwable error, String value) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().toLowerCase(Locale.ROOT).contains(value)) return true;
        }
        return false;
    }
    private static Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
    private static String safe(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
