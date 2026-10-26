package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
public record MovimientoCajaDto(UUID id,String numero,OffsetDateTime fechaHora,String caja,String sucursal,
    String usuario,TipoMovimientoFinanciero tipo,TipoOrigenMovimiento origen,String concepto,String referencia,
    BigDecimal importe,BigDecimal saldo,String monedaCodigo,String observacion,EstadoMovimientoFinanciero estado,
    UUID reversionDeId,boolean reversible){}
