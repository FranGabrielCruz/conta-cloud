package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class CashCountPrintServiceTest {
    UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),agentId=UUID.randomUUID(),id=UUID.randomUUID();
    JdbcTemplate jdbc=mock(JdbcTemplate.class);AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);PrintAgentRoutingService routing=mock(PrintAgentRoutingService.class);AuditoriaService audit=mock(AuditoriaService.class);
    CashCountDocumentService documents=mock(CashCountDocumentService.class);RecuentoCajaPdfService normal=mock(RecuentoCajaPdfService.class);
    CashCountPrintService service=new CashCountPrintService(documents,new BillingPrintRouter(jdbc,routing,agents,audit),normal);
    @BeforeEach void login(){login(Set.of("recuentos_caja.imprimir"));}
    void login(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"DEMO","Juan","juan","",true,true,Set.of(),permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    AgenteImpresion agent;RecuentoCaja ready(boolean screen){var r=mock(RecuentoCaja.class);when(r.getCajaId()).thenReturn(cash);when(r.getSucursalId()).thenReturn(branch);when(r.getDocumentoPos()).thenReturn("snapshot");when(documents.obtener(id)).thenReturn(r);
        agent=mock(AgenteImpresion.class);when(agent.getSucursalId()).thenReturn(branch);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(agent.isPrintOnScreen()).thenReturn(screen);var c=mock(Caja.class);when(c.getId()).thenReturn(cash);when(agent.getCajas()).thenReturn(Set.of(c));when(routing.resolver(cash)).thenReturn(agentId);when(agents.bloquear(agentId,tenant,company)).thenReturn(Optional.of(agent));return r;}
    @Test void pantallaNoRenderizaNiCreaJob(){ready(true);assertThat(service.solicitar(id,UUID.randomUUID()).printOnScreen()).isTrue();verify(documents,never()).generar(any(RecuentoCaja.class));verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));verifyNoInteractions(normal,audit);}
    @Test void imprimeConTipoYOrigenCorrectosYReintentoNoDuplicaJob(){var r=ready(false);UUID attempt=UUID.randomUUID(),job=UUID.randomUUID();var result=new SolicitudImpresionResultadoDto(job,false);when(documents.generar(r)).thenReturn(new ArchivoPdfDto("recuento.pdf",new byte[]{1}));when(jdbc.query(contains("document_type='CASH_COUNT'"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(tenant),eq(company),eq(attempt),eq(id))).thenReturn(List.of(),List.of(),List.of(result),List.of(result));assertThat(service.solicitar(id,attempt)).isEqualTo(result);assertThat(service.solicitar(id,attempt)).isEqualTo(result);verify(documents).generar(r);verify(jdbc).update(contains("insert into billing_print_jobs"),any(UUID.class),eq(tenant),eq(company),eq(branch),eq(cash),eq(agentId),eq(id),eq(attempt),any(byte[].class),eq(false),eq("CASH_COUNT"));verifyNoInteractions(normal);}
    @Test void revocadoNoRenderizaNiAbrePdf(){ready(false);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.REVOKED);assertThatThrownBy(()->service.solicitar(id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(normal);verify(documents,never()).generar(any(RecuentoCaja.class));}
    @Test void cajaRetiradaNoGeneraSalida(){ready(false);when(agent.getCajas()).thenReturn(Set.of());assertThatThrownBy(()->service.solicitar(id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verify(documents,never()).generar(any(RecuentoCaja.class));verifyNoInteractions(normal);}
    @Test void errorDesconexionNoHaceFallback(){ready(false);when(routing.resolver(cash)).thenThrow(new ReglaNegocioException("Sin agente"));assertThatThrownBy(()->service.solicitar(id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(normal);verify(documents,never()).generar(any(RecuentoCaja.class));}
    @Test void documentoAjenoNoResuelveAgente(){when(documents.obtener(id)).thenThrow(new RecursoNoEncontradoException("No encontrado"));assertThatThrownBy(()->service.solicitar(id,UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(jdbc,routing,agents,normal);}
    @Test void vistaUsaSnapshotONormalLegacySinCrearTrabajo(){var r=ready(true);var pdf=new ArchivoPdfDto("recuento.pdf",new byte[]{1});when(documents.generar(r)).thenReturn(pdf);assertThat(service.pantalla(id)).isEqualTo(pdf);when(r.getDocumentoPos()).thenReturn(null);when(normal.generar(id)).thenReturn(pdf);assertThat(service.pantalla(id)).isEqualTo(pdf);verifyNoInteractions(jdbc);}
    @Test void permisoSeExigeEnBackendAntesDeConsultar(){login(Set.of("recuentos_caja.ver"));var factory=new org.springframework.aop.framework.ProxyFactory(service);factory.addAdvisor(org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor.preAuthorize());var proxy=(CashCountPrintService)factory.getProxy();assertThatThrownBy(()->proxy.solicitar(id,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(documents,jdbc);}
}
