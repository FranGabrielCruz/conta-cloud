package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.DireccionMovimientoBancario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record MovimientoConciliableDto(UUID id,LocalDate fecha,String concepto,String referencia,
    BigDecimal monto,DireccionMovimientoBancario direccion,boolean pendienteAnterior) {}
