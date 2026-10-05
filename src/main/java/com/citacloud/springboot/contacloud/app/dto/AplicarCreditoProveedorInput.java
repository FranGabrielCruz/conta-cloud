package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.*;
public record AplicarCreditoProveedorInput(List<Aplicacion> aplicaciones,Long version){
 public record Aplicacion(UUID facturaId,BigDecimal monto){}
}
