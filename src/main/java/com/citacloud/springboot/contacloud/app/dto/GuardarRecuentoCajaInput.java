package com.citacloud.springboot.contacloud.app.dto;
import java.util.*;
public record GuardarRecuentoCajaInput(UUID sesionId,UUID idempotencia,List<Cantidad> cantidades,String observacion){public record Cantidad(UUID denominacionId,Integer cantidad){}}
