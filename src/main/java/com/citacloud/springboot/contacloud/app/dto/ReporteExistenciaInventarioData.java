package com.citacloud.springboot.contacloud.app.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReporteExistenciaInventarioData(String empresaNombreComercial, String empresaRazonSocial,
        String empresaIdentificacion, String empresaDireccion, String empresaTelefono, String empresaCorreo,
        byte[] empresaLogo, String al, String busqueda, String sucursal, String almacen, String categoria,
        String estado, String stock, long productosEncontrados, boolean mostrarSucursal,
        boolean mostrarAlmacen, String generadoPor, OffsetDateTime generadoEn,
        List<LineaReporteExistenciaInventario> lineas) {}
