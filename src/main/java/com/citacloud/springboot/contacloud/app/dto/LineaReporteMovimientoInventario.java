package com.citacloud.springboot.contacloud.app.dto;

public record LineaReporteMovimientoInventario(String fechaHora, String documento, String producto,
        String productoCodigo, String almacen, String sucursal, String origen, String concepto,
        String entrada, String salida, String existencia) {

    public String getFechaHora() {
        return fechaHora;
    }

    public String getDocumento() {
        return documento;
    }

    public String getProducto() {
        return producto;
    }

    public String getProductoCodigo() {
        return productoCodigo;
    }

    public String getAlmacen() {
        return almacen;
    }

    public String getSucursal() {
        return sucursal;
    }

    public String getOrigen() {
        return origen;
    }

    public String getConcepto() {
        return concepto;
    }

    public String getEntrada() {
        return entrada;
    }

    public String getSalida() {
        return salida;
    }

    public String getExistencia() {
        return existencia;
    }
}
