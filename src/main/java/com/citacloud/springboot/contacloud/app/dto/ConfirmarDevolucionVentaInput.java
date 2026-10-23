package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.*;
public record ConfirmarDevolucionVentaInput(UUID claveOperacion,UUID facturaId,UUID motivoId,String observacion,
        boolean retornaInventario,UUID almacenDestinoId,List<Linea> lineas){
    public ConfirmarDevolucionVentaInput{lineas=lineas==null?List.of():List.copyOf(lineas);}
    public record Linea(UUID lineaFacturaId,BigDecimal cantidad){}
}
