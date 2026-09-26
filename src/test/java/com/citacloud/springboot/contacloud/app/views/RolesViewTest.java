package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.PermisoDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RolesViewTest {
    @Test
    void noAgrupaPermisosDeRecursosDistintosDentroDeConfiguracion() {
        var empresa = permiso("EMPRESA_VER", "empresa", "ver");
        var sucursal = permiso("SUCURSAL_VER", "sucursal", "ver");
        var moneda = permiso("MONEDA_VER", "moneda", "ver");

        assertThat(RolesView.claveEquivalencia(empresa))
            .isNotEqualTo(RolesView.claveEquivalencia(sucursal))
            .isNotEqualTo(RolesView.claveEquivalencia(moneda));
        assertThat(RolesView.claveEquivalencia(sucursal)).contains("Sucursales");
    }

    private static PermisoDto permiso(String codigo, String recurso, String accion) {
        return new PermisoDto(UUID.randomUUID(), codigo, codigo, null, "CORE", recurso, accion);
    }
}
