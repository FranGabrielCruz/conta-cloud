package com.citacloud.springboot.contacloud.app.security;
import java.util.UUID;
public final class EmpresaContext {
    private EmpresaContext() {}
    public static UUID requerirEmpresaId(){ return TenantContext.principalActual().empresaId(); }
    public static boolean permiteSucursal(UUID sucursalId){
        var principal=TenantContext.principalActual();
        return principal.accesoTodasSucursales() || principal.sucursalIds().contains(sucursalId);
    }
}
