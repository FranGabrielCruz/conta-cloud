package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.services.EmpresaModuloService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ModuleAuthorizationTest {

    private final EmpresaModuloService modules = mock(EmpresaModuloService.class);
    private final ModuleAuthorization authorization = new ModuleAuthorization(modules);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void exigeModuloHabilitadoYPermisoDelUsuario() {
        authenticate(Set.of("IMPUESTO_VER"));
        when(modules.habilitado("IMPUESTOS")).thenReturn(false, true);

        assertThat(authorization.canAccess("IMPUESTOS", "IMPUESTO_VER")).isFalse();
        assertThat(authorization.canAccess("IMPUESTOS", "IMPUESTO_VER")).isTrue();
        assertThat(authorization.canAccess("IMPUESTOS", "IMPUESTO_EDITAR")).isFalse();
    }

    private void authenticate(Set<String> permissions) {
        TenantPrincipal principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPRESA",
            "Usuario", "user", "hash", true, permissions);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
