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

        assertThat(opciones).contains("Dashboard", "Usuarios", "Configuración fiscal")
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
                .containsExactly("Bases de datos");
        });
    }

    @Test
    void permisoDeMigracionesAbreLaPantallaUnificadaDeBases() {
        autenticar(Set.of("migraciones.ver"));

        assertThat(service.obtener()).anySatisfy(grupo -> {
            assertThat(grupo.titulo()).isEqualTo("INFRAESTRUCTURA");
            assertThat(grupo.opciones()).containsExactly(
                new MenuService.OpcionMenu("Bases de datos", "bases-datos", "DATABASE"));
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

    @Test
    void periodosSeIntegraEnConfiguracionContableSinOpcionDuplicada() {
        autenticar(Set.of("periodos_fiscales.ver", "configuracion_contable.ver"));

        assertThat(service.obtener().stream()
            .flatMap(grupo -> grupo.opciones().stream())
            .map(MenuService.OpcionMenu::titulo))
            .contains("Configuración contable")
            .doesNotContain("Períodos fiscales");
    }

    @Test
    void construyeLasTresSeccionesDeFaseDosEnElOrdenDefinido() {
        when(modules.habilitadosActuales()).thenReturn(Set.of("VENTAS", "COMPRAS", "CAJA_BANCOS"));
        autenticar(Set.of(
            "clientes.ver", "cotizaciones.ver", "facturas.ver", "notas_credito.ver",
            "notas_debito.ver", "recibos.ver", "cuentas_cobrar.ver",
            "proveedores.ver", "ordenes_compra.ver", "facturas_proveedores.ver",
            "notas_credito_proveedores.ver", "pagos_proveedores.ver", "cuentas_pagar.ver",
            "cajas.ver", "cuentas_bancarias.ver", "ingresos.ver", "egresos.ver",
            "transferencias.ver", "conciliacion_bancaria.ver"));

        var grupos = service.obtener();

        assertThat(grupos).extracting(MenuService.GrupoMenu::titulo)
            .containsExactly("INICIO", "VENTAS", "COMPRAS", "CAJA Y BANCOS");
        assertThat(grupos.get(1).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Clientes", "Cotizaciones", "Facturas", "Notas de crédito",
                "Notas de débito", "Recibos", "Cuentas por cobrar");
        assertThat(grupos.get(2).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Proveedores", "Órdenes de compra", "Facturas de proveedores",
                "Notas de crédito", "Pagos", "Cuentas por pagar");
        assertThat(grupos.get(3).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Cajas", "Cuentas bancarias", "Ingresos", "Egresos",
                "Transferencias", "Conciliación bancaria");
    }

    @Test
    void filtraFaseDosPorPermisoYOcultaSeccionesVacias() {
        when(modules.habilitadosActuales()).thenReturn(Set.of("VENTAS", "COMPRAS", "CAJA_BANCOS"));
        autenticar(Set.of("clientes.ver", "facturas.ver", "cuentas_cobrar.ver", "cajas.ver", "ingresos.ver"));

        var grupos = service.obtener();

        assertThat(grupos).extracting(MenuService.GrupoMenu::titulo)
            .containsExactly("INICIO", "VENTAS", "CAJA Y BANCOS");
        assertThat(grupos.get(1).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Clientes", "Facturas", "Cuentas por cobrar");
        assertThat(grupos.get(2).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Cajas", "Ingresos");
    }

    @Test
    void recalculaFaseDosConLosModulosDeLaEmpresaActual() {
        autenticar(Set.of("clientes.ver", "proveedores.ver", "cajas.ver"));
        when(modules.habilitadosActuales()).thenReturn(Set.of("VENTAS", "COMPRAS"));
        assertThat(service.obtener()).extracting(MenuService.GrupoMenu::titulo)
            .contains("VENTAS", "COMPRAS").doesNotContain("CAJA Y BANCOS");

        when(modules.habilitadosActuales()).thenReturn(Set.of("VENTAS", "CAJA_BANCOS"));
        assertThat(service.obtener()).extracting(MenuService.GrupoMenu::titulo)
            .contains("VENTAS", "CAJA Y BANCOS").doesNotContain("COMPRAS");
    }

    @Test
    void muestraNuevasOpcionesSoloConModuloYPermiso() {
        when(modules.habilitadosActuales()).thenReturn(Set.of("INVENTARIO", "FISCAL", "REPORTES"));
        autenticar(Set.of("productos.ver", "almacenes.ver", "fiscal_606.ver", "reportes_inventario.ver"));

        var grupos=service.obtener();
        assertThat(grupos).extracting(MenuService.GrupoMenu::titulo)
            .containsExactly("INICIO", "INVENTARIO", "FISCAL", "REPORTES");
        assertThat(grupos.get(1).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Productos", "Almacenes");
        assertThat(grupos.get(2).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("606");
        assertThat(grupos.get(3).opciones()).extracting(MenuService.OpcionMenu::titulo)
            .containsExactly("Inventario");
    }

    @Test
    void ocultaNuevaSeccionSinPermisosOAunqueElModuloEsteDeshabilitado() {
        autenticar(Set.of("productos.ver", "catalogo_cuentas.ver"));
        when(modules.habilitadosActuales()).thenReturn(Set.of("INVENTARIO"));
        assertThat(service.obtener()).extracting(MenuService.GrupoMenu::titulo)
            .contains("INVENTARIO").doesNotContain("CONTABILIDAD");

        when(modules.habilitadosActuales()).thenReturn(Set.of("CONTABILIDAD"));
        autenticar(Set.of("productos.ver"));
        assertThat(service.obtener()).extracting(MenuService.GrupoMenu::titulo)
            .doesNotContain("INVENTARIO", "CONTABILIDAD");
    }

    private void autenticar(Set<String> permisos) {
        var principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPRESA01",
            "Administrador", "admin", "hash", true, permisos);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
