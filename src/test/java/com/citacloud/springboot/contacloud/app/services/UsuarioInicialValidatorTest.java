package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.UsuarioInicialDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsuarioInicialValidatorTest {

    @Test
    void normalizaLosDatosDelAdministradorInicial() {
        UsuarioInicialDto resultado = UsuarioInicialValidator.validar(
            new UsuarioInicialDto(" ADMIN.NUEVO ", " Ana ", " Pérez ",
                " ANA@EMPRESA.COM ", "(809) 555-1234"),
            "ClaveSegura2026", "ClaveSegura2026");

        assertEquals("admin.nuevo", resultado.usuario());
        assertEquals("Ana", resultado.nombre());
        assertEquals("Pérez", resultado.apellido());
        assertEquals("ana@empresa.com", resultado.correo());
        assertEquals("8095551234", resultado.telefono());
    }

    @Test
    void rechazaContrasenasDebilesODiferentes() {
        UsuarioInicialDto usuario = new UsuarioInicialDto(
            "admin", "Ana", "Pérez", null, null);

        assertThrows(ReglaNegocioException.class,
            () -> UsuarioInicialValidator.validar(usuario, "corta", "corta"));
        assertThrows(ReglaNegocioException.class,
            () -> UsuarioInicialValidator.validar(usuario, "ClaveSegura2026", "OtraClave2026"));
    }

    @Test
    void rechazaUsuarioConCaracteresNoPermitidos() {
        UsuarioInicialDto usuario = new UsuarioInicialDto(
            "admin empresa", "Ana", "Pérez", null, null);

        assertThrows(ReglaNegocioException.class,
            () -> UsuarioInicialValidator.validar(usuario, "ClaveSegura2026", "ClaveSegura2026"));
    }
}
