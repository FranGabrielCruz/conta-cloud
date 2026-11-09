package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ReciboHistorialDto;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument;
import com.citacloud.springboot.contacloud.app.services.ReciboHistorialService;
import org.springframework.data.domain.PageImpl;
import java.util.stream.Stream;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.*;
import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.CobroClienteDto;
import com.citacloud.springboot.contacloud.app.services.CobroClientePdfService;
import com.citacloud.springboot.contacloud.app.services.CobroClienteService;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RecibosVentaViewTest {
    private UI ui;
    @AfterEach void limpiarSesion(){
        SecurityContextHolder.clearContext();
        VaadinSession.setCurrent(null);
        UI.setCurrent(null);
    }

    @Test void tablaSoloOfreceVerEnAmbosEstados() {
        autenticar("recibos.ver","recibos.imprimir","recibos.anular");
        var vista=spy(new RecibosVentaView(null,null,null,null));
        for(var estado:List.of(EstadoReciboCliente.APPLIED,EstadoReciboCliente.VOIDED)){
            var fila=recibo(estado);
            doNothing().when(vista).abrir(fila.id());
            var acciones=botones(vista.acciones(fila));
            assertThat(acciones).extracting(RecibosVentaViewTest::nombre).containsExactly("Ver recibo");
            var ver=acciones.getFirst();
            assertThat(ver.getText()).isEmpty();
            assertThat(ver.getTooltip().getText()).isEqualTo("Ver recibo");
            assertThat(ver.getClassNames()).contains("cc-action-grid");
            ver.click();
            verify(vista).abrir(fila.id());
        }
    }

    @Test void detalleOrdenaLosBotonesYAplicaPermisosYEstado() {
        var vista=new RecibosVentaView(null,null,null,null);
        autenticar("recibos.ver","recibos.imprimir","recibos.anular");
        assertThat(pie(vista.crearDetalle(detalle(recibo(EstadoReciboCliente.APPLIED),true))))
            .extracting(RecibosVentaViewTest::nombre).containsExactly("Imprimir recibo","Anular recibo","Cerrar");
        assertThat(pie(vista.crearDetalle(detalle(recibo(EstadoReciboCliente.VOIDED),true))))
            .extracting(RecibosVentaViewTest::nombre).containsExactly("Imprimir recibo","Cerrar");
        assertThat(pie(vista.crearDetalle(detalle(recibo(EstadoReciboCliente.APPLIED),false))))
            .extracting(RecibosVentaViewTest::nombre).containsExactly("Imprimir recibo","Cerrar");
        autenticar("recibos.ver","recibos.imprimir");
        assertThat(pie(vista.crearDetalle(detalle(recibo(EstadoReciboCliente.APPLIED),true))))
            .extracting(RecibosVentaViewTest::nombre).containsExactly("Imprimir recibo","Cerrar");
    }

    @Test void confirmarActualizaElMismoDetalleAbierto() {
        ui=new UI();
        UI.setCurrent(ui);
        autenticar("recibos.ver","recibos.imprimir","recibos.anular");
        var historial=mock(ReciboHistorialService.class);
        var cobros=mock(CobroClienteService.class);
        var vista=spy(new RecibosVentaView(historial,cobros,null,null));
        var emitido=recibo(EstadoReciboCliente.APPLIED);
        var filaAnulada=new ReciboHistorialDto.Resumen(emitido.id(),emitido.numero(),emitido.fecha(),
            emitido.cliente(),emitido.moneda(),emitido.total(),EstadoReciboCliente.VOIDED,emitido.documentos());
        var anulado=detalle(filaAnulada,false);
        when(historial.buscar(eq(""),isNull(),isNull(),isNull(),eq(0),eq(10))).thenReturn(new PageImpl<>(List.of(filaAnulada)));
        when(historial.detalle(emitido.id())).thenReturn(anulado);
        when(cobros.anularConResultado(emitido.id(),"Cobro incorrecto"))
            .thenReturn(new CobroClienteService.ResultadoAnulacion(anulado.recibo(),false));

        var confirmacionAbierta=new java.util.concurrent.atomic.AtomicReference<Dialog>();
        doAnswer(invocacion->{
            var confirmacion=(Dialog)invocacion.callRealMethod();
            confirmacionAbierta.set(confirmacion);
            return confirmacion;
        }).when(vista).confirmarAnulacion(eq(emitido.id()),eq(emitido.numero()),any());
        var detalle=vista.crearDetalle(detalle(emitido,true));
        detalle.open();
        pie(detalle).get(1).click();
        var confirmacion=confirmacionAbierta.get();
        assertThat(confirmacion.isOpened()).isTrue();
        confirmacion.getChildren().filter(TextArea.class::isInstance).map(TextArea.class::cast)
            .findFirst().orElseThrow().setValue("Cobro incorrecto");
        pie(confirmacion).getFirst().click();

        assertThat(detalle.isOpened()).isTrue();
        assertThat(confirmacion.isOpened()).isFalse();
        assertThat(pie(detalle)).extracting(RecibosVentaViewTest::nombre).containsExactly("Imprimir recibo","Cerrar");
        assertThat(descendientes(detalle).map(c->c.getElement().getText()))
            .contains("Anulado","ANULACIÓN","Cobro incorrecto","DOP 10.00");
        verify(historial).buscar("",null,null,null,0,10);
    }

    @Test void imprimirAbreElPdfDelReciboEnUnaPestanaNueva() {
        autenticar("recibos.ver","recibos.imprimir");
        var ui=mock(UI.class);
        var pagina=mock(Page.class);
        when(ui.getPage()).thenReturn(pagina);
        UI.setCurrent(ui);
        var sesion=mock(VaadinSession.class);
        var recursos=mock(StreamResourceRegistry.class);
        var registro=mock(StreamRegistration.class);
        VaadinSession.setCurrent(sesion);
        when(sesion.getResourceRegistry()).thenReturn(recursos);
        when(recursos.registerResource(any(AbstractStreamResource.class))).thenReturn(registro);
        when(registro.getResourceUri()).thenReturn(URI.create("VAADIN/dynamic/REC-000006.pdf"));
        var pdf=mock(CobroClientePdfService.class);
        var cobros=mock(CobroClienteService.class);
        var vista=new RecibosVentaView(null,cobros,pdf,null);
        var fila=recibo(EstadoReciboCliente.VOIDED);
        when(pdf.generar(fila.id())).thenReturn(new ArchivoPdfDto("REC-000006.pdf",new byte[]{1,2,3}));
        pie(vista.crearDetalle(detalle(fila,false))).getFirst().click();
        verify(pdf).generar(fila.id());
        verify(pagina).open("VAADIN/dynamic/REC-000006.pdf","_blank");
        verifyNoInteractions(cobros);
    }

    @Test void unErrorDeGeneracionNoAbreUnaPestana() {
        ui=new UI();
        UI.setCurrent(ui);
        var sesion=mock(VaadinSession.class);
        var recursos=mock(StreamResourceRegistry.class);
        VaadinSession.setCurrent(sesion);
        when(sesion.getResourceRegistry()).thenReturn(recursos);
        var pdf=mock(CobroClientePdfService.class);
        var cobros=mock(CobroClienteService.class);
        var vista=new RecibosVentaView(null,cobros,pdf,null);
        var fila=recibo(EstadoReciboCliente.APPLIED);
        when(pdf.generar(fila.id())).thenThrow(new com.citacloud.springboot.contacloud.app.services.ReglaNegocioException("No fue posible generar el PDF del recibo."));
        vista.imprimir(fila.id());
        verifyNoInteractions(recursos,cobros);
    }

    @Test void confirmacionRechazaMotivoVacioYEnviaMotivoRecortado() {
        ui=new UI();
        UI.setCurrent(ui);
        var cobros=mock(CobroClienteService.class);
        var vista=new RecibosVentaView(null,cobros,null,null);
        var fila=recibo(EstadoReciboCliente.APPLIED);
        var refrescar=mock(Runnable.class);
        var dialogo=vista.confirmarAnulacion(fila.id(),fila.numero(),refrescar);
        var motivo=dialogo.getChildren().filter(TextArea.class::isInstance).map(TextArea.class::cast).findFirst().orElseThrow();
        var acciones=pie(dialogo);
        acciones.getFirst().click();
        motivo.setValue("   ");
        acciones.getFirst().click();
        assertThat(motivo.isInvalid()).isTrue();
        acciones.getLast().click();
        verifyNoInteractions(cobros,refrescar);

        var reciboAnulado=mock(CobroClienteDto.class);
        when(reciboAnulado.numero()).thenReturn(fila.numero());
        when(cobros.anularConResultado(fila.id(),"Motivo de prueba"))
            .thenReturn(new CobroClienteService.ResultadoAnulacion(reciboAnulado,false));
        motivo.setValue("  Motivo de prueba  ");
        acciones.getFirst().click();
        verify(cobros).anularConResultado(fila.id(),"Motivo de prueba");
        verify(refrescar).run();
    }

    private static List<AppActionButton> botones(Component acciones){
        return acciones.getChildren().map(AppActionButton.class::cast).toList();
    }
    private static String nombre(AppActionButton boton){return boton.getAriaLabel().orElseThrow();}
    private static List<AppActionButton> pie(Dialog dialogo){
        return dialogo.getFooter().getElement().getChildren().flatMap(e->e.getComponent().stream())
            .map(AppActionButton.class::cast).toList();
    }
    private static Stream<Component> descendientes(Component componente){
        return Stream.concat(Stream.of(componente),componente.getChildren().flatMap(RecibosVentaViewTest::descendientes));
    }
    private static ReciboHistorialDto.Resumen recibo(EstadoReciboCliente estado){
        return new ReciboHistorialDto.Resumen(UUID.randomUUID(),"REC-000006",LocalDateTime.now(),
            "Cliente de prueba","DOP",BigDecimal.TEN,estado,List.of("FAC-000001"));
    }
    private static ReciboHistorialDto detalle(ReciboHistorialDto.Resumen fila,boolean anulable){
        var recibo=mock(CobroClienteDto.class);
        when(recibo.id()).thenReturn(fila.id());
        when(recibo.numero()).thenReturn(fila.numero());
        when(recibo.moneda()).thenReturn(fila.moneda());
        when(recibo.estado()).thenReturn(fila.estado());
        when(recibo.aplicado()).thenReturn(fila.total());
        when(recibo.disponible()).thenReturn(BigDecimal.ZERO);
        var documento=mock(PaymentReceiptPrintDocument.class);
        when(documento.receiptDateTime()).thenReturn(fila.fecha());
        when(documento.customer()).thenReturn(new PaymentReceiptPrintDocument.CustomerSnapshot(fila.cliente(),null));
        when(documento.totalCollected()).thenReturn(fila.total());
        when(documento.paymentMethods()).thenReturn(List.of(new PaymentReceiptPrintDocument.PaymentMethod("Efectivo",fila.total(),null)));
        if(fila.estado()==EstadoReciboCliente.VOIDED){
            when(recibo.motivoAnulacion()).thenReturn("Cobro incorrecto");
            return new ReciboHistorialDto(recibo,documento,fila.fecha(),"admin",anulable);
        }
        return new ReciboHistorialDto(recibo,documento,null,null,anulable);
    }
    private static void autenticar(String... permisos){
        var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","prueba","",true,Set.of(permisos));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
