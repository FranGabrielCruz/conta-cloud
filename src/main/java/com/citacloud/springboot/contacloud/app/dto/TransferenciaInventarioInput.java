package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoTransferenciaInventario;import java.time.LocalDate;import java.math.BigDecimal;import java.util.*;
public record TransferenciaInventarioInput(LocalDate fecha,TipoTransferenciaInventario tipo,UUID almacenOrigenId,UUID almacenDestinoId,String referencia,String notas,List<Linea> lineas,Long version,UUID claveOperacion){
 public TransferenciaInventarioInput { if(claveOperacion==null)claveOperacion=UUID.randomUUID(); }
 public TransferenciaInventarioInput(LocalDate fecha,TipoTransferenciaInventario tipo,UUID almacenOrigenId,UUID almacenDestinoId,String referencia,String notas,List<Linea> lineas,Long version){this(fecha,tipo,almacenOrigenId,almacenDestinoId,referencia,notas,lineas,version,null);}
 public record Linea(UUID productoId,UUID unidadId,BigDecimal cantidad){} }
