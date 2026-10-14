package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;

public record ConteoFisicoReporteData(
        String empresaNombreComercial,
        String empresaRazonSocial,
        String empresaIdentificacion,
        String empresaDireccion,
        String empresaTelefono,
        String empresaCorreo,
        byte[] empresaLogo,
        String numero,
        String fecha,
        String almacen,
        String sucursal,
        String tipo,
        String modo,
        String estado,
        String referencia,
        String observacion,
        boolean mostrarTeorico,
        String confirmadoPor,
        String confirmadoEn,
        String generadoPor,
        String generadoEn,
        List<Linea> lineas) {

    public record Linea(
            Integer orden,
            String codigo,
            String producto,
            String unidad,
            String inicial,
            String contado,
            String teorico,
            String diferencia) {

        public Integer getOrden() { return orden; }
        public String getCodigo() { return codigo; }
        public String getProducto() { return producto; }
        public String getUnidad() { return unidad; }
        public String getInicial() { return inicial; }
        public String getContado() { return contado; }
        public String getTeorico() { return teorico; }
        public String getDiferencia() { return diferencia; }
    }
}
