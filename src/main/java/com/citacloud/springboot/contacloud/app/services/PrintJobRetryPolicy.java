package com.citacloud.springboot.contacloud.app.services;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
/** Central delivery policy; separate from connection retries. */
@Component
public final class PrintJobRetryPolicy {
 public final int maximumAttempts,leaseSeconds,baseDelaySeconds,maximumDelaySeconds;private final double jitter;
 public PrintJobRetryPolicy(@Value("${contacloud.print-queue.maximum-attempts:5}")int attempts,@Value("${contacloud.print-queue.lease-seconds:300}")int lease,@Value("${contacloud.print-queue.base-delay-seconds:5}")int base,@Value("${contacloud.print-queue.maximum-delay-seconds:300}")int max,@Value("${contacloud.print-queue.jitter:0.1}")double jitter){
  if(attempts<1||attempts>100||lease<30||lease>3600||base<1||max<base||max>86400||!Double.isFinite(jitter)||jitter<0||jitter>.5)throw new IllegalArgumentException("Política de impresión inválida");
  maximumAttempts=attempts;leaseSeconds=lease;baseDelaySeconds=base;maximumDelaySeconds=max;this.jitter=jitter;
 }
 public static PrintJobRetryPolicy defaults(){return new PrintJobRetryPolicy(5,300,5,300,.1);}
 public int delaySeconds(int attempt){double value=Math.min(maximumDelaySeconds,baseDelaySeconds*Math.pow(2,Math.max(0,Math.min(30,attempt-1))));return Math.max(1,(int)Math.min(maximumDelaySeconds,Math.round(value*(1+java.util.concurrent.ThreadLocalRandom.current().nextDouble(-Math.max(jitter,.000001),Math.max(jitter,.000001))))));}
 public boolean transientError(String code){return java.util.Set.of("PRINTER_NOT_CONFIGURED","PRINTER_UNAVAILABLE","TEMPORARY_IO_ERROR","TIMEOUT","OUTPUT_ERROR").contains(code);}
 public static String validateCode(boolean printed,String code){if(printed){if(code!=null)throw new ReglaNegocioException("Resultado de impresión inválido.");return null;}
  if(code==null||!java.util.Set.of("PRINTER_NOT_CONFIGURED","PRINTER_UNAVAILABLE","TEMPORARY_IO_ERROR","TIMEOUT","OUTPUT_ERROR","INVALID_PAYLOAD","INVALID_SNAPSHOT","UNSUPPORTED_DOCUMENT_LAYOUT","UNSUPPORTED_PAPER_SIZE","RENDER_ERROR","OUTPUT_UNCERTAIN","LOCAL_STATE_ERROR","CANCELLED").contains(code))throw new ReglaNegocioException("Código de impresión inválido.");return code;
 }
}
