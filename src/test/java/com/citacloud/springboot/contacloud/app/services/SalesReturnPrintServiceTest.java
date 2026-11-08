package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SalesReturnPrintServiceTest {
    final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),agentId=UUID.randomUUID(),returnId=UUID.randomUUID(),attempt=UUID.randomUUID(),job=UUID.randomUUID();
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final DevolucionPosDocumentoService documents=mock(DevolucionPosDocumentoService.class);
    final PrintAgentRoutingService routing=mock(PrintAgentRoutingService.class);
    final AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);
    final AuditoriaService audit=mock(AuditoriaService.class);
    final DevolucionVentaPrintService service=new DevolucionVentaPrintService(jdbc,documents,routing,agents,audit);
    final DevolucionVenta returned=mock(DevolucionVenta.class);final AgenteImpresion agent=mock(AgenteImpresion.class);
    final byte[] pdf={'%','P','D','F','-'};
    @BeforeEach void setup(){
        var p=mock(TenantPrincipal.class);when(p.tenantId()).thenReturn(tenant);when(p.empresaId()).thenReturn(company);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
        when(documents.obtener(returnId)).thenReturn(returned);when(documents.origen(returned)).thenReturn(new DevolucionPosDocumentoService.Origen(branch,cash));
        when(routing.resolver(cash)).thenReturn(agentId);when(agents.bloquear(agentId,tenant,company)).thenReturn(Optional.of(agent));
        when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(agent.getSucursalId()).thenReturn(branch);
        var first=mock(Caja.class);when(first.getId()).thenReturn(UUID.randomUUID());var second=mock(Caja.class);when(second.getId()).thenReturn(cash);
        when(agent.getCajas()).thenReturn(Set.of(first,second));when(agent.isPrintOnScreen()).thenReturn(false);
        when(documents.documento(returned)).thenReturn(PrintSnapshotFixtures.salesReturn());when(documents.generar(returnId)).thenReturn(new ArchivoPdfDto("Devolucion.pdf",pdf));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private void existing(List<UUID> first,List<UUID> second){
        when(jdbc.query(contains("document_type='SALES_RETURN'"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(tenant),eq(company),eq(attempt),eq(returnId))).thenReturn(first.stream().map(id->new SolicitudImpresionResultadoDto(id,false)).toList(),first.stream().map(id->new SolicitudImpresionResultadoDto(id,false)).toList(),second.stream().map(id->new SolicitudImpresionResultadoDto(id,false)).toList());
    }
    @Test void currentRegisterRoutesAmongMultipleAgentRegistersAndUsesSharedPdfPreference(){
        existing(List.of(),List.of(job));when(jdbc.update(startsWith("insert into billing_print_jobs"),any(Object[].class))).thenReturn(1);
        assertEquals(job,service.solicitar(returnId,attempt).trabajoId());
        verify(jdbc).update(startsWith("insert into billing_print_jobs"),any(UUID.class),eq(tenant),eq(company),eq(branch),eq(cash),eq(agentId),eq(returnId),eq(attempt),eq(pdf),eq(false),eq("SALES_RETURN"));
        verify(agent).isPrintOnScreen();verify(audit).registrar(eq("PrintJobCreated"),eq("PrintJob"),any(UUID.class),contains("SALES_RETURN"));
    }
    @Test void retryWithSameAttemptReturnsExistingJobWithoutRenderingOrRoutingAgain(){
        existing(List.of(job),List.of(job));assertEquals(job,service.solicitar(returnId,attempt).trabajoId());
        verifyNoInteractions(routing,agents,audit);verify(documents,never()).generar(any());verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
    }
    @Test void rendererFailureNeverEnqueuesOrRepeatsBusinessEffects(){
        existing(List.of(),List.of());when(documents.generar(returnId)).thenThrow(new ReglaNegocioException("No fue posible generar el documento."));
        assertThrows(ReglaNegocioException.class,()->service.solicitar(returnId,attempt));verifyNoInteractions(audit);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
    }
    @Test void removedRegisterOrDifferentBranchCannotRedirectAJob(){
        existing(List.of(),List.of());when(agent.getCajas()).thenReturn(Set.of());
        assertThrows(ReglaNegocioException.class,()->service.solicitar(returnId,attempt));verify(documents,never()).generar(any());
        when(agent.getSucursalId()).thenReturn(UUID.randomUUID());assertThrows(ReglaNegocioException.class,()->service.solicitar(returnId,attempt));
    }
    @Test void unauthorisedReturnIdStopsBeforeAnyRouting(){
        when(documents.obtener(returnId)).thenThrow(new RecursoNoEncontradoException("Devolución no encontrada."));
        assertThrows(RecursoNoEncontradoException.class,()->service.solicitar(returnId,attempt));verifyNoInteractions(jdbc,routing,agents,audit);
    }
    @Test void explicitReprintUsesNewAttemptButTheSameReturnReference(){
        existing(List.of(job),List.of(job));assertEquals(job,service.solicitar(returnId,attempt).trabajoId());
        UUID secondAttempt=UUID.randomUUID();when(jdbc.query(contains("document_type='SALES_RETURN'"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(tenant),eq(company),eq(secondAttempt),eq(returnId))).thenReturn(List.of(),List.of(),List.of(new SolicitudImpresionResultadoDto(job,false)));
        assertEquals(job,service.solicitar(returnId,secondAttempt).trabajoId());verify(documents,times(2)).obtener(returnId);verify(documents,times(1)).generar(returnId);
    }
    @Test void pdfModeDoesNotRenderTicketOrCreateJobEvenWhenRevoked(){
        when(agent.isPrintOnScreen()).thenReturn(true);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.REVOKED);
        var result=service.solicitar(returnId,attempt);assertTrue(result.printOnScreen());assertNull(result.trabajoId());
        verify(documents,never()).generar(any());verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));verifyNoInteractions(audit);
    }
    @Test void revokedPrinterModeCannotRenderOrEnqueue(){
        when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.REVOKED);
        assertThrows(ReglaNegocioException.class,()->service.solicitar(returnId,attempt));
        verify(documents,never()).generar(any());verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
    }
}
