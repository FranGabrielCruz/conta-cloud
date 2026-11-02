package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
class PrintAgentRoutingServiceTest {
 final CashRegisterRepository cajas=mock(CashRegisterRepository.class);final AgenteImpresionRepository agentes=mock(AgenteImpresionRepository.class);
 final PrintAgentRoutingService service=new PrintAgentRoutingService(cajas,agentes);
 UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),sucursal=UUID.randomUUID(),caja=UUID.randomUUID();
 @BeforeEach void setup(){var p=new TenantPrincipal(UUID.randomUUID(),tenant,empresa,null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("facturas.pdf"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @Test void rechazaCajaDeOtraEmpresaSinBuscarAgentes(){when(cajas.findByIdAndTenantIdAndEmpresaId(caja,tenant,empresa)).thenReturn(Optional.empty());assertThatThrownBy(()->service.resolver(caja)).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(agentes);}
 @Test void sinAgenteNoRecurreAOtraCaja(){configurarCaja();when(agentes.destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.ACTIVE)).thenReturn(List.of());assertThatThrownBy(()->service.resolver(caja)).isInstanceOf(ReglaNegocioException.class);verify(agentes).destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.ACTIVE);verify(agentes).destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.REVOKED);verifyNoMoreInteractions(agentes);}
 @Test void destinoAmbiguoSeRechaza(){configurarCaja();when(agentes.destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.ACTIVE)).thenReturn(List.of(mock(AgenteImpresion.class),mock(AgenteImpresion.class)));assertThatThrownBy(()->service.resolver(caja)).isInstanceOf(ReglaNegocioException.class);}
 @Test void resuelveSoloAgenteDeCajaActual(){configurarCaja();var a=mock(AgenteImpresion.class);UUID id=UUID.randomUUID();when(a.getId()).thenReturn(id);when(agentes.destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.ACTIVE)).thenReturn(List.of(a));assertThat(service.resolver(caja)).isEqualTo(id);}
 @Test void agenteRevocadoPuedeResolverSoloModoPdf(){
  configurarCaja();var a=mock(AgenteImpresion.class);UUID id=UUID.randomUUID();when(a.getId()).thenReturn(id);when(a.isPrintOnScreen()).thenReturn(true);
  when(agentes.destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.REVOKED)).thenReturn(List.of(a));
  assertThat(service.resolver(caja)).isEqualTo(id);
  when(a.isPrintOnScreen()).thenReturn(false);assertThatThrownBy(()->service.resolver(caja)).isInstanceOf(ReglaNegocioException.class);
 }
 @Test void noEligeEntreAsociacionesHistoricasRevocadas(){
  configurarCaja();when(agentes.destinos(tenant,empresa,sucursal,caja,EstadoAgenteImpresion.REVOKED)).thenReturn(List.of(mock(AgenteImpresion.class),mock(AgenteImpresion.class)));
  assertThatThrownBy(()->service.resolver(caja)).isInstanceOf(ReglaNegocioException.class);
 }
 void configurarCaja(){var c=new Caja(tenant,empresa,sucursal,UUID.randomUUID(),"C01","Caja",null,true,UUID.randomUUID());when(cajas.findByIdAndTenantIdAndEmpresaId(caja,tenant,empresa)).thenReturn(Optional.of(c));}
}
