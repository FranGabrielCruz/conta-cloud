package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record ProductoPosDto(UUID id,String codigo,String nombre,String codigoBarras,String unidad,BigDecimal precio,
                             BigDecimal tasaImpuesto,BigDecimal existencia,boolean controlaExistencia,boolean permiteNegativo){
    @Override public String toString(){return codigo+" · "+nombre+" · "+precio;}
}
