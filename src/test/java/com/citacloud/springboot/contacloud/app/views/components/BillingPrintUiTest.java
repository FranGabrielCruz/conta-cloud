package com.citacloud.springboot.contacloud.app.views.components;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.component.UI;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.*;
class BillingPrintUiTest {
 final UI ui=new UI();final BillingPrintApplicationService service=mock(BillingPrintApplicationService.class);final BillingPrintUi actions=spy(new BillingPrintUi(service));
 final UUID id=UUID.randomUUID(),attempt=UUID.randomUUID();final ArchivoPdfDto file=new ArchivoPdfDto("screen.pdf",new byte[]{1});
 @BeforeEach void setup(){UI.setCurrent(ui);doNothing().when(actions).mostrarPdf(any());}
 @AfterEach void clear(){UI.setCurrent(null);}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void escenarioPantalla(TipoDocumentoImpresion type){when(service.imprimir(type,id,attempt)).thenReturn(new BillingPrintResult(new SolicitudImpresionResultadoDto(null,true),file));actions.imprimir(type,id,attempt);verify(actions).mostrarPdf(file);}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void escenarioImpresoraSinAperturaPdf(TipoDocumentoImpresion type){when(service.imprimir(type,id,attempt)).thenReturn(new BillingPrintResult(new SolicitudImpresionResultadoDto(UUID.randomUUID(),false),null));actions.imprimir(type,id,attempt);verify(actions,never()).mostrarPdf(any());}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void errorNuncaSeConvierteEnPantalla(TipoDocumentoImpresion type){when(service.imprimir(type,id,attempt)).thenThrow(new ReglaNegocioException("No hay agente configurado para esta caja."));actions.imprimir(type,id,attempt);verify(actions,never()).mostrarPdf(any());}
}
