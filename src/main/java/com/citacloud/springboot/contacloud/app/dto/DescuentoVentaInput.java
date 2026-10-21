package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;import java.math.BigDecimal;import java.util.*;
public record DescuentoVentaInput(AlcanceDescuentoVenta alcance,TipoDescuentoVenta tipo,BigDecimal valor,List<UUID> lineaIds,UUID motivoId,String detalle,UUID autorizacionId){
 public DescuentoVentaInput{lineaIds=lineaIds==null?List.of():List.copyOf(lineaIds);}
}
