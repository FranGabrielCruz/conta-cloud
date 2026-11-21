package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountingPostingServiceTest {
 private final ConfiguracionMotorService config=mock(ConfiguracionMotorService.class);
 private final AsientoContableService asientos=mock(AsientoContableService.class);
 private final IntegracionContableHandler handler=mock(IntegracionContableHandler.class);
 private AccountingPostingService servicio;
 private EventoContable evento;
 @BeforeEach void preparar() {
  var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"TEST","Prueba","operador","",true,true,Set.of(),Set.of("facturas_proveedores.registrar"));
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
  when(handler.modulo()).thenReturn(Modulo.COMPRAS);when(handler.eventos()).thenReturn(Set.of("FACTURA_PROVEEDOR:REGISTRO"));
  servicio=new AccountingPostingService(new RegistroIntegracionesContables(List.of(handler)),config,asientos);
  evento=ComprasAccountingHandlerTest.evento(List.of(ComprasAccountingHandlerTest.r(false,"100")),"0","100",null,null);
 }
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void reenvioDevuelveAsientoAntesDeRevalidarConfigORecalcularLineas() {
  var id=UUID.randomUUID();when(asientos.automaticoExistente(evento.fuente())).thenReturn(Optional.of(id));
  assertThat(servicio.contabilizar(evento)).isEqualTo(id);
  verifyNoInteractions(config);verify(handler,never()).proponer(any());
 }
 @Test void operadorSinPermisoAdministrarAutomatizacionSiGeneraAsiento() {
  when(config.efectivo(Modulo.COMPRAS)).thenReturn(true);var id=UUID.randomUUID();
  when(asientos.registrarAutomatico(eq(evento.fuente()),any(),any(),any(),any(),eq(1L),eq("trace-compra"),anyString())).thenReturn(id);
  assertThat(servicio.contabilizar(evento)).isEqualTo(id);verify(handler).proponer(evento);
 }
 @Test void apagadoNoProponeNiEscribeYFalloContableSePropaga() {
  assertThat(servicio.contabilizar(evento)).isNull();verify(handler,never()).proponer(any());
  when(config.efectivo(Modulo.COMPRAS)).thenThrow(new ReglaNegocioException("Configuración incompleta"));
  assertThatThrownBy(()->servicio.contabilizar(evento)).hasMessageContaining("incompleta");
 }
 @Test void registroNoInventaIntegracionesYRechazaDuplicados() {
  var r=new RegistroIntegracionesContables(List.of(handler));
  assertThat(r.disponible(Modulo.COMPRAS)).isTrue();assertThat(r.disponible(Modulo.INVENTARIO)).isFalse();
  assertThatThrownBy(()->new RegistroIntegracionesContables(List.of(handler,handler))).isInstanceOf(IllegalStateException.class);
 }
}

