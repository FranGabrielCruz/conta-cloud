package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TransferenciaInventarioReporteData(
    String empresaNombreComercial, String empresaRazonSocial, String empresaIdentificacion,
    String empresaDireccion, String empresaTelefono, String empresaCorreo, byte[] empresaLogo,
    String numero, String estado, LocalDate fecha, String tipo, String referencia, String observacion,
    String almacenOrigen, String sucursalOrigen, String almacenDestino, String sucursalDestino,
    OffsetDateTime despachadaEn, String despachadaPor, OffsetDateTime recibidaEn, String recibidaPor,
    String generadoPor, OffsetDateTime generadoEn, List<Linea> lineas) {

    public record Linea(Integer orden, String codigo, String producto, String descripcion, String unidad,
        String despachado, String recibido, String diferencia, String motivoDiferencia) {
        public Integer getOrden(){return orden;} public String getCodigo(){return codigo;}
        public String getProducto(){return producto;} public String getDescripcion(){return descripcion;}
        public String getUnidad(){return unidad;} public String getDespachado(){return despachado;}
        public String getRecibido(){return recibido;} public String getDiferencia(){return diferencia;}
        public String getMotivoDiferencia(){return motivoDiferencia;}
    }
}
