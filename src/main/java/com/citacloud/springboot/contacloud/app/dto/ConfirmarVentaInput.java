package com.citacloud.springboot.contacloud.app.dto;
import java.util.*;
public record ConfirmarVentaInput(String token,UUID almacenId,UUID clienteId,String nota,UUID idempotencia,UUID ventaGuardadaId,
                                  List<LineaVentaInput> lineas,List<AplicacionCobroInput> aplicaciones){}
