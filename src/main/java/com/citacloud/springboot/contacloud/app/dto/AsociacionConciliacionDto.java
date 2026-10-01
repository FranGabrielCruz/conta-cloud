package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.DireccionMovimientoBancario;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record AsociacionConciliacionDto(UUID id,UUID movimientoFinancieroId,UUID movimientoBancarioId,
    LocalDate fechaContaCloud,String conceptoContaCloud,LocalDate fechaBanco,String descripcionBanco,
    String referenciaBanco,BigDecimal monto,DireccionMovimientoBancario direccion,OffsetDateTime conciliadoEn) {}
