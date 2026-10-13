package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;import java.time.*;import java.math.BigDecimal;import java.util.*;
public record TransferenciaInventarioDto(UUID id,String numero,LocalDate fecha,TipoTransferenciaInventario tipo,
 UUID almacenOrigenId,String almacenOrigen,String sucursalOrigen,UUID almacenDestinoId,String almacenDestino,String sucursalDestino,
 EstadoTransferenciaInventario estado,String referencia,String notas,OffsetDateTime despachadaEn,String despachadaPor,
 OffsetDateTime recibidaEn,String recibidaPor,
 OffsetDateTime canceladaEn,String motivoCancelacion,long version,List<Linea> lineas){
 public record Linea(UUID id,UUID productoId,String codigo,String producto,String descripcion,UUID unidadId,String unidad,
  BigDecimal cantidadDespachada,BigDecimal cantidadRecibida,BigDecimal cantidadPendiente,BigDecimal diferencia,String motivoDiferencia){}
}
