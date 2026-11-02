package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
import com.citacloud.springboot.contacloud.app.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BillingPrintConfigurationTest {
 static <T>void mocked(AnnotationConfigApplicationContext context,Class<T> type){context.registerBean(type,()->mock(type));}
 @Test void configuracionRealRegistraTodosLosTiposYFallaSiSeAgregaUnoSinAdaptador(){
  try(var context=new AnnotationConfigApplicationContext()){
   mocked(context,FacturaPosDocumentoService.class);mocked(context,FacturaVentaRepository.class);mocked(context,FacturaVentaPdfService.class);mocked(context,DevolucionPosDocumentoService.class);mocked(context,DevolucionVentaPdfService.class);mocked(context,PaymentReceiptSnapshotRepository.class);mocked(context,PaymentReceiptPrintService.class);mocked(context,CashCountDocumentService.class);mocked(context,CashCountPrintService.class);mocked(context,CashOperationPrintDocumentService.class);
   context.register(BillingPrintDocumentsConfiguration.class,BillingPrintDocumentRegistry.class);context.refresh();
   assertThat(context.getBean(BillingPrintDocumentRegistry.class).registeredTypes()).containsExactlyInAnyOrder(TipoDocumentoImpresion.values());
  }
 }
}
