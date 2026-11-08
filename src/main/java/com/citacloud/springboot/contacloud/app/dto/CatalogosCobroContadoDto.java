package com.citacloud.springboot.contacloud.app.dto;
import java.util.*;
public record CatalogosCobroContadoDto(List<MetodoPagoPosDto> metodos,List<CuentaDineroOpcionDto> cuentas,Map<UUID,UUID> sesionesCaja){
 public CatalogosCobroContadoDto{metodos=List.copyOf(metodos);cuentas=List.copyOf(cuentas);sesionesCaja=Map.copyOf(sesionesCaja);}
 public CatalogosCobroContadoDto(List<MetodoPagoPosDto> metodos,List<CuentaDineroOpcionDto> cuentas){this(metodos,cuentas,Map.of());}
}
