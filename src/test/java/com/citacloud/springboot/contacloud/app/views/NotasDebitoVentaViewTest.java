package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.dto.*;import com.citacloud.springboot.contacloud.app.services.*;import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.vaadin.flow.component.*;import com.vaadin.flow.component.button.Button;import com.vaadin.flow.component.dialog.Dialog;import com.vaadin.flow.router.Route;
import java.util.*;import java.time.LocalDate;import java.math.BigDecimal;import java.util.stream.Stream;
import org.junit.jupiter.api.*;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class NotasDebitoVentaViewTest{
 @AfterEach void clean(){SecurityContextHolder.clearContext();}
 NotasDebitoVentaView view(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));return new NotasDebitoVentaView(mock(NotaDebitoVentaService.class),mock(NotaDebitoVentaPdfService.class),mock(EmpresaModuloService.class),mock(UltimasFacturasService.class));}
 Stream<Component> tree(Component c){return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::tree));}
 @Test void rutaRealYNuevaSegunPermiso(){assertThat(NotasDebitoVentaView.class.getAnnotation(Route.class).value()).isEqualTo("notas-debito");assertThat(PhaseTwoPlaceholderView.class.getAnnotation(Route.class).value()).isNotEqualTo("notas-debito");assertThat(tree(view(Set.of("notas_debito.ver"))).filter(Button.class::isInstance).map(Button.class::cast)).noneMatch(b->b.isVisible()&&"Nueva nota de débito".equals(b.getAriaLabel().orElse("")));assertThat(tree(view(Set.of("notas_debito.crear"))).filter(Button.class::isInstance).map(Button.class::cast)).anyMatch(b->b.isVisible()&&"Nueva nota de débito".equals(b.getAriaLabel().orElse("")));}
 @Test void origenCuatroColumnasYDatosFinancierosSeparados(){var p=new PreparacionNotaDebitoVentaDto(UUID.randomUUID(),"FAC-1",LocalDate.of(2026,11,8),"Cliente","USD",new BigDecimal("100"),new BigDecimal("40"),"Crédito","Emitida",List.of(),List.of());var section=NotasDebitoVentaView.facturaOrigen(p);assertThat(section.getClassNames()).contains("cc-detail-four");assertThat(tree(section).filter(c->c.hasClassName("cc-detail-label")).map(c->c.getElement().getText())).containsExactly("Factura","Fecha","Cliente","Moneda","Total factura","Saldo pendiente","Condición factura","Estado");}
 @Test void accionesSoloImprimirAnularCerrarSegunEstadoYPermisos(){var v=view(Set.of("notas_debito.ver","notas_debito.pdf","notas_debito.anular"));var d=mock(Dialog.class);var n=mock(NotaDebitoVentaDocumento.class);var issued=new NotaDebitoVentaCicloDto("CONFIRMED",0,true,null,null,null);assertThat(v.accionesDetalle(d,n,issued)).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Imprimir nota de débito","Anular nota de débito","Cerrar");assertThat(v.accionesDetalle(d,n,new NotaDebitoVentaCicloDto("VOIDED",1,true,"Motivo","Admin",null))).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Imprimir nota de débito","Cerrar");assertThat(view(Set.of("notas_debito.ver")).accionesDetalle(d,n,issued)).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Cerrar");}
 @Test void listadoSoloVerSinPdfNiImprimir(){var v=view(Set.of("notas_debito.ver"));var r=new com.citacloud.springboot.contacloud.app.repositories.NotaDebitoVentaRepository.Resumen(UUID.randomUUID(),"NDV-1",LocalDate.now(),"FAC-1","Cliente","USD",BigDecimal.TEN,"CONFIRMED");assertThat(((Button)v.acciones(r)).getAriaLabel()).contains("Ver nota de débito");assertThat(((Button)v.acciones(r)).getText()).isEmpty();}

 @Test void emisionAbrePdfSoloDespuesDelServicioYDelCierre(){
  var v=spy(view(Set.of("notas_debito.crear","notas_debito.emitir","notas_debito.pdf")));
  var service=(NotaDebitoVentaService)org.springframework.test.util.ReflectionTestUtils.getField(v,"service");
  var n=mock(NotaDebitoVentaDocumento.class);UUID noteId=UUID.randomUUID();when(n.id()).thenReturn(noteId);var request=mock(EmitirNotaDebitoVentaInput.class);when(service.emitir(request)).thenReturn(n);
  doNothing().when(v).mostrarEmitida(n);doNothing().when(v).abrirPdf(noteId);Runnable close=mock(Runnable.class);
  v.emitirYMostrar(request,close);var order=inOrder(service,close,v);order.verify(service).emitir(request);order.verify(close).run();order.verify(v).mostrarEmitida(n);order.verify(v).abrirPdf(noteId);verify(service,times(1)).emitir(request);
 }
 @Test void falloPdfNoReemiteYReintentoSoloUsaIdExistente(){
  var v=spy(view(Set.of("notas_debito.pdf")));var service=(NotaDebitoVentaService)org.springframework.test.util.ReflectionTestUtils.getField(v,"service");
  var n=mock(NotaDebitoVentaDocumento.class);UUID noteId=UUID.randomUUID();when(n.id()).thenReturn(noteId);var request=mock(EmitirNotaDebitoVentaInput.class);when(service.emitir(request)).thenReturn(n);
  doNothing().when(v).mostrarEmitida(n);RuntimeException failure=new ReglaNegocioException("Fallo PDF");doThrow(failure).doNothing().when(v).abrirPdf(noteId);doNothing().when(v).informarFalloPdf(n,failure);
  assertThat(v.emitirYMostrar(request,()->{})).isSameAs(n);verify(v).informarFalloPdf(n,failure);
  v.abrirPdfEmitida(n);verify(v,times(2)).abrirPdf(noteId);verify(service,times(1)).emitir(request);verifyNoMoreInteractions(service);
 }
 @Test void emisionFallidaNoAbrePdfNiCierraFormulario(){
  var v=spy(view(Set.of("notas_debito.pdf")));var service=(NotaDebitoVentaService)org.springframework.test.util.ReflectionTestUtils.getField(v,"service");var request=mock(EmitirNotaDebitoVentaInput.class);when(service.emitir(request)).thenThrow(new ReglaNegocioException("Rechazada"));Runnable close=mock(Runnable.class);
  assertThatThrownBy(()->v.emitirYMostrar(request,close)).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(close);verify(v,never()).abrirPdf(any());verify(v,never()).mostrarEmitida(any());
 }
 @Test void aperturaAutomaticaRespetaPermisoPdf(){
  var v=spy(view(Set.of("notas_debito.emitir")));var service=(NotaDebitoVentaService)org.springframework.test.util.ReflectionTestUtils.getField(v,"service");var n=mock(NotaDebitoVentaDocumento.class);var request=mock(EmitirNotaDebitoVentaInput.class);when(service.emitir(request)).thenReturn(n);doNothing().when(v).mostrarEmitida(n);
  v.emitirYMostrar(request,()->{});verify(v,never()).abrirPdf(any());
 }
 @Test void avisoPdfIndicaEmisionExitosaYReintentoSinDatosTecnicos(){var n=mock(NotaDebitoVentaDocumento.class);when(n.numero()).thenReturn("NDV-000006");assertThat(NotasDebitoVentaView.mensajeFalloPdf(n)).isEqualTo("La nota de débito NDV-000006 fue emitida correctamente, pero no fue posible abrir el PDF. Puede reintentarlo desde Imprimir.");}

 @Test void datosDeLaNotaAgrupanFechaMotivoYCondicion(){
  var date=new com.vaadin.flow.component.datepicker.DatePicker("Fecha");var reason=new com.vaadin.flow.component.combobox.ComboBox<PreparacionNotaDebitoVentaDto.Opcion>("Motivo");var condition=new com.vaadin.flow.component.select.Select<com.citacloud.springboot.contacloud.app.models.TipoCondicionPago>();condition.setLabel("Condición de la nota");var row=NotasDebitoVentaView.datosNota(date,reason,condition);assertThat(row.getClassNames()).contains("cc-debit-note-data");assertThat(row.getChildren()).containsExactly(date,reason,condition);
 }
 @Test void totalesCompactosUsanMonedaYOmitenDescuentoVacio(){
  var n=mock(NotaDebitoVentaDocumento.class);when(n.moneda()).thenReturn("USD");when(n.subtotal()).thenReturn(new BigDecimal("1000"));when(n.impuesto()).thenReturn(new BigDecimal("180"));when(n.total()).thenReturn(new BigDecimal("1180"));when(n.descuento()).thenReturn(BigDecimal.ZERO);
  var totals=NotasDebitoVentaView.resumenFormulario(n);assertThat(totals.getClassNames()).contains("cc-debit-note-totals");assertThat(tree(totals).map(c->c.getElement().getText())).contains("Subtotal","Impuestos","TOTAL","USD 1,180.00").doesNotContain("Descuento");when(n.descuento()).thenReturn(BigDecimal.TEN);assertThat(tree(NotasDebitoVentaView.resumenFormulario(n)).map(c->c.getElement().getText())).contains("Descuento");
 }
}
