package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.component.UI;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class FacturacionSalidaTest {
    final UI ui=new UI();
    final BillingPrintService printing=mock(BillingPrintService.class);
    final FacturaVentaPdfService documentos=mock(FacturaVentaPdfService.class);
    final FacturacionPosView view=mock(FacturacionPosView.class,CALLS_REAL_METHODS);
    final UUID id=UUID.randomUUID(),intento=UUID.randomUUID();
    @BeforeEach void preparar(){
        UI.setCurrent(ui);
        ReflectionTestUtils.setField(view,"printing",printing);
        ReflectionTestUtils.setField(view,"documentos",documentos);
        when(documentos.generar(id)).thenReturn(new ArchivoPdfDto("normal.pdf",new byte[]{1}));
    }
    @AfterEach void limpiar(){UI.setCurrent(null);}
    @Test void modoPdfUtilizaGeneradorNormalDeFacturacion(){
        when(printing.solicitar(id,intento)).thenReturn(new SolicitudImpresionResultadoDto(null,true));
        ReflectionTestUtils.invokeMethod(view,"imprimirFactura",id,intento);
        verify(documentos).generar(id);verify(documentos,never()).generarParaImpresion(any());
        verify(printing).solicitar(id,intento);verifyNoMoreInteractions(printing);
    }
    @Test void modoImpresoraNoAbreDocumento(){
        when(printing.solicitar(id,intento)).thenReturn(new SolicitudImpresionResultadoDto(UUID.randomUUID(),false));
        ReflectionTestUtils.invokeMethod(view,"imprimirFactura",id,intento);
        verifyNoInteractions(documentos);
    }
    @Test void falloNoAbrePdfComoFallback(){
        when(printing.solicitar(id,intento)).thenThrow(new ReglaNegocioException("No disponible"));
        ReflectionTestUtils.invokeMethod(view,"imprimirFactura",id,intento);
        verifyNoInteractions(documentos);
    }
    @Test void verPdfManualEsIndependienteDeConfiguracionYCola(){
        ReflectionTestUtils.invokeMethod(view,"abrirPdf",id);
        verify(documentos).generar(id);verifyNoInteractions(printing);
    }
}
