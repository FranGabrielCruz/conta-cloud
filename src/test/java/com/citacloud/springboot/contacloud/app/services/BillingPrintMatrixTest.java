package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BillingPrintMatrixTest {
 final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),agentId=UUID.randomUUID(),id=UUID.randomUUID(),attempt=UUID.randomUUID();
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final PrintAgentRoutingService routing=mock(PrintAgentRoutingService.class);
 final AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);final AuditoriaService audit=mock(AuditoriaService.class);
 final AgenteImpresion agent=mock(AgenteImpresion.class);
 @SuppressWarnings("unchecked") final Supplier<ArchivoPdfDto> renderer=mock(Supplier.class);
 final BillingPrintRouter router=new BillingPrintRouter(jdbc,routing,agents,audit);
 @BeforeEach void setup(){
  var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("facturas.pdf","devoluciones_venta.pdf","recibos.imprimir"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
  when(routing.resolver(cash)).thenReturn(agentId);when(agents.bloquear(agentId,tenant,company)).thenReturn(Optional.of(agent));when(agent.getSucursalId()).thenReturn(branch);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);
  var caja=mock(Caja.class);when(caja.getId()).thenReturn(cash);when(agent.getCajas()).thenReturn(Set.of(caja));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 BillingPrintRouter.Documento documento(TipoDocumentoImpresion tipo){return new BillingPrintRouter.Documento(tipo,id,branch,cash,renderer);}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void trueSiempreEsScreenSinRenderNiTrabajo(TipoDocumentoImpresion tipo){
  when(agent.isPrintOnScreen()).thenReturn(true);var r=router.solicitar(documento(tipo),attempt);
  assertThat(r.outputMode()).isEqualTo(BillingPrintOutputMode.SCREEN);assertThat(r.trabajoId()).isNull();verifyNoInteractions(renderer,audit);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
 }
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void falseSiempreEsPrintAgentConUnTrabajoTipado(TipoDocumentoImpresion tipo){
  when(renderer.get()).thenReturn(new ArchivoPdfDto("ticket.pdf",new byte[]{1}));UUID job=UUID.randomUUID();var expected=new SolicitudImpresionResultadoDto(job,false);
  when(jdbc.query(contains("select id,print_on_screen"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(tenant),eq(company),eq(attempt),eq(id))).thenReturn(List.of(),List.of(),List.of(expected),List.of(expected));
  var r=router.solicitar(documento(tipo),attempt);assertThat(r.outputMode()).isEqualTo(BillingPrintOutputMode.PRINT_AGENT);assertThat(router.solicitar(documento(tipo),attempt)).isEqualTo(r);
  verify(renderer).get();verify(jdbc).update(contains("insert into billing_print_jobs"),any(UUID.class),eq(tenant),eq(company),eq(branch),eq(cash),eq(agentId),eq(id),eq(attempt),any(byte[].class),eq(false),eq(tipo.name()));
 }
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void revocadoFalseNoEncolaNiCambiaAPantalla(TipoDocumentoImpresion tipo){
  when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.REVOKED);assertThatThrownBy(()->router.solicitar(documento(tipo),attempt)).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(renderer,audit);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
 }
}
