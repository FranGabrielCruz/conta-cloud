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
        return permisos.stream().anyMatch(PERMISOS_CONFIGURACION_USUARIO::contains);
    }

    public List<GrupoMenu> obtener() {
        Set<String> permisos = TenantContext.principalActual().permisos();
        Set<String> habilitados = modules.habilitadosActuales();
        List<GrupoMenu> grupos = new ArrayList<>();
        grupos.add(new GrupoMenu("INICIO", List.of(new OpcionMenu("Dashboard", "dashboard", "HOME"))));

        List<OpcionMenu> administracion = new ArrayList<>();
        agregarSiModulo(permisos,habilitados, administracion, "empresas.ver", null,"EMPRESA", "Empresas", "empresas", "OFFICE");
        agregarSiModulo(permisos,habilitados, administracion, "USUARIO_VER", "usuarios.ver", "USUARIOS", "Usuarios", "usuarios", "USERS");
        agregarSiModulo(permisos,habilitados, administracion, "ROL_VER", "roles.ver", "ROLES", "Roles y permisos", "roles", "USER_STAR");
        if (!administracion.isEmpty()) grupos.add(new GrupoMenu("ADMINISTRACIÓN", List.copyOf(administracion)));

        List<OpcionMenu> configuracion = new ArrayList<>();
        agregarSiModulo(permisos,habilitados,configuracion,"SUCURSAL_VER",null,"SUCURSALES","Sucursales","configuracion","OFFICE");
        agregarSiModulo(permisos,habilitados,configuracion,"MONEDA_VER",null,"MONEDAS","Monedas","configuracion","COIN_PILES");
        agregarSiModulo(permisos,habilitados,configuracion,"TASA_CAMBIO_VER",null,"TASAS_CAMBIO","Tasas de cambio","tasas-cambio","EXCHANGE");
        agregarSiModulo(permisos,habilitados,configuracion,"IMPUESTO_VER",null,"IMPUESTOS","Impuestos","impuestos","CALC");
        agregarSiModulo(permisos,habilitados,configuracion,"COMPROBANTE_VER",null,"COMPROBANTES_FISCALES","Comprobantes fiscales","comprobantes-fiscales","FILE_TEXT_O");
        agregarSiModulo(permisos,habilitados,configuracion,"SECUENCIA_VER",null,"SECUENCIAS","Secuencias","secuencias","SORT_NUMERIC_ASC");
        agregarSiModulo(permisos,habilitados,configuracion,"CONDICION_PAGO_VER",null,"CONDICIONES_PAGO","Condiciones de pago","condiciones-pago","CLOCK");
        agregarSiModulo(permisos,habilitados,configuracion,"PERIODO_VER",null,"PERIODOS_FISCALES","Períodos fiscales","periodos-fiscales","CALENDAR");
        agregarSiModulo(permisos,habilitados,configuracion,"CONFIGURACION_CONTABLE_VER",null,"CONFIGURACION_CONTABLE","Configuración contable","configuracion-contable","BOOK_DOLLAR");
        if (!configuracion.isEmpty()) grupos.add(new GrupoMenu("CONFIGURACIÓN", List.copyOf(configuracion)));

        List<OpcionMenu> infraestructura=new ArrayList<>();
        agregarSi(permisos,infraestructura,"bases_datos.ver","Bases de datos","bases-datos","DATABASE");
        agregarSi(permisos,infraestructura,"migraciones.ver","Migraciones","migraciones","REFRESH");
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
