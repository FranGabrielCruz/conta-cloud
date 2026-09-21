package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MenuServiceTest {

    private final MenuService service = new MenuService();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void muestraSoloOpcionesPermitidasDelTenant() {
        autenticar(Set.of("USUARIO_VER", "IMPUESTO_VER"));

        var opciones = service.obtener().stream()
            .flatMap(grupo -> grupo.opciones().stream())
            .map(MenuService.OpcionMenu::titulo)
            .toList();

        assertThat(opciones).contains("Dashboard", "Usuarios", "Impuestos")
            .doesNotContain("Empresa", "Roles y permisos", "Empresas", "Tenants");
    }

    @Test
    void empresaApareceEnAdministracionSoloConPermisoYEnlazaEmpresaActual() {
        autenticar(Set.of("EMPRESA_VER"));

        var administracion = service.obtener().stream()
            .filter(grupo -> grupo.titulo().equals("ADMINISTRACIÓN"))
            .findFirst().orElseThrow();
        assertThat(administracion.opciones()).containsExactly(
            new MenuService.OpcionMenu("Empresa", "empresa", "OFFICE"));
    }

    @Test
    void plataformaSoloExisteParaSuperadministrador() {
        autenticar(Set.of("ROLE_PLATFORM_SUPERADMIN"));

        assertThat(service.obtener()).anySatisfy(grupo -> {
            assertThat(grupo.titulo()).isEqualTo("PLATAFORMA");
            assertThat(grupo.opciones()).extracting(MenuService.OpcionMenu::titulo)
                .containsExactly("Tenants", "Bases de datos", "Asignaciones", "Migraciones");
        });
    }

    @Test
    void rolSinPermisosNoMuestraOpcionesNiSeccionesVacias() {
        autenticar(Set.of());

        assertThat(service.obtener()).containsExactly(
            new MenuService.GrupoMenu("INICIO", java.util.List.of(
                new MenuService.OpcionMenu("Dashboard", "dashboard", "HOME"))));
        assertThat(service.mostrarConfiguracionUsuario()).isFalse();
    }

    @Test
    void configuracionDelUsuarioSoloApareceConUnaOpcionPermitida() {
        autenticar(Set.of("SUCURSAL_VER"));
        assertThat(service.mostrarConfiguracionUsuario()).isTrue();

        autenticar(Set.of("ROL_VER"));
        assertThat(service.mostrarConfiguracionUsuario()).isFalse();
    }

    private void autenticar(Set<String> permisos) {
        var principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPRESA01",
            "Administrador", "admin", "hash", true, permisos);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
