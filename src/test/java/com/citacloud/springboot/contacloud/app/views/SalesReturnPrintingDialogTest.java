package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.services.DevolucionVentaService;
import com.citacloud.springboot.contacloud.app.views.components.DevolucionesVentasDialog;
import com.citacloud.springboot.contacloud.app.security.*;
import com.vaadin.flow.component.button.Button;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SalesReturnPrintingDialogTest {
    final DevolucionVentaService service=mock(DevolucionVentaService.class);
    @SuppressWarnings("unchecked") final Consumer<UUID> pdf=mock(Consumer.class);
    @SuppressWarnings("unchecked") final BiFunction<UUID,UUID,Boolean> print=mock(BiFunction.class);
    @BeforeEach void setup(){
        auth(Set.of("devoluciones_venta.ver","devoluciones_venta.pdf"));
        when(service.buscar(anyString(),any(),any(),any(),anyInt())).thenReturn(Page.empty());
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private void auth(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    private DevolucionesVentasDialog dialog(){return new DevolucionesVentasDialog(service,pdf,()->{},print,"terminal-test");}
    @Test void unacknowledgedRetryKeepsIntentAndAnotherButtonUsesNewKey(){
        var dialog=dialog();UUID id=UUID.randomUUID();Button button=ReflectionTestUtils.invokeMethod(dialog,"botonImprimir",id);assertNotNull(button);
        button.click();button.click();verify(print,times(2)).apply(eq(id),any());assertTrue(button.isEnabled());
        Button reprint=ReflectionTestUtils.invokeMethod(dialog,"botonImprimir",id);assertNotNull(reprint);reprint.click();
        var keys=ArgumentCaptor.forClass(UUID.class);verify(print,times(3)).apply(eq(id),keys.capture());assertEquals(keys.getAllValues().get(0),keys.getAllValues().get(1));assertNotEquals(keys.getAllValues().get(0),keys.getAllValues().get(2));
        verify(service,never()).confirmar(any());verifyNoInteractions(pdf);
    }
    @Test void confirmationResultDoesNotAutomaticallyPrintOrOpenAnInvoicePdf(){
        var dialog=dialog();var detail=mock(DevolucionVentaDetalleDto.class);when(detail.facturaId()).thenReturn(UUID.randomUUID());ReflectionTestUtils.setField(dialog,"detalle",detail);
        ReflectionTestUtils.invokeMethod(dialog,"mostrarConfirmada",new DevolucionVentaResultadoDto(UUID.randomUUID(),"DEV-1","NCV-1",BigDecimal.ONE,"DOP"));
        verifyNoInteractions(print,pdf);verify(service,never()).confirmar(any());
    }
    @Test void printPermissionIsRequiredAndManualPdfRemainsIndependent(){
        var dialog=dialog();UUID id=UUID.randomUUID();Button pdfButton=ReflectionTestUtils.invokeMethod(dialog,"botonPdf",id);assertNotNull(pdfButton);pdfButton.click();verify(pdf).accept(id);verifyNoInteractions(print);
        auth(Set.of("devoluciones_venta.ver"));Button printButton=ReflectionTestUtils.invokeMethod(dialog,"botonImprimir",id);assertNotNull(printButton);assertFalse(printButton.isVisible());printButton.click();verifyNoInteractions(print);
    }
}
