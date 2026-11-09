package com.citacloud.springboot.contacloud.app.views.components;
import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
import com.citacloud.springboot.contacloud.app.services.BillingPrintApplicationService;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.vaadin.flow.component.notification.Notification;
import java.util.UUID;
import org.springframework.stereotype.Component;
/** One presentation handler for every registered billing document. No fallback changes the output mode. */
@Component
public class BillingPrintUi {
 private final BillingPrintApplicationService service;
 public BillingPrintUi(BillingPrintApplicationService service){this.service=service;}
 public boolean imprimir(TipoDocumentoImpresion type,UUID id,UUID attempt){
  try{var result=service.imprimir(type,id,attempt);switch(result.outputMode()){
   case SCREEN -> mostrarPdf(result.screenDocument());
   case PRINT_AGENT -> Notification.show("Impresión pendiente del agente de esta caja.");
  }return true;}catch(RuntimeException ex){UiErrorHandler.show(ex,"La operación permanece guardada. No fue posible solicitar su impresión.");return false;}
 }
 public AppActionButton boton(TipoDocumentoImpresion type,UUID id,ButtonSize size,String label){
  return new BillingPrintActionButton(id,size,label,(document,attempt)->imprimir(type,document,attempt));
 }
 void mostrarPdf(ArchivoPdfDto file){PdfEnPestana.abrir(file);}
}
