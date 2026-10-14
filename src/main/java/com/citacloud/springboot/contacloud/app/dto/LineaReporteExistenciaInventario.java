package com.citacloud.springboot.contacloud.app.dto;

public record LineaReporteExistenciaInventario(String producto, String codigo, String unidad,
        String sucursal, String almacen, String existencia) {
    public String getProducto(){return producto;} public String getCodigo(){return codigo;}
    public String getUnidad(){return unidad;} public String getSucursal(){return sucursal;}
    public String getAlmacen(){return almacen;} public String getExistencia(){return existencia;}
}
