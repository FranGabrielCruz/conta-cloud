package com.citacloud.springboot.contacloud.app.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public final class TenantContext {
    private TenantContext() {}
    public static UUID requerirEmpresaId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("No existe un tenant autenticado");
        }
        return principal.empresaId();
    }
    public static TenantPrincipal principalActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("No existe un usuario autenticado");
        }
        return principal;
    }
}
