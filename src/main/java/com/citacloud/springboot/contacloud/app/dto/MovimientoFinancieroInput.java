package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record MovimientoFinancieroInput(LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId,
    String concepto, BigDecimal monto, String referencia, String descripcion) {}
