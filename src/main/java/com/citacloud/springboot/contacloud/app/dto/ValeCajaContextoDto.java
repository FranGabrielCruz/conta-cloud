package com.citacloud.springboot.contacloud.app.dto;
import java.util.List;import java.util.UUID;
public record ValeCajaContextoDto(UUID sesionId,String caja,String sucursal,String monedaCodigo,boolean abierta,List<ConceptoValeCajaDto> conceptos){}
