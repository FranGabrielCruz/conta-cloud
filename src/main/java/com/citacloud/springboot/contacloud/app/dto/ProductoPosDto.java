package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record ProductoPosDto(UUID id,String codigo,String nombre,String codigoBarras,String unidad,BigDecimal precio,
                             BigDecimal tasaImpuesto,BigDecimal existencia,boolean controlaExistencia,boolean permiteNegativo,boolean permiteDescuentos,boolean permiteRecargos){
    public ProductoPosDto(UUID id,String codigo,String nombre,String codigoBarras,String unidad,BigDecimal precio,
                         BigDecimal tasaImpuesto,BigDecimal existencia,boolean controlaExistencia,boolean permiteNegativo){
        this(id,codigo,nombre,codigoBarras,unidad,precio,tasaImpuesto,existencia,controlaExistencia,permiteNegativo,true,true);
    }
    @Override public String toString(){return codigo+" · "+nombre+" · "+precio;}
}
