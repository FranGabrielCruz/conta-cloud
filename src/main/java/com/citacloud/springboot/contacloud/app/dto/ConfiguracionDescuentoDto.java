package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.*;
public record ConfiguracionDescuentoDto(boolean habilitado,boolean permiteVenta,boolean permiteLinea,boolean permitePorcentaje,boolean permiteMonto,boolean requiereMotivo,boolean permiteAutorizacion,long version,String monedaBase,List<Limite> limites,List<Motivo> motivos,List<RolOpcion> roles){
 public record Limite(UUID id,UUID rolId,String rol,BigDecimal porcentajeMaximo,BigDecimal montoMaximo,boolean activo,long version){}
 public record Motivo(UUID id,String nombre,boolean otro,boolean activo,long version){@Override public String toString(){return nombre;}}
 public record RolOpcion(UUID id,String nombre){@Override public String toString(){return nombre;}}
}
