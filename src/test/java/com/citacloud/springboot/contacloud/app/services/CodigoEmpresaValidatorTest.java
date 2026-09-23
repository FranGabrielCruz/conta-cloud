package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodigoEmpresaValidatorTest {

    @Test
    void normalizaElCodigoParaElLogin() {
        assertEquals("EMPRESA01", CodigoEmpresaValidator.validarYNormalizar(" empresa01 "));
        assertEquals("EMPRESA-01", CodigoEmpresaValidator.validarYNormalizar("Empresa-01"));
    }

    @Test
    void rechazaCodigosInvalidos() {
        assertThrows(ReglaNegocioException.class, () -> CodigoEmpresaValidator.validarYNormalizar(null));
        assertThrows(ReglaNegocioException.class, () -> CodigoEmpresaValidator.validarYNormalizar("AB"));
        assertThrows(ReglaNegocioException.class, () -> CodigoEmpresaValidator.validarYNormalizar("EMPRESA 01"));
        assertThrows(ReglaNegocioException.class,
            () -> CodigoEmpresaValidator.validarYNormalizar("EMPRESA-CODIGO-DE-ACCESO-DEMASIADO-LARGO"));
    }
}
