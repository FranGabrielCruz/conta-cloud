package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrdenCompraInput(UUID proveedorId,UUID sucursalId,LocalDate fecha,LocalDate fechaEntrega,
        UUID monedaId,UUID condicionPagoId,String referencia,String notas,List<LineaOrdenCompraInput> lineas,Long version) {}
