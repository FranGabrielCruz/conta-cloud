package com.citacloud.springboot.contacloud.app.views.components;
import com.citacloud.springboot.contacloud.app.services.UltimasFacturasService;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class UltimasFacturasDialogLayoutTest {
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void reservaAccionesPorContenidoYConservaClienteFlexibleYAlturaDeRegistros() throws Exception {
  var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,Set.of("facturas.pdf"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
  var dialog=new UltimasFacturasDialog(mock(UltimasFacturasService.class),id->{},(id,attempt)->true,()->{});
  var field=UltimasFacturasDialog.class.getDeclaredField("grid");field.setAccessible(true);Grid<?> grid=(Grid<?>)field.get(dialog);
  var columns=grid.getColumns();assertThat(columns).hasSize(6);var actions=columns.getLast();assertThat(actions.getHeaderText()).isEqualTo("Acciones");assertThat(actions.isAutoWidth()).isTrue();assertThat(actions.isFrozenToEnd()).isTrue();assertThat(actions.getFlexGrow()).isZero();assertThat(columns.get(2).getFlexGrow()).isPositive();assertThat(grid.isAllRowsVisible()).isTrue();
 }
 @Test void imprimirProtegeElBotonYConservaCallbackSeparadoDeVerPdf(){
  java.util.function.Consumer<UUID> pdf=mock(java.util.function.Consumer.class);
  java.util.function.BiFunction<UUID,UUID,Boolean> impresora=mock(java.util.function.BiFunction.class);
  var dialog=new UltimasFacturasDialog(mock(UltimasFacturasService.class),pdf,impresora,()->{});
  UUID factura=UUID.randomUUID();var boton=dialog.botonImprimir(factura,ButtonSize.GRID_ACTION);
  assertThat(boton.isDisableOnClick()).isTrue();boton.click();
  verify(impresora).apply(eq(factura),any(UUID.class));verifyNoInteractions(pdf);assertThat(boton.isEnabled()).isTrue();
 }
}
