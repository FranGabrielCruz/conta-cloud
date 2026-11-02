package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.PosPaymentDialog;
import com.vaadin.flow.component.UI;
import java.math.BigDecimal;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacturacionDobleEnterIntegrationTest {
    final UI ui=new UI();final FacturacionPosService service=mock(FacturacionPosService.class);
    FacturacionPosView view;TerminalPosDto terminal;MetodoPagoPosDto cash;
    @BeforeEach void setup()throws Exception{
        UI.setCurrent(ui);auth(Set.of("facturas.cobrar"));
        var ctor=FacturacionPosView.class.getConstructors()[0];Object[] args=Arrays.stream(ctor.getParameterTypes()).map(t->t==FacturacionPosService.class?service:mock(t)).toArray();view=(FacturacionPosView)ctor.newInstance(args);
        terminal=mock(TerminalPosDto.class);when(terminal.cajaAbierta()).thenReturn(true);when(terminal.monedaCodigo()).thenReturn("DOP");when(terminal.almacenId()).thenReturn(UUID.randomUUID());ReflectionTestUtils.setField(view,"terminal",terminal);
        cash=new MetodoPagoPosDto(UUID.randomUUID(),"CASH","Efectivo",TipoMetodoPagoVenta.CASH,false,false,true,true,false,null,true);
        when(service.metodosPago()).thenReturn(List.of(cash));
    }
    @AfterEach void cleanup(){var d=(PosPaymentDialog)ReflectionTestUtils.getField(view,"dialogoCobroActivo");if(d!=null)d.close();UI.setCurrent(null);SecurityContextHolder.clearContext();}
    void auth(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    @SuppressWarnings({"rawtypes","unchecked"}) void linea()throws Exception{
        var product=new ProductoPosDto(UUID.randomUUID(),"TEST","Prueba",null,"UND",new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("10"),false,false);
        var type=Class.forName(FacturacionPosView.class.getName()+"$Fila");var ctor=type.getDeclaredConstructor(ProductoPosDto.class);ctor.setAccessible(true);((List)ReflectionTestUtils.getField(view,"filas")).add(ctor.newInstance(product));
    }
    void cobrar(){ReflectionTestUtils.invokeMethod(view,"dialogoCobro");}
    @Test void facturaValidaAbreUnaVezYCerrarPermiteReabrir()throws Exception{
        linea();cobrar();var first=(PosPaymentDialog)ReflectionTestUtils.getField(view,"dialogoCobroActivo");assertThat(first).isNotNull();assertThat(first.isOpened()).isTrue();
        cobrar();assertThat(ReflectionTestUtils.getField(view,"dialogoCobroActivo")).isSameAs(first);verify(service,times(1)).metodosPago();
        first.close();cobrar();assertThat(ReflectionTestUtils.getField(view,"dialogoCobroActivo")).isNotSameAs(first);verify(service,times(2)).metodosPago();
    }
    @Test void facturaVaciaNoPreparaCobro(){cobrar();verifyNoInteractions(service);assertThat(ReflectionTestUtils.getField(view,"dialogoCobroActivo")).isNull();}
    @Test void cajaCerradaNoPreparaCobro()throws Exception{linea();when(terminal.cajaAbierta()).thenReturn(false);cobrar();verifyNoInteractions(service);}
    @Test void usuarioSinPermisoNoPreparaCobro()throws Exception{linea();auth(Set.of());cobrar();verifyNoInteractions(service);}
    @Test void errorNoDejaLaAperturaBloqueada()throws Exception{linea();when(service.metodosPago()).thenThrow(new ReglaNegocioException("No disponible")).thenReturn(List.of(cash));cobrar();assertThat(ReflectionTestUtils.getField(view,"preparandoCobro")).isEqualTo(false);cobrar();assertThat(ReflectionTestUtils.getField(view,"dialogoCobroActivo")).isNotNull();}
    @Test void tecladoBotonYF9CompartenUnaAccionSinDetectorServidorDuplicado()throws Exception{
        String source=Files.readString(Path.of("src/main/java/com/citacloud/springboot/contacloud/app/views/FacturacionPosView.java"));
        assertThat(source).contains("cc-pos-open-payment\", event -> dialogoCobro()","this::dialogoCobro, Key.F9","new PosChargeButton(e -> dialogoCobro())","__ccBillingKeyboard?.dispose()");
        assertThat(source).doesNotContain("doubleEnter.register","__ccPosEnterHandler","__ccPosLastEnter");
    }
}
