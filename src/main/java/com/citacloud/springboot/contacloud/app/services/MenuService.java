package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class MenuService {
    private static final Set<String> PERMISOS_CONFIGURACION_USUARIO = Set.of(
        "EMPRESA_VER", "empresa.ver", "SUCURSAL_VER", "sucursales.ver", "MONEDA_VER", "monedas.ver");

    private final EmpresaModuloService modules;
    public MenuService(EmpresaModuloService modules){this.modules=modules;}

    public boolean mostrarConfiguracionUsuario() {
        Set<String> permisos = TenantContext.principalActual().permisos();
        return modules.habilitadosActuales().contains("CONFIGURACION")
            && permisos.stream().anyMatch(PERMISOS_CONFIGURACION_USUARIO::contains);
    }

    public List<GrupoMenu> obtener() {
        Set<String> permisos = TenantContext.principalActual().permisos();
        Set<String> habilitados = modules.habilitadosActuales();
        List<GrupoMenu> grupos = new ArrayList<>();
        grupos.add(new GrupoMenu("INICIO", List.of(new OpcionMenu("Dashboard", "dashboard", "HOME"))));

        for (PhaseTwoNavigation.Section section : PhaseTwoNavigation.SECTIONS) {
            List<OpcionMenu> options = section.items().stream()
                .filter(item -> habilitados.contains(item.module()) && permisos.contains(item.permission()))
                .map(item -> new OpcionMenu(item.title(), item.route(), item.icon()))
                .toList();
            if (!options.isEmpty()) grupos.add(new GrupoMenu(section.title(), options));
        }

        List<OpcionMenu> administracion = new ArrayList<>();
        agregarSi(permisos, administracion, "empresas.ver", "Empresas", "empresas", "OFFICE");
        agregarSiModulo(permisos,habilitados, administracion, "USUARIO_VER", "usuarios.ver", "USUARIOS", "Usuarios", "usuarios", "USERS");
        agregarSiModulo(permisos,habilitados, administracion, "ROL_VER", "roles.ver", "ROLES", "Roles y permisos", "roles", "USER_STAR");
        if (!administracion.isEmpty()) grupos.add(new GrupoMenu("ADMINISTRACIÓN", List.copyOf(administracion)));

        List<OpcionMenu> configuracion = new ArrayList<>();
        agregarSiModulo(permisos,habilitados,configuracion,"TASA_CAMBIO_VER","tasas_cambio.ver","TASAS_CAMBIO","Tasas de cambio","tasas-cambio","EXCHANGE");
        boolean fiscal=(habilitados.contains("IMPUESTOS")&&(permisos.contains("impuestos.ver")||permisos.contains("IMPUESTO_VER")))
            ||(habilitados.contains("COMPROBANTES_FISCALES")&&(permisos.contains("comprobantes_fiscales.ver")||permisos.contains("COMPROBANTE_VER")))
            ||(habilitados.contains("SECUENCIAS")&&(permisos.contains("secuencias.ver")||permisos.contains("SECUENCIA_VER")));
        if(fiscal)configuracion.add(new OpcionMenu("Configuración fiscal","configuracion-fiscal","FILE_TEXT_O"));
        agregarSiModulo(permisos,habilitados,configuracion,"CONDICION_PAGO_VER","condiciones_pago.ver","CONDICIONES_PAGO","Condiciones de pago","condiciones-pago","CLOCK");
        boolean contabilidad = habilitados.contains("CONFIGURACION_CONTABLE")
            && (permisos.contains("configuracion_contable.ver")
                || permisos.contains("CONFIGURACION_CONTABLE_VER")
                || permisos.contains("periodos_fiscales.ver")
                || permisos.contains("PERIODO_VER"));
        if (contabilidad) configuracion.add(new OpcionMenu(
            "Configuración contable", "configuracion-contable", "BOOK_DOLLAR"));
        if (!configuracion.isEmpty()) grupos.add(new GrupoMenu("CONFIGURACIÓN", List.copyOf(configuracion)));

        List<OpcionMenu> infraestructura=new ArrayList<>();
        if(permisos.contains("bases_datos.ver")||permisos.contains("migraciones.ver"))
            infraestructura.add(new OpcionMenu("Bases de datos","bases-datos","DATABASE"));
        if(!infraestructura.isEmpty())grupos.add(new GrupoMenu("INFRAESTRUCTURA",List.copyOf(infraestructura)));
        return List.copyOf(grupos);
    }
    private void agregarSi(Set<String> permisos, List<OpcionMenu> opciones, String permiso, String titulo, String ruta, String icono) {
        if (permisos.contains(permiso)) opciones.add(new OpcionMenu(titulo, ruta, icono));
    }
    private void agregarSi(Set<String> permisos, List<OpcionMenu> opciones, String permiso, String alterno, String titulo, String ruta, String icono) {
        if (permisos.contains(permiso) || permisos.contains(alterno)) opciones.add(new OpcionMenu(titulo, ruta, icono));
    }
    private void agregarSiModulo(Set<String> permisos,Set<String> modulos,List<OpcionMenu> opciones,String permiso,String alterno,String modulo,String titulo,String ruta,String icono){
        if(modulos.contains(modulo)&&(permisos.contains(permiso)||(alterno!=null&&permisos.contains(alterno))))opciones.add(new OpcionMenu(titulo,ruta,icono));
    }
    public record GrupoMenu(String titulo, List<OpcionMenu> opciones) {}
    public record OpcionMenu(String titulo, String ruta, String icono) {}
}
