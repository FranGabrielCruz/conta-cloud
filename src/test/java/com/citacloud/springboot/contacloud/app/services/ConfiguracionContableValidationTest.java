package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionContableInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracionContableValidationTest {
    @Test
    void aceptaDevengadoComoMetodoDisponible() {
        assertThat(ConfiguracionContableService.validar(
            new ConfiguracionContableInput("DEVENGADO", false, false)).name()).isEqualTo("DEVENGADO");
    }

    @Test
    void rechazaMetodoEfectivoMientrasNoEsteSoportado() {
        assertThatThrownBy(() -> ConfiguracionContableService.validar(
            new ConfiguracionContableInput("EFECTIVO", false, false)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El método efectivo todavía no está disponible.");
    }

    @Test
    void noPermiteHabilitarContabilizacionEnPeriodosCerrados() {
        assertThatThrownBy(() -> ConfiguracionContableService.validar(
            new ConfiguracionContableInput("DEVENGADO", false, true)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La contabilización en períodos cerrados no está habilitada.");
    }
}
