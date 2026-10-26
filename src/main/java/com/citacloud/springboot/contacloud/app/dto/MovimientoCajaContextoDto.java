package com.citacloud.springboot.contacloud.app.dto;
import java.util.*;
public record MovimientoCajaContextoDto(UUID sesionId,String caja,String sucursal,String monedaCodigo,
    boolean abierta,List<ConceptoMovimientoCajaDto> conceptos){}
