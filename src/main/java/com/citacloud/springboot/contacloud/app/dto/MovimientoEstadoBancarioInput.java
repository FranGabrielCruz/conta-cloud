package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.DireccionMovimientoBancario;
import java.math.BigDecimal;
import java.time.LocalDate;
public record MovimientoEstadoBancarioInput(LocalDate fecha,DireccionMovimientoBancario direccion,
    String descripcion,String referencia,BigDecimal monto) {}
