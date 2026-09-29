package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermisoModuloPolicyTest {
    @Test
    void relacionaPermisosLegadosConSuModulo() {
        assertThat(PermisoModuloPolicy.moduloRequerido("TASA_CAMBIO_CREAR", null))
            .contains("TASAS_CAMBIO");
        assertThat(PermisoModuloPolicy.moduloRequerido("CONFIGURACION_CONTABLE_EDITAR", null))
            .contains("CONFIGURACION_CONTABLE");
    }

    @Test
    void relacionaPermisosConRecursoExplicito() {
        assertThat(PermisoModuloPolicy.moduloRequerido("usuarios.crear", "usuarios"))
            .contains("USUARIOS");
    }

    @Test
    void agrupaEmpresaSucursalesYMonedasEnConfiguracion() {
        assertThat(PermisoModuloPolicy.moduloRequerido("EMPRESA_VER", null)).contains("CONFIGURACION");
        assertThat(PermisoModuloPolicy.moduloRequerido("SUCURSAL_VER", null)).contains("CONFIGURACION");
        assertThat(PermisoModuloPolicy.moduloRequerido("MONEDA_VER", null)).contains("CONFIGURACION");
    }

    @Test
    void noPublicaRecursosSinModuloConfigurable() {
        assertThat(PermisoModuloPolicy.moduloRequerido("bases_datos.ver", "bases_datos"))
            .isEmpty();
    }

    @Test
    void relacionaLosRecursosDeFaseDosConSusEntitlements() {
        assertThat(PermisoModuloPolicy.moduloRequerido("clientes.ver", "clientes")).contains("VENTAS");
        assertThat(PermisoModuloPolicy.moduloRequerido("ordenes_compra.ver", "ordenes_compra"))
            .contains("COMPRAS");
        assertThat(PermisoModuloPolicy.moduloRequerido("conciliacion_bancaria.ver", "conciliacion_bancaria"))
            .contains("CAJA_BANCOS");
    }
}
