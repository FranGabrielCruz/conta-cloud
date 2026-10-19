package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Almacen;
import com.citacloud.springboot.contacloud.app.models.TipoAlmacen;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PosWarehouseResolverTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID empresa = UUID.randomUUID();
    private final UUID sucursal = UUID.randomUUID();

    @Test
    void aceptaUnAlmacenGeneralActivoDeLaMismaSucursal() {
        assertThat(PosWarehouseResolver.validoParaVentas(
                almacen(tenant, empresa, sucursal, TipoAlmacen.GENERAL), tenant, empresa, sucursal)).isTrue();
    }

    @Test
    void rechazaAlmacenesDeOtraSucursalOTenant() {
        Almacen almacen = almacen(tenant, empresa, UUID.randomUUID(), TipoAlmacen.GENERAL);

        assertThat(PosWarehouseResolver.validoParaVentas(almacen, tenant, empresa, sucursal)).isFalse();
        assertThat(PosWarehouseResolver.validoParaVentas(
                almacen, UUID.randomUUID(), empresa, almacen.getSucursalId())).isFalse();
    }

    @Test
    void rechazaDevolucionesDanadosYTransito() {
        assertThat(valido(TipoAlmacen.DEVOLUCIONES)).isFalse();
        assertThat(valido(TipoAlmacen.DANADOS)).isFalse();
        assertThat(valido(TipoAlmacen.TRANSITO)).isFalse();
    }

    @Test
    void rechazaAlmacenInactivo() {
        Almacen almacen = almacen(tenant, empresa, sucursal, TipoAlmacen.GENERAL);
        almacen.cambiarEstado(false, UUID.randomUUID());

        assertThat(PosWarehouseResolver.validoParaVentas(almacen, tenant, empresa, sucursal)).isFalse();
    }

    private boolean valido(TipoAlmacen tipo) {
        return PosWarehouseResolver.validoParaVentas(
                almacen(tenant, empresa, sucursal, tipo), tenant, empresa, sucursal);
    }

    private static Almacen almacen(UUID tenant, UUID empresa, UUID sucursal, TipoAlmacen tipo) {
        return new Almacen(tenant, empresa, "ALM-1", "Principal", "principal", sucursal, tipo,
                null, null, null, UUID.randomUUID());
    }
}
