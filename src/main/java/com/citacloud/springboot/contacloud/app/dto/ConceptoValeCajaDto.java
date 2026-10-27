package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record ConceptoValeCajaDto(UUID id,String codigo,String nombre,boolean requiereReferencia,boolean requiereObservacion){}
