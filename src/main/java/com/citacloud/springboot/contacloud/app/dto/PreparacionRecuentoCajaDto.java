package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoDenominacionCaja; import java.math.BigDecimal; import java.time.OffsetDateTime; import java.util.*;
public record PreparacionRecuentoCajaDto(UUID sesionId,String caja,String sucursal,UUID monedaId,String monedaCodigo,String monedaSimbolo,OffsetDateTime fechaHora,String usuario,List<Denominacion> denominaciones){public record Denominacion(UUID id,BigDecimal valor,TipoDenominacionCaja tipo,int orden){}}
