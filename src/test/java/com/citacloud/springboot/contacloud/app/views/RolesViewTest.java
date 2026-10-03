package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.PermisoDto;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RolesViewTest {
    @Test
    void agrupaLosPermisosDelMenuFinalEnSusSecciones() {
        assertThat(RolesView.seccionPermiso(permiso("productos.ver", "productos"))).isEqualTo("Inventario");
        assertThat(RolesView.seccionPermiso(permiso("catalogo_cuentas.ver", "catalogo_cuentas"))).isEqualTo("Contabilidad");
        assertThat(RolesView.seccionPermiso(permiso("fiscal_606.ver", "fiscal_606"))).isEqualTo("Fiscal");
        assertThat(RolesView.seccionPermiso(permiso("activos_fijos.ver", "activos_fijos"))).isEqualTo("Activos fijos");
        assertThat(RolesView.seccionPermiso(permiso("presupuestos.ver", "presupuestos"))).isEqualTo("Finanzas");
        assertThat(RolesView.seccionPermiso(permiso("reportes_financieros.ver", "reportes_financieros"))).isEqualTo("Reportes");
    }
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
    private static PermisoDto permiso(String codigo,String recurso){return permiso(codigo,recurso,"ver");}
}
