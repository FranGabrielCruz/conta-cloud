package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.PermisoDto;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RolesViewTest {
    @Test void noAgrupaPermisosDeRecursosDistintosAunqueTenganLaMismaAccion(){
        PermisoDto cajas=permiso("cajas.ver","cajas","ver");
        PermisoDto cuentas=permiso("cuentas_bancarias.ver","cuentas_bancarias","ver");
        PermisoDto ingresos=permiso("ingresos.ver","ingresos","ver");

        assertThat(RolesView.claveEquivalencia(cajas))
            .isNotEqualTo(RolesView.claveEquivalencia(cuentas))
            .isNotEqualTo(RolesView.claveEquivalencia(ingresos));
    }

    @Test void agrupaSoloAliasLegacyYActualDelMismoPermiso(){
        PermisoDto legacy=permiso("ROL_VER",null,null);
        PermisoDto actual=permiso("roles.ver","roles","ver");
        assertThat(RolesView.claveEquivalencia(legacy)).isEqualTo(RolesView.claveEquivalencia(actual));
    }

    @Test void mantieneSeparadasAccionesDelMismoRecurso(){
        PermisoDto ver=permiso("operaciones_caja.ver","operaciones_caja","ver");
        PermisoDto abrir=permiso("operaciones_caja.abrir","operaciones_caja","abrir");
        assertThat(RolesView.claveEquivalencia(ver)).isNotEqualTo(RolesView.claveEquivalencia(abrir));
    }

    private static PermisoDto permiso(String codigo,String recurso,String accion){
        return new PermisoDto(UUID.randomUUID(),codigo,codigo,"", "CAJA_BANCOS",recurso,accion);
    }
}
