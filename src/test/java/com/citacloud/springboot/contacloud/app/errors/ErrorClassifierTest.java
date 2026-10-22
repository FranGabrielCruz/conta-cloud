package com.citacloud.springboot.contacloud.app.errors;

import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ErrorClassifierTest {
    @Test void clasificaReglasConCodigoEstable(){
        var error=ErrorClassifier.classify(new ReglaNegocioException(ErrorCode.INVENTORY_INSUFFICIENT,"Existencia insuficiente para el producto."));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,error.status());
        assertEquals(ErrorCode.INVENTORY_INSUFFICIENT,error.code());
        assertTrue(error.expected());
    }
    @Test void elCodigoNoDependeDelTextoHumano(){
        var first=ErrorClassifier.classify(new ReglaNegocioException(ErrorCode.INVENTORY_INSUFFICIENT,"Existencia insuficiente."));
        var second=ErrorClassifier.classify(new ReglaNegocioException(ErrorCode.INVENTORY_INSUFFICIENT,"No existe stock disponible."));
        assertEquals(first.code(),second.code());
    }
    @Test void ocultaDetalleDeErroresInesperados(){
        var error=ErrorClassifier.classify(new RuntimeException("password=secreto"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,error.status());
        assertEquals(ErrorCode.UNEXPECTED_ERROR,error.code());
        assertFalse(error.message().contains("secreto"));
    }
    @Test void identificaConflictoDeIdempotencia(){
        var error=ErrorClassifier.classify(new DataIntegrityViolationException("uk_payment_idempotency"));
        assertEquals(HttpStatus.CONFLICT,error.status());
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,error.code());
    }
    @Test void distingueDeadlockDeTimeoutDeBloqueo(){
        var deadlock=ErrorClassifier.classify(new RuntimeException("deadlock detected"));
        var timeout=ErrorClassifier.classify(new CannotAcquireLockException("lock timeout"));
        assertEquals(ErrorCode.DEADLOCK_CONFLICT,deadlock.code());
        assertEquals(ErrorCode.LOCK_TIMEOUT,timeout.code());
        assertNotEquals(deadlock.message(),timeout.message());
    }
}
