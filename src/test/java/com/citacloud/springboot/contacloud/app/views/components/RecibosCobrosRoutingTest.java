package com.citacloud.springboot.contacloud.app.views.components;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.vaadin.flow.component.UI;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecibosCobrosRoutingTest {
 final UI ui=new UI();final CobroClienteService service=mock(CobroClienteService.class);
 @SuppressWarnings("unchecked") final BiFunction<UUID,UUID,Boolean> print=mock(BiFunction.class);
 @SuppressWarnings("unchecked") final Consumer<UUID> pdf=mock(Consumer.class);
 @BeforeEach void setup(){
  UI.setCurrent(ui);var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,Set.of("recibos.ver","recibos.imprimir"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
 }
 @AfterEach void clear(){UI.setCurrent(null);SecurityContextHolder.clearContext();}
 @Test void imprimirTieneProteccionClienteYReutilizaIntentoEnReintento(){
  var dialog=new RecibosCobrosDialog(service,mock(FacturacionPosService.class),mock(TerminalPosDto.class),print,pdf);UUID id=UUID.randomUUID();var boton=dialog.botonImprimir(id);
  assertThat(boton.isDisableOnClick()).isTrue();boton.click();boton.click();
  var keys=org.mockito.ArgumentCaptor.forClass(UUID.class);verify(print,times(2)).apply(eq(id),keys.capture());assertThat(keys.getAllValues().getFirst()).isEqualTo(keys.getAllValues().getLast());verifyNoInteractions(pdf);verify(service,never()).confirmar(any());
 }
 @Test void falloDeImpresionNoRepiteConfirmacionNiAbrePdf(){
  var dialog=new RecibosCobrosDialog(service,mock(FacturacionPosService.class),mock(TerminalPosDto.class),print,pdf);UUID id=UUID.randomUUID();doThrow(new ReglaNegocioException("No hay agente configurado para esta caja.")).when(print).apply(eq(id),any());
  var boton=dialog.botonImprimir(id);boton.click();assertThat(boton.isEnabled()).isTrue();verifyNoInteractions(pdf);verify(service,never()).confirmar(any());
 }
}
