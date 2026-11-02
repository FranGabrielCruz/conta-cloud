package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.jdbc.core.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
class BillingPrintServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);final FacturaVentaRepository facturas=mock(FacturaVentaRepository.class);final PrintAgentRoutingService routing=mock(PrintAgentRoutingService.class);final FacturaVentaPdfService pdf=mock(FacturaVentaPdfService.class);final AuditoriaService audit=mock(AuditoriaService.class);
 final BillingPrintService service=new BillingPrintService(jdbc,facturas,routing,pdf,agents,audit);
 final PrintAgentPrincipal p=new PrintAgentPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"PA-1","Agente");
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @Test void agenteSinCajasNoObtieneTrabajos(){AgenteImpresion a=activo();when(a.getCajas()).thenReturn(Set.of());assertThat(service.siguiente(p)).isEmpty();verifyNoInteractions(jdbc);}
 @Test void agenteDeOtraEmpresaNoPuedeConfirmarTrabajo(){when(agents.bloquear(p.agentId(),p.tenantId(),p.companyId())).thenReturn(Optional.empty());assertThatThrownBy(()->service.completar(p,UUID.randomUUID(),UUID.randomUUID(),true)).isInstanceOf(VinculacionAgenteException.class);verifyNoInteractions(jdbc,audit);}
 @Test void confirmacionConIdAjenoNoGeneraAuditoriaDeExito(){activo();assertThatThrownBy(()->service.completar(p,UUID.randomUUID(),UUID.randomUUID(),true)).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(audit);}
 @Test void reservaTrabajosDeCualquieraDeSusCajasUsandoLaAsociacionAutorizada(){var a=activo();when(a.getCajas()).thenReturn(Set.of(mock(Caja.class),mock(Caja.class)));UUID job=UUID.randomUUID();when(jdbc.query(contains("for update of j skip locked"),org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),eq(p.tenantId()),eq(p.companyId()),eq(p.branchId()),eq(p.agentId()))).thenReturn(List.of(job));when(jdbc.update(contains("set status='PROCESSING'"),any(UUID.class),eq(job),eq(p.tenantId()),eq(p.companyId()),eq(p.agentId()))).thenReturn(1);when(jdbc.queryForObject(contains("select document_pdf"),eq(byte[].class),eq(job),eq(p.tenantId()),eq(p.companyId()),eq(p.agentId()),any(UUID.class))).thenReturn(new byte[]{1});assertThat(service.siguiente(p)).isPresent();verify(jdbc).query(contains("ar.cash_register_id=j.cash_register_id"),org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),eq(p.tenantId()),eq(p.companyId()),eq(p.branchId()),eq(p.agentId()));}
 @Test void preferenciaSeResuelvePorFacturaCajaYAgenteNoPorEmpresa(){autenticarUsuario();UUID cajaA=UUID.randomUUID(),cajaB=UUID.randomUUID(),idA=UUID.randomUUID(),idB=UUID.randomUUID(),fA=UUID.randomUUID(),fB=UUID.randomUUID();factura(fA,cajaA);factura(fB,cajaB);when(routing.resolver(cajaA)).thenReturn(idA);when(routing.resolver(cajaB)).thenReturn(idB);var a=mock(AgenteImpresion.class);var b=mock(AgenteImpresion.class);when(a.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(b.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(a.isPrintOnScreen()).thenReturn(true);when(b.isPrintOnScreen()).thenReturn(false);when(agents.findByIdAndTenantIdAndEmpresaId(idA,p.tenantId(),p.companyId())).thenReturn(Optional.of(a));when(agents.findByIdAndTenantIdAndEmpresaId(idB,p.tenantId(),p.companyId())).thenReturn(Optional.of(b));assertThat(service.printOnScreen(fA)).isTrue();assertThat(service.printOnScreen(fB)).isFalse();verifyNoInteractions(jdbc);}
 @Test void unaSolicitudIdempotenteConservaUnTrabajoYLaPreferenciaDeSuAgente(){autenticarUsuario();UUID facturaId=UUID.randomUUID(),cajaId=UUID.randomUUID(),intento=UUID.randomUUID(),job=UUID.randomUUID();factura(facturaId,cajaId);var a=activo();when(a.isPrintOnScreen()).thenReturn(false);var c=mock(Caja.class);when(c.getId()).thenReturn(cajaId);when(a.getCajas()).thenReturn(Set.of(c));when(routing.resolver(cajaId)).thenReturn(p.agentId());when(pdf.generarParaImpresion(facturaId)).thenReturn(new ArchivoPdfDto("factura.pdf",new byte[]{1}));var result=new SolicitudImpresionResultadoDto(job,false);when(jdbc.query(contains("select id,print_on_screen"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(p.tenantId()),eq(p.companyId()),eq(intento),eq(facturaId))).thenReturn(List.of(),List.of(),List.of(result),List.of(result));assertThat(service.solicitar(facturaId,intento)).isEqualTo(result);assertThat(service.solicitar(facturaId,intento)).isEqualTo(result);verify(pdf).generarParaImpresion(facturaId);verify(jdbc).update(contains("insert into billing_print_jobs"),any(UUID.class),eq(p.tenantId()),eq(p.companyId()),eq(p.branchId()),eq(cajaId),eq(p.agentId()),eq(facturaId),eq(intento),any(byte[].class),eq(false),eq("SALES_INVOICE"));}

 @Test void modoPdfNoGeneraTicketNiCreaTrabajoAunqueElAgenteNoTengaHeartbeat(){
  autenticarUsuario();UUID id=UUID.randomUUID(),caja=UUID.randomUUID();factura(id,caja);
  var a=activo();when(a.isPrintOnScreen()).thenReturn(true);
  var c=mock(Caja.class);when(c.getId()).thenReturn(caja);when(a.getCajas()).thenReturn(Set.of(c));
  when(routing.resolver(caja)).thenReturn(p.agentId());
  var salida=service.solicitar(id,UUID.randomUUID());
  assertThat(salida.printOnScreen()).isTrue();assertThat(salida.trabajoId()).isNull();
  verifyNoInteractions(pdf,audit);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));

 }
 @Test void facturaDeOtroTenantNoPermiteNiPdfNiTrabajo(){
  autenticarUsuario();assertThatThrownBy(()->service.solicitar(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
  verifyNoInteractions(jdbc,routing,agents,pdf,audit);
 }
 @Test void cajaRetiradaNoPuedeCrearSalidaAunqueElModoSeaPdf(){
  autenticarUsuario();UUID id=UUID.randomUUID(),caja=UUID.randomUUID();factura(id,caja);
  var a=activo();when(a.isPrintOnScreen()).thenReturn(true);when(a.getCajas()).thenReturn(Set.of());when(routing.resolver(caja)).thenReturn(p.agentId());
  assertThatThrownBy(()->service.solicitar(id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);
  verifyNoInteractions(pdf,audit);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
 }
 @Test void falloDeImpresoraNoSeConvierteEnPdf(){
  autenticarUsuario();UUID id=UUID.randomUUID(),caja=UUID.randomUUID();factura(id,caja);
  when(routing.resolver(caja)).thenThrow(new ReglaNegocioException("No disponible"));
  assertThatThrownBy(()->service.solicitar(id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);
  verifyNoInteractions(pdf,audit);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
 }
 @Test void resultadoImpideCombinarPdfYTrabajo(){
  assertThatThrownBy(()->new SolicitudImpresionResultadoDto(UUID.randomUUID(),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new SolicitudImpresionResultadoDto(null,false)).isInstanceOf(IllegalArgumentException.class);
 }
 private AgenteImpresion activo(){var a=mock(AgenteImpresion.class);when(a.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(a.getId()).thenReturn(p.agentId());when(a.getSucursalId()).thenReturn(p.branchId());when(agents.bloquear(p.agentId(),p.tenantId(),p.companyId())).thenReturn(Optional.of(a));return a;}
 private void autenticarUsuario(){var u=new TenantPrincipal(UUID.randomUUID(),p.tenantId(),p.companyId(),null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("facturas.pdf"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));}
 private FacturaVenta factura(UUID id,UUID caja){var f=mock(FacturaVenta.class);when(f.getCajaId()).thenReturn(caja);when(f.getSucursalId()).thenReturn(p.branchId());when(facturas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.companyId())).thenReturn(Optional.of(f));return f;}
}
