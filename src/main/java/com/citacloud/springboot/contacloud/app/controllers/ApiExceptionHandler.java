package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.services.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<?> noEncontrado(RecursoNoEncontradoException ex) { return respuesta(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validacion(MethodArgumentNotValidException ex) { return respuesta(HttpStatus.BAD_REQUEST, "Revise los datos enviados"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> integridad(DataIntegrityViolationException ex) {
        log.warn("Conflicto de integridad", ex); return respuesta(HttpStatus.CONFLICT, "No fue posible guardar: existe un valor duplicado o relacionado");
    }
    private ResponseEntity<?> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of("fecha", Instant.now(), "estado", status.value(), "mensaje", mensaje));
    }
}
