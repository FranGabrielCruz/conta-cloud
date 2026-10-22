package com.citacloud.springboot.contacloud.app.errors;

import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ErrorClassifierTest {
    @Test void clasificaReglasConCodigoEstable(){
        var error=ErrorClassifier.classify(new ReglaNegocioException("Existencia insuficiente para el producto."));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,error.status());
        assertEquals(ErrorCode.INVENTORY_INSUFFICIENT,error.code());
        assertTrue(error.expected());
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
}
