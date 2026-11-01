package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
class BillingPrintServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);
 final AuditoriaService audit=mock(AuditoriaService.class);
 final BillingPrintService service=new BillingPrintService(jdbc,mock(FacturaVentaRepository.class),mock(PrintAgentRoutingService.class),mock(FacturaVentaPdfService.class),agents,audit);
 final PrintAgentPrincipal p=new PrintAgentPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"PA-1","Agente");
 @Test void agenteSinCajaNoObtieneTrabajos(){var agent=mock(AgenteImpresion.class);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(agents.bloquear(p.agentId(),p.tenantId(),p.companyId())).thenReturn(Optional.of(agent));assertThatThrownBy(()->service.siguiente(p)).isInstanceOf(VinculacionAgenteException.class);verifyNoInteractions(jdbc);}
 @Test void agenteDeOtraEmpresaNoPuedeConfirmarTrabajo(){when(agents.bloquear(p.agentId(),p.tenantId(),p.companyId())).thenReturn(Optional.empty());assertThatThrownBy(()->service.completar(p,UUID.randomUUID(),UUID.randomUUID(),true)).isInstanceOf(VinculacionAgenteException.class);verifyNoInteractions(jdbc,audit);}
 @Test void confirmacionConIdAjenoNoGeneraAuditoriaDeExito(){var agent=mock(AgenteImpresion.class);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(agent.getCajaId()).thenReturn(UUID.randomUUID());when(agents.bloquear(p.agentId(),p.tenantId(),p.companyId())).thenReturn(Optional.of(agent));assertThatThrownBy(()->service.completar(p,UUID.randomUUID(),UUID.randomUUID(),true)).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(audit);}
 @Test void reservaYDescargaMantienenTenantEmpresaYCaja(){var a=mock(AgenteImpresion.class);UUID caja=UUID.randomUUID(),branch=UUID.randomUUID(),job=UUID.randomUUID();when(a.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(a.getCajaId()).thenReturn(caja);when(a.getSucursalId()).thenReturn(branch);when(a.getId()).thenReturn(p.agentId());when(agents.bloquear(p.agentId(),p.tenantId(),p.companyId())).thenReturn(Optional.of(a));when(jdbc.query(contains("for update skip locked"),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),eq(p.tenantId()),eq(p.companyId()),eq(branch),eq(caja),eq(p.agentId()))).thenReturn(List.of(job));when(jdbc.queryForObject(contains("select document_pdf"),eq(byte[].class),eq(job),eq(p.tenantId()),eq(p.companyId()))).thenReturn(new byte[]{1});assertThat(service.siguiente(p)).isPresent();verify(jdbc).update(contains("where id=? and tenant_id=? and empresa_id=?"),any(UUID.class),eq(job),eq(p.tenantId()),eq(p.companyId()));}
}
