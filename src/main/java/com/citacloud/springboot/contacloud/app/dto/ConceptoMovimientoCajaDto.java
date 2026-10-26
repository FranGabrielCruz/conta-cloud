package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoMovimientoFinanciero;
import java.util.UUID;
public record ConceptoMovimientoCajaDto(UUID id,String codigo,String nombre,
    TipoMovimientoFinanciero tipo,boolean requiereObservacion){}
