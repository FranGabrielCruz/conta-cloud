package com.citacloud.springboot.contacloud.app.multitenancy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantProvisioningPermissionsTest {

    @Test
    void excluyeAdministracionDeEmpresasEInfraestructuraDelNuevoTenant() {
        assertFalse(TenantProvisioningService.permisoDisponibleParaTenant("CORE", "empresas"));
        assertFalse(TenantProvisioningService.permisoDisponibleParaTenant("INFRAESTRUCTURA", "bases_datos"));
        assertFalse(TenantProvisioningService.permisoDisponibleParaTenant("infraestructura", "migraciones"));
    }

    @Test
    void conservaLosPermisosOperativosDelAdministrador() {
        assertTrue(TenantProvisioningService.permisoDisponibleParaTenant("CORE", "usuarios"));
        assertTrue(TenantProvisioningService.permisoDisponibleParaTenant("CORE", "roles"));
        assertTrue(TenantProvisioningService.permisoDisponibleParaTenant("MONEDAS", "monedas"));
        assertTrue(TenantProvisioningService.permisoDisponibleParaTenant(null, null));
    }
}
