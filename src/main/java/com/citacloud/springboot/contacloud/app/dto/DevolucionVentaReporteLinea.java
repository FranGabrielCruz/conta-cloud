package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
public record DevolucionVentaReporteLinea(int orden,String codigo,String descripcion,String unidad,BigDecimal cantidad,
        BigDecimal precio,BigDecimal impuesto,BigDecimal total){
    public Integer getOrden(){return orden;} public String getCodigo(){return codigo;} public String getDescripcion(){return descripcion;}
    public String getUnidad(){return unidad;} public BigDecimal getCantidad(){return cantidad;} public BigDecimal getPrecio(){return precio;}
    public BigDecimal getImpuesto(){return impuesto;} public BigDecimal getTotal(){return total;}
}
