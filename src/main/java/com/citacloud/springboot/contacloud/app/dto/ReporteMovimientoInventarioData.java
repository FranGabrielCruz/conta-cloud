package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReporteMovimientoInventarioData(UUID tenantId, UUID empresaId, String empresaNombreComercial,
        String empresaRazonSocial, String empresaIdentificacion, String empresaDireccion, String empresaTelefono,
        String empresaCorreo, byte[] empresaLogo, String periodo, String busqueda, String sucursal,
        String almacen, String producto, String productoCodigo, String unidad, String tipo, String origen,
        long movimientos, long productosAfectados, BigDecimal saldoInicial, BigDecimal entradas,
        BigDecimal salidas, BigDecimal saldoFinal, boolean mostrarEntrada, boolean mostrarSalida,
        boolean mostrarAlmacen, String generadoPor, OffsetDateTime generadoEn,
        List<LineaReporteMovimientoInventario> lineas) {
    public ReporteMovimientoInventarioData {
        empresaLogo = empresaLogo == null ? null : empresaLogo.clone();
        lineas = List.copyOf(lineas);
    }

    @Override
    public byte[] empresaLogo() {
        return empresaLogo == null ? null : empresaLogo.clone();
    }
}
