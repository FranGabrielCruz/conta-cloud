package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class MenuService {
    public List<GrupoMenu> obtener() {
        Set<String> permisos = TenantContext.principalActual().permisos();
        List<GrupoMenu> grupos = new ArrayList<>();
        grupos.add(new GrupoMenu("INICIO", List.of(new OpcionMenu("Dashboard", "dashboard", "HOME"))));

        List<OpcionMenu> administracion = new ArrayList<>();
        if (permisos.contains("ROLE_PLATFORM_SUPERADMIN")) administracion.add(new OpcionMenu("Empresas", "empresas", "OFFICE"));
        agregarSi(permisos, administracion, "USUARIO_VER", "Usuarios", "usuarios", "USERS");
        agregarSi(permisos, administracion, "ROL_VER", "Roles y permisos", "roles", "USER_STAR");
        if (!administracion.isEmpty()) grupos.add(new GrupoMenu("ADMINISTRACIÓN", List.copyOf(administracion)));

        List<OpcionMenu> configuracion = new ArrayList<>();
        agregarSi(permisos, configuracion, "TASA_CAMBIO_VER", "Tasas de cambio", "tasas-cambio", "EXCHANGE");
        agregarSi(permisos, configuracion, "IMPUESTO_VER", "Impuestos", "impuestos", "CALC");
        agregarSi(permisos, configuracion, "COMPROBANTE_VER", "Comprobantes fiscales", "comprobantes-fiscales", "FILE_TEXT_O");
        agregarSi(permisos, configuracion, "SECUENCIA_VER", "Secuencias", "secuencias", "SORT_NUMERIC_ASC");
        agregarSi(permisos, configuracion, "CONDICION_PAGO_VER", "Condiciones de pago", "condiciones-pago", "CLOCK");
        agregarSi(permisos, configuracion, "PERIODO_VER", "Períodos fiscales", "periodos-fiscales", "CALENDAR");
        agregarSi(permisos, configuracion, "CONFIGURACION_CONTABLE_VER", "Configuración contable", "configuracion-contable", "BOOK_DOLLAR");
        if (!configuracion.isEmpty()) grupos.add(new GrupoMenu("CONFIGURACIÓN", List.copyOf(configuracion)));

        if (permisos.contains("ROLE_PLATFORM_SUPERADMIN")) {
            grupos.add(new GrupoMenu("PLATAFORMA", List.of(
                new OpcionMenu("Tenants", "plataforma/tenants", "CLOUD"),
                new OpcionMenu("Bases de datos", "plataforma/bases-datos", "DATABASE"),
                new OpcionMenu("Asignaciones", "plataforma/asignaciones", "CONNECT"),
                new OpcionMenu("Migraciones", "plataforma/migraciones", "REFRESH"))));
        }
        return List.copyOf(grupos);
    }
    private void agregarSi(Set<String> permisos, List<OpcionMenu> opciones, String permiso, String titulo, String ruta, String icono) {
        if (permisos.contains(permiso)) opciones.add(new OpcionMenu(titulo, ruta, icono));
    }
    public record GrupoMenu(String titulo, List<OpcionMenu> opciones) {}
    public record OpcionMenu(String titulo, String ruta, String icono) {}
}
