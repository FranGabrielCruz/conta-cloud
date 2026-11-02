package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.ResultadoRecuentoCaja;
import com.citacloud.springboot.contacloud.app.views.components.DetalleRecuentoCajaDialog;
import com.vaadin.flow.component.button.Button;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CashCountPrintDialogTest {
    RecuentoCajaDto fixture(){return new RecuentoCajaDto(UUID.randomUUID(),"RCJ-001",UUID.randomUUID(),"Turno 1","Caja 1","Principal","DOP","$",OffsetDateTime.parse("2026-11-01T20:15:00Z"),"Juan",BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,ResultadoRecuentoCaja.BALANCED,null,List.of());}
    Button action(DetalleRecuentoCajaDialog d,String label){return d.getFooter().getElement().getChildren().map(element->element.getComponent().orElse(null)).filter(Button.class::isInstance).map(Button.class::cast).filter(b->label.equals(b.getElement().getAttribute("aria-label"))).findFirst().orElseThrow();}
    @Test void dobleClickConservaClaveYVerPdfNoSolicitaImpresion(){var r=fixture();List<UUID> attempts=new ArrayList<>();List<UUID> pdfs=new ArrayList<>();var d=new DetalleRecuentoCajaDialog(r,true,(id,key)->{attempts.add(key);return false;},pdfs::add);action(d,"Ver PDF").click();assertThat(pdfs).containsExactly(r.id());assertThat(attempts).isEmpty();action(d,"Imprimir recuento").click();action(d,"Imprimir recuento").click();assertThat(attempts).hasSize(2);assertThat(attempts.get(0)).isEqualTo(attempts.get(1));var other=new DetalleRecuentoCajaDialog(r,true,(id,key)->{attempts.add(key);return false;},pdfs::add);action(other,"Imprimir recuento").click();assertThat(attempts.get(2)).isNotEqualTo(attempts.get(0));}
    @Test void sinPermisoNoOfreceSalida(){var d=new DetalleRecuentoCajaDialog(fixture(),false,(id,key)->{throw new AssertionError();},id->{throw new AssertionError();});assertThat(d.getFooter().getElement().getChildren().map(element->element.getComponent().orElse(null)).filter(Button.class::isInstance).map(c->c.getElement().getAttribute("aria-label"))).doesNotContain("Imprimir recuento","Ver PDF");}
}
