package com.citacloud.springboot.contacloud.app.dto;
import java.time.LocalDateTime;import java.math.BigDecimal;
public record NotaDebitoVentaCicloDto(String estado,long version,boolean anulable,String motivo,String anuladaPor,LocalDateTime anuladaEn,BigDecimal pagado,BigDecimal saldo){
 public NotaDebitoVentaCicloDto(String estado,long version,boolean anulable,String motivo,String anuladaPor,LocalDateTime anuladaEn){this(estado,version,anulable,motivo,anuladaPor,anuladaEn,null,null);}
}
