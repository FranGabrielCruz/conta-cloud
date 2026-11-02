package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.ResultadoRecuentoCaja;
import com.citacloud.springboot.contacloud.app.services.CierreCajaService;
import com.citacloud.springboot.contacloud.app.views.components.CierreCajaDialog;
import com.vaadin.flow.component.button.Button;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CashClosingPrintDialogTest {
 CierreCajaDto fixture(){var z=BigDecimal.ZERO;var date=OffsetDateTime.parse("2026-11-02T22:35:00Z");return new CierreCajaDto(UUID.randomUUID(),"CIE-000018",UUID.randomUUID(),"TURN-01","Caja 01","Principal","DOP","$",date,date,"Juan Pérez","Juan Pérez",z,z,z,z,z,z,z,z,z,z,z,z,ResultadoRecuentoCaja.BALANCED,null,List.of(),List.of());}
 Button action(CierreCajaDialog d,String label){return d.getFooter().getElement().getChildren().map(e->e.getComponent().orElse(null)).filter(Button.class::isInstance).map(Button.class::cast).filter(b->label.equals(b.getElement().getAttribute("aria-label"))).findFirst().orElseThrow();}
 @Test void retryKeepsAttemptAndManualPdfNeverClosesOrPrints(){var r=fixture();var service=mock(CierreCajaService.class);var attempts=new ArrayList<UUID>();var pdfs=new ArrayList<UUID>();var d=new CierreCajaDialog(r.sesionId(),service,null,(id,key)->{attempts.add(key);return false;},pdfs::add);ReflectionTestUtils.invokeMethod(d,"resultado",r);action(d,"Ver PDF").click();assertThat(pdfs).containsExactly(r.id());assertThat(attempts).isEmpty();action(d,"Imprimir cierre").click();action(d,"Imprimir cierre").click();assertThat(attempts).hasSize(2);assertThat(attempts.getFirst()).isEqualTo(attempts.getLast());verifyNoInteractions(service);}
 @Test void noPermissionOffersNoOutput(){var r=fixture();var d=new CierreCajaDialog(r.sesionId(),mock(CierreCajaService.class),null,null,null);ReflectionTestUtils.invokeMethod(d,"resultado",r);assertThat(d.getFooter().getElement().getChildren().map(e->e.getComponent().orElse(null)).filter(Button.class::isInstance).map(c->c.getElement().getAttribute("aria-label"))).doesNotContain("Imprimir cierre","Ver PDF");}
}
