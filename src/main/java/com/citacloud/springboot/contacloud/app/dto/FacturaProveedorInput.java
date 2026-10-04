package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FacturaProveedorInput(UUID proveedorId,UUID sucursalId,String numeroFactura,String numeroFiscal,
        LocalDate fecha,LocalDate vencimiento,UUID condicionPagoId,UUID monedaId,UUID ordenCompraId,
        String referencia,String notas,List<LineaFacturaProveedorInput> lineas,Long version) {}
