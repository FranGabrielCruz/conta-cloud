package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuServiceTest {

    private final EmpresaModuloService modules = mock(EmpresaModuloService.class);
    private final MenuService service = new MenuService(modules);

    @BeforeEach
    void habilitarModulosImplementados() {
        when(modules.habilitadosActuales()).thenReturn(Set.of(
            "CONFIGURACION", "USUARIOS", "ROLES",
            "TASAS_CAMBIO", "IMPUESTOS", "COMPROBANTES_FISCALES", "SECUENCIAS",
            "CONDICIONES_PAGO", "PERIODOS_FISCALES", "CONFIGURACION_CONTABLE"));
    }

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
    void configuracionSoloApareceEnElMenuDelUsuario() {
        autenticar(Set.of("EMPRESA_VER"));

        assertThat(service.obtener().stream()
            .flatMap(grupo -> grupo.opciones().stream())
            .map(MenuService.OpcionMenu::titulo))
            .containsExactly("Dashboard");
        assertThat(service.mostrarConfiguracionUsuario()).isTrue();
    }

    @Test
    void empresasPermaneceEnAdministracionConSuPermiso() {
        autenticar(Set.of("empresas.ver"));

        var administracion = service.obtener().stream()
            .filter(grupo -> grupo.titulo().equals("ADMINISTRACIÓN"))
            .findFirst().orElseThrow();
        assertThat(administracion.opciones()).containsExactly(
            new MenuService.OpcionMenu("Empresas", "empresas", "OFFICE"));
    }

    @Test
    void infraestructuraSeControlaConPermisosNormales() {
        autenticar(Set.of("bases_datos.ver", "migraciones.ver"));

        assertThat(service.obtener()).anySatisfy(grupo -> {
            assertThat(grupo.titulo()).isEqualTo("INFRAESTRUCTURA");
            assertThat(grupo.opciones()).extracting(MenuService.OpcionMenu::titulo)
                .containsExactly("Bases de datos", "Migraciones");
        });
    }

    @Test
    void unRolEspecialNoSustituyeLosPermisosGranulares() {
        autenticar(Set.of("ROLE_GLOBAL_SPECIAL"));

        assertThat(service.obtener()).containsExactly(
            new MenuService.GrupoMenu("INICIO", java.util.List.of(
                new MenuService.OpcionMenu("Dashboard", "dashboard", "HOME"))));
    }

    @Test
    void ocultaOpcionAunqueExistaPermisoCuandoModuloEstaDeshabilitado() {
        when(modules.habilitadosActuales()).thenReturn(Set.of("EMPRESA"));
        autenticar(Set.of("USUARIO_VER", "IMPUESTO_VER"));

        assertThat(service.obtener().stream()
            .flatMap(grupo -> grupo.opciones().stream())
            .map(MenuService.OpcionMenu::titulo))
            .containsExactly("Dashboard");
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

    @Test
    void empresaSucursalesYMonedasNoDuplicanConfiguracionEnElDrawer() {
        autenticar(Set.of("EMPRESA_VER", "SUCURSAL_VER", "MONEDA_VER"));

        assertThat(service.obtener().stream()
            .flatMap(grupo -> grupo.opciones().stream())
            .map(MenuService.OpcionMenu::titulo))
            .containsExactly("Dashboard");
        assertThat(service.mostrarConfiguracionUsuario()).isTrue();
    }

    private void autenticar(Set<String> permisos) {
        var principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPRESA01",
            "Administrador", "admin", "hash", true, permisos);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
