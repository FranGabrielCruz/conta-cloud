package com.citacloud.springboot.contacloud.app.dto;

import java.util.*;
public final class ConfiguracionMotorDto {
 private ConfiguracionMotorDto() {}
 public enum Modulo {
  VENTAS("Ventas"), COMPRAS("Compras"), INVENTARIO("Inventario"), CXC("CxC"), CXP("CxP"),
  FINANZAS("Finanzas"), ACTIVOS_FIJOS("Activos fijos"), CIERRE("Cierre");
  private final String etiqueta;
  Modulo(String etiqueta) { this.etiqueta=etiqueta; }
  public String etiqueta() { return etiqueta; }
  public static List<Modulo> automatizables() { return List.of(VENTAS,COMPRAS,INVENTARIO,CXC,CXP,ACTIVOS_FIJOS); }
 }
 public record Funcion(String clave,String nombre,Set<Modulo> modulos,Set<Modulo> obligatoria,Set<String> clasificaciones) {}
 public record Cuenta(UUID id,String codigo,String nombre,boolean activa,String tipo,String clasificacion,
                      boolean resultado, String centroCosto) {
  public String etiqueta() { return codigo+" — "+nombre; }
 }
 public enum EstadoConfiguracion { READY, INCOMPLETE, UNAVAILABLE }
 public record EstadoModulo(Modulo modulo,boolean completo,boolean automatico,boolean efectivo,List<String> pendientes,
                            EstadoConfiguracion estado,String motivo,boolean integrationAvailable,
                            boolean userCanManageAutomation,String unavailableReason) {
  public EstadoModulo(Modulo modulo,boolean completo,boolean automatico,boolean efectivo,List<String> pendientes,
                      EstadoConfiguracion estado,String motivo) {
   this(modulo,completo,automatico,efectivo,pendientes,estado,motivo,estado!=EstadoConfiguracion.UNAVAILABLE,true,
    estado==EstadoConfiguracion.UNAVAILABLE?"INTEGRATION_NOT_IMPLEMENTED":null);
  }
  public EstadoModulo(Modulo modulo,boolean completo,boolean automatico,boolean efectivo,List<String> pendientes) {
   this(modulo,completo,automatico,efectivo,pendientes,completo?EstadoConfiguracion.READY:EstadoConfiguracion.INCOMPLETE,"");
  }
 }
 public record Estado(long version,String metodo,boolean automatico,boolean catalogo,boolean periodos,
                      Map<String,Cuenta> cuentas,List<EstadoModulo> modulos,List<EstadoModulo> cuentasModulos,
                      boolean canManageAutomation) {
  public Estado(long version,String metodo,boolean automatico,boolean catalogo,boolean periodos,Map<String,Cuenta> cuentas,List<EstadoModulo> modulos) {
   this(version,metodo,automatico,catalogo,periodos,cuentas,modulos,modulos,true);
  }
  public boolean puedeActivarMaestro() { return catalogo && modulos.stream().anyMatch(m->m.completo()&&m.integrationAvailable()); }
  public long modulosListos() { return modulos.stream().filter(m->m.completo()&&m.integrationAvailable()).count(); }
  public long modulosActivos() { return modulos.stream().filter(EstadoModulo::efectivo).count(); }
 }
 public record Entrada(long version,String metodo,boolean automatico,Map<String,UUID> cuentas,Map<Modulo,Boolean> modulos) {}
}

