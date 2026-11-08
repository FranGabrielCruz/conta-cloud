package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.router.Route;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotasCreditoVentaViewTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    NotasCreditoVentaView view(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","prueba","",true,permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));return new NotasCreditoVentaView(mock(NotaCreditoVentaService.class),mock(NotaCreditoVentaPdfService.class),mock(EmpresaModuloService.class));}
    Stream<Component> tree(Component c){return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::tree));}
    @Test void rutaRealSustituyePlaceholderSinAccionEditar(){
        assertThat(NotasCreditoVentaView.class.getAnnotation(Route.class).value()).isEqualTo("notas-credito");assertThat(PhaseTwoPlaceholderView.class.getAnnotation(Route.class).value()).isNotEqualTo("notas-credito");
        var v=view(Set.of("notas_credito.ver","notas_credito.crear"));var buttons=tree(v).filter(Button.class::isInstance).map(Button.class::cast).toList();
        assertThat(buttons).anyMatch(b->b.isVisible()&&b.getAriaLabel().orElse("").equals("Nueva nota de crédito"));assertThat(buttons).noneMatch(b->b.getAriaLabel().orElse("").contains("Editar"));
        assertThat(tree(v).filter(DatePicker.class::isInstance).map(DatePicker.class::cast)).allMatch(d->Locale.forLanguageTag("es-DO").equals(d.getLocale()));
    }
    @Test void usuarioDeConsultaNoVeNuevaNota(){var v=view(Set.of("notas_credito.ver"));assertThat(tree(v).filter(Button.class::isInstance).map(Button.class::cast)).noneMatch(b->b.isVisible()&&b.getAriaLabel().orElse("").equals("Nueva nota de crédito"));}
    @Test void selectorRespetaContratoVaadinYTamanosDePaginaSolicitados(){
        view(Set.of("notas_credito.crear"));var service=mock(NotaCreditoVentaService.class);
        var v=new NotasCreditoVentaView(service,mock(NotaCreditoVentaPdfService.class),mock(EmpresaModuloService.class));
        var selector=v.crearSelectorFactura();
        @SuppressWarnings("unchecked") var provider=(com.vaadin.flow.data.provider.DataProvider<com.citacloud.springboot.contacloud.app.repositories.NotaCreditoVentaConsultaRepository.FacturaOpcion,String>)selector.getDataProvider();
        for(int size:List.of(25,50)){
            when(service.facturas("FAC",1,size)).thenReturn(org.springframework.data.domain.Page.empty());
            var query=spy(new com.vaadin.flow.data.provider.Query<com.citacloud.springboot.contacloud.app.repositories.NotaCreditoVentaConsultaRepository.FacturaOpcion,String>(size,size,List.of(),null,"FAC"));
            assertThat(provider.fetch(query).toList()).isEmpty();
            verify(query,atLeastOnce()).getPageSize();verify(service).facturas("FAC",1,size);
        }
    }
    @Test void bloqueOrigenTieneOchoCamposEnOrdenYCuatroColumnas(){
        var p=new com.citacloud.springboot.contacloud.app.dto.PreparacionNotaCreditoVentaDto(UUID.randomUUID(),"FAC-TEST",java.time.LocalDate.of(2026,11,8),null,null,"DOP",new java.math.BigDecimal("500"),new java.math.BigDecimal("100"),new java.math.BigDecimal("400"),List.of(),List.of(),"Contado");
        var section=NotasCreditoVentaView.facturaOrigen(p);assertThat(section.getClassNames()).contains("cc-detail-four");
        var labels=tree(section).filter(c->c.hasClassName("cc-detail-label")).map(c->c.getElement().getText()).toList();assertThat(labels).containsExactly("Factura","Fecha","Cliente","Moneda","Total factura","Total acreditado","Disponible para acreditar","Condición");
        assertThat(tree(section).map(c->c.getElement().getText())).contains("Consumidor Final","Contado","DOP 400.00");
    }
    @Test void imprimirReutilizaAccionPrincipalGlobalConIconoPrinter(){
        var button=view(Set.of("notas_credito.ver","notas_credito.pdf")).botonImprimir(UUID.randomUUID());
        assertThat(button.getClassNames()).contains("cc-action-main","cc-action-view");assertThat(button.getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:print");assertThat(button.getText()).isEmpty();assertThat(button.getAriaLabel()).contains("Imprimir nota de crédito");
    }
    @Test void cancelarSinCambiosCierraYDescartarRequiereConfirmacion(){
        var form=mock(com.vaadin.flow.component.dialog.Dialog.class);NotasCreditoVentaView.cancelar(form,false);verify(form).close();clearInvocations(form);
        var confirm=NotasCreditoVentaView.confirmacionDescartar(form);verifyNoInteractions(form);
        com.vaadin.flow.component.ComponentUtil.fireEvent(confirm,new com.vaadin.flow.component.confirmdialog.ConfirmDialog.ConfirmEvent(confirm,true));verify(form).close();
    }

    @Test void listadoSoloOfreceVerInclusoSiTienePermisoPdf(){
        var v=view(Set.of("notas_credito.ver","notas_credito.pdf"));var row=new com.citacloud.springboot.contacloud.app.repositories.NotaCreditoVentaConsultaRepository.Resumen(UUID.randomUUID(),"NC-1",java.time.LocalDate.now(),"Consumidor Final","FAC-1",null,"DOP",java.math.BigDecimal.TEN,"VOIDED");
        var action=v.acciones(row);assertThat(action).isInstanceOf(Button.class);assertThat(((Button)action).getAriaLabel()).contains("Ver nota de crédito");assertThat(action.getChildren().filter(Button.class::isInstance)).isEmpty();assertThat(NotasCreditoVentaView.estadoNota(row.estado())).isEqualTo("Anulada");
    }

    @Test void detalleTieneDoceCamposEnCuatroColumnasYSeparaOrigen(){
        var d=mock(com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaDocumento.class);when(d.fecha()).thenReturn(java.time.LocalDate.now());when(d.fechaFactura()).thenReturn(java.time.LocalDate.now());when(d.factura()).thenReturn("FAC-1");when(d.moneda()).thenReturn("DOP");
        var direct=new com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaCicloDto(UUID.randomUUID(),null,null,0,"CONFIRMED",true,null,null,null);var section=NotasCreditoVentaView.informacionNota(d,direct);
        assertThat(section.getClassNames()).contains("cc-detail-four");assertThat(tree(section).filter(c->c.hasClassName("cc-detail-label")).map(c->c.getElement().getText())).containsExactly("Fecha","Estado","Cliente","RNC/Cédula","Factura","Fecha factura","NCF afectado","NCF de la nota","Moneda","Origen","Documento origen","Estado origen");assertThat(tree(section).map(c->c.getElement().getText())).contains("Consumidor Final","Crédito financiero","Emitida","—");
        when(d.devolucion()).thenReturn("DEV-1");var returned=new com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaCicloDto(direct.facturaId(),UUID.randomUUID(),"CONFIRMED",1,"VOIDED",false,"Error","Operador",java.time.LocalDateTime.now());assertThat(tree(NotasCreditoVentaView.informacionNota(d,returned)).map(c->c.getElement().getText())).contains("Devolución","DEV-1","Confirmada","Anulada");
    }

    @Test void detalleOfreceSoloImprimirAnularYCerrarSegunEstado(){
        var v=view(Set.of("notas_credito.ver","notas_credito.pdf","notas_credito.anular"));
        var note=mock(com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaDocumento.class);when(note.id()).thenReturn(UUID.randomUUID());
        var dialog=mock(com.vaadin.flow.component.dialog.Dialog.class);
        var issued=new com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaCicloDto(UUID.randomUUID(),null,null,0,"CONFIRMED",true,null,null,null);
        var buttons=v.accionesDetalle(dialog,note,issued);
        assertThat(buttons).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Imprimir nota de crédito","Anular nota de crédito","Cerrar");
        assertThat(buttons).allMatch(b->b.getText().isEmpty()&&b.hasClassName("cc-action-main"));
        assertThat(buttons.get(0).getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:print");
        assertThat(buttons.get(1).getClassNames()).contains("cc-action-danger");assertThat(buttons.getLast().getClassNames()).contains("cc-action-neutral");
        var voided=new com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaCicloDto(issued.facturaId(),null,null,1,"VOIDED",true,"Error","Operador",java.time.LocalDateTime.now());
        var history=v.accionesDetalle(dialog,note,voided);assertThat(history).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Imprimir nota de crédito","Cerrar");
        history.getLast().click();verify(dialog).close();
    }
    @Test void detalleRespetaPermisosYElegibilidadSinAccionPdfRedundante(){
        var note=mock(com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaDocumento.class);var dialog=mock(com.vaadin.flow.component.dialog.Dialog.class);
        var issued=new com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaCicloDto(UUID.randomUUID(),null,null,0,"CONFIRMED",true,null,null,null);
        assertThat(view(Set.of("notas_credito.ver")).accionesDetalle(dialog,note,issued)).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Cerrar");
        var used=new com.citacloud.springboot.contacloud.app.dto.NotaCreditoVentaCicloDto(issued.facturaId(),null,null,0,"CONFIRMED",false,null,null,null);
        assertThat(view(Set.of("notas_credito.pdf","notas_credito.anular")).accionesDetalle(dialog,note,used)).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Imprimir nota de crédito","Cerrar");
        verifyNoInteractions(dialog);
    }
}
