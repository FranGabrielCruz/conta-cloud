package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.BillingPrintUi;
import com.vaadin.flow.component.UI;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
class FacturacionSalidaTest {
 final UI ui=new UI();final FacturacionPosView view=mock(FacturacionPosView.class,CALLS_REAL_METHODS);final BillingPrintUi actions=mock(BillingPrintUi.class);
 final UUID id=UUID.randomUUID(),attempt=UUID.randomUUID();
 @BeforeEach void setup(){UI.setCurrent(ui);ReflectionTestUtils.setField(view,"billingPrintUi",actions);}
 @AfterEach void clear(){UI.setCurrent(null);}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void todasLasAccionesImprimirDeleganSinInterpretarSalida(TipoDocumentoImpresion type){
  String method=switch(type){case SALES_INVOICE->"imprimirFactura";case SALES_RETURN->"imprimirDevolucion";case PAYMENT_RECEIPT->"imprimirRecibo";case CASH_COUNT->"imprimirRecuento";case CASH_VOUCHER->"imprimirVale";case CASH_CLOSING->"imprimirCierre";};
  ReflectionTestUtils.invokeMethod(view,method,id,attempt);verify(actions).imprimir(type,id,attempt);verifyNoMoreInteractions(actions);
 }
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void verPdfExplicitoNuncaEntraALaCola(TipoDocumentoImpresion type){
  var file=new ArchivoPdfDto("manual.pdf",new byte[]{1});String method;
  switch(type){
   case SALES_INVOICE->{var pdf=mock(FacturaVentaPdfService.class);when(pdf.generar(id)).thenReturn(file);ReflectionTestUtils.setField(view,"documentos",pdf);method="abrirPdf";}
   case SALES_RETURN->{var pdf=mock(DevolucionVentaPdfService.class);when(pdf.generar(id)).thenReturn(file);ReflectionTestUtils.setField(view,"devolucionesPdf",pdf);method="abrirPdfDevolucion";}
   case PAYMENT_RECEIPT->{var pdf=mock(PaymentReceiptPrintService.class);when(pdf.generar(id)).thenReturn(file);ReflectionTestUtils.setField(view,"recibosImpresion",pdf);method="abrirPdfRecibo";}
   case CASH_COUNT->{var pdf=mock(RecuentoCajaPdfService.class);when(pdf.generar(id)).thenReturn(file);ReflectionTestUtils.setField(view,"recuentosCajaPdf",pdf);method="abrirPdfRecuento";}
   case CASH_VOUCHER->{var pdf=mock(ValeCajaPdfService.class);when(pdf.generar(id)).thenReturn(file);ReflectionTestUtils.setField(view,"valesCajaPdf",pdf);method="abrirPdfVale";}
   case CASH_CLOSING->{var pdf=mock(CierreCajaPdfService.class);when(pdf.generar(id)).thenReturn(file);ReflectionTestUtils.setField(view,"cierresCajaPdf",pdf);method="abrirPdfCierre";}
   default->throw new AssertionError(type);
  }
  ReflectionTestUtils.invokeMethod(view,method,id);verifyNoInteractions(actions);
 }
}
