package com.citacloud.springboot.contacloud.app.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(UUID usuarioId, UUID tenantId, UUID empresaId, UUID usuarioEmpresaId,
                              String empresaCodigo, String nombre,
                              String username, String password, boolean enabled,
                              boolean accesoTodasSucursales, Set<UUID> sucursalIds,
                              Set<String> permisos) implements UserDetails {
    public TenantPrincipal(UUID usuarioId, UUID empresaId, String empresaCodigo, String nombre,
                           String username, String password, boolean enabled, Set<String> permisos) {
        this(usuarioId, empresaId, empresaId, null, empresaCodigo, nombre, username, password, enabled,
            true, Set.of(), permisos);
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return permisos.stream().map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isEnabled() { return enabled; }
}
