package com.citacloud.springboot.contacloud.app.security;
import com.citacloud.springboot.contacloud.app.services.EmpresaModuloService;import org.springframework.stereotype.Component;
@Component("moduleAuthorization") public class ModuleAuthorization {
    private final EmpresaModuloService modules;public ModuleAuthorization(EmpresaModuloService modules){this.modules=modules;}
    public boolean enabled(String moduleKey){return modules.habilitado(moduleKey);}
    public boolean canAccess(String moduleKey,String permission){return modules.habilitado(moduleKey)&&TenantContext.principalActual().permisos().contains(permission);}
}
