package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record TransferenciaFinancieraDto(UUID id,LocalDate fecha,TipoCuentaDinero tipoOrigen,UUID origenId,
    String origenNombre,TipoCuentaDinero tipoDestino,UUID destinoId,String destinoNombre,
    UUID monedaOrigenId,String monedaOrigenCodigo,UUID monedaDestinoId,String monedaDestinoCodigo,
    BigDecimal montoOrigen,BigDecimal montoDestino,BigDecimal tasaCambio,String descripcionTasa,
    String referencia,String descripcion,EstadoMovimientoFinanciero estado,OffsetDateTime creadoEn,
    OffsetDateTime anuladoEn,String anuladoPorNombre,String motivoAnulacion,boolean anulable) {}
