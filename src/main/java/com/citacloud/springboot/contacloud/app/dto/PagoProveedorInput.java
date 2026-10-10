package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public record PagoProveedorInput(UUID proveedorId,LocalDate fecha,UUID monedaId,BigDecimal monto,MedioPagoMovimiento medioPago,TipoCuentaDinero tipoFuente,UUID fuenteId,String numeroCheque,String referencia,String observacion,List<Aplicacion> aplicaciones,Long version,UUID claveIdempotencia){public record Aplicacion(UUID facturaId,BigDecimal monto){}}
