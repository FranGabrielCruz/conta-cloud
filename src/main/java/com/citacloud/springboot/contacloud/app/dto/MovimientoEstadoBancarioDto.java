package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record MovimientoEstadoBancarioDto(UUID id,LocalDate fecha,DireccionMovimientoBancario direccion,
    String descripcion,String referencia,BigDecimal monto,FuenteMovimientoBancario fuente) {}
