package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
/** All UI print commands enter here after the business transaction has committed. */
@Service
public class BillingPrintApplicationService {
 private final BillingPrintDocumentRegistry documents;private final BillingPrintRouter router;
 public BillingPrintApplicationService(BillingPrintDocumentRegistry documents,BillingPrintRouter router){this.documents=documents;this.router=router;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 @PreAuthorize("isAuthenticated()")
 public BillingPrintResult imprimir(TipoDocumentoImpresion type,UUID id,UUID attempt){
  var document=documents.load(type,id);
  var output=router.solicitar(new BillingPrintRouter.Documento(type,id,document.branchId(),document.cashRegisterId(),document.printer(),document.snapshot()),attempt);
  return new BillingPrintResult(output,output.outputMode()==BillingPrintOutputMode.SCREEN?document.screen().get():null);
 }
}
