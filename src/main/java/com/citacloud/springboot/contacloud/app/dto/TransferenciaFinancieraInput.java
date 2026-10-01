package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record TransferenciaFinancieraInput(LocalDate fecha,TipoCuentaDinero tipoOrigen,UUID origenId,
    TipoCuentaDinero tipoDestino,UUID destinoId,BigDecimal montoOrigen,BigDecimal tasaCambio,
    String referencia,String descripcion) {}
