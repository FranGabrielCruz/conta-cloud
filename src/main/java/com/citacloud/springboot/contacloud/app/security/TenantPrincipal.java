package com.citacloud.springboot.contacloud.app.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(UUID usuarioId, UUID empresaId, String empresaCodigo, String nombre,
                              String username, String password, boolean enabled,
                              Set<String> permisos) implements UserDetails {
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return permisos.stream().map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isEnabled() { return enabled; }
}
