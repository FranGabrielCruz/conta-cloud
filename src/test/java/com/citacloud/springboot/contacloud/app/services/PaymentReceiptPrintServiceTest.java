package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PaymentReceiptPrintServiceTest {
    UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),agentId=UUID.randomUUID(),receipt=UUID.randomUUID();
    JdbcTemplate jdbc=mock(JdbcTemplate.class);AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);
    PrintAgentRoutingService routing=mock(PrintAgentRoutingService.class);AuditoriaService audit=mock(AuditoriaService.class);
    PaymentReceiptSnapshotRepository snapshots=mock(PaymentReceiptSnapshotRepository.class);
    PaymentReceiptPrintService service=new PaymentReceiptPrintService(snapshots,new BillingPrintRouter(jdbc,routing,agents,audit));
    @BeforeEach void context(){var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("recibos.imprimir"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    PaymentReceiptSnapshotRepository.Stored ready(boolean screen){
        var s=new PaymentReceiptSnapshotRepository.Stored(branch,cash,"APPLIED","{}");when(snapshots.obtener(receipt,tenant,company)).thenReturn(s);
        var a=mock(AgenteImpresion.class);when(a.getSucursalId()).thenReturn(branch);when(a.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(a.isPrintOnScreen()).thenReturn(screen);
        var c=mock(Caja.class);when(c.getId()).thenReturn(cash);when(a.getCajas()).thenReturn(Set.of(c));when(routing.resolver(cash)).thenReturn(agentId);when(agents.bloquear(agentId,tenant,company)).thenReturn(Optional.of(a));return s;
    }
    @Test void pantallaNoGeneraTicketNiTrabajo(){ready(true);var result=service.solicitar(receipt,UUID.randomUUID());assertThat(result.printOnScreen()).isTrue();verify(snapshots,never()).documento(any());verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));verifyNoInteractions(audit);}
    @Test void trabajoTieneTipoYOrigenCorrectosYReintentoNoRenderizaDosVeces(){
        var s=ready(false);UUID attempt=UUID.randomUUID(),job=UUID.randomUUID();var result=new SolicitudImpresionResultadoDto(job,false);
        when(jdbc.query(contains("select id,print_on_screen"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(tenant),eq(company),eq(attempt),eq(receipt))).thenReturn(List.of(),List.of(),List.of(result),List.of(result));
        when(snapshots.documento(s)).thenReturn(doc());
        assertThat(service.solicitar(receipt,attempt)).isEqualTo(result);assertThat(service.solicitar(receipt,attempt)).isEqualTo(result);
        verify(snapshots,times(2)).documento(s);verify(jdbc).update(contains("insert into billing_print_jobs"),any(UUID.class),eq(tenant),eq(company),eq(branch),eq(cash),eq(agentId),eq(receipt),eq(attempt),any(byte[].class),eq(false),eq("PAYMENT_RECEIPT"));
    }
    @Test void otroTenantNoResuelveAgente(){assertThatThrownBy(()->service.solicitar(receipt,UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(jdbc,routing,agents,audit);}
    @Test void sinCajaNoSeSeleccionaAgenteArbitrario(){when(snapshots.obtener(receipt,tenant,company)).thenReturn(new PaymentReceiptSnapshotRepository.Stored(branch,null,"AVAILABLE","{}"));assertThatThrownBy(()->service.solicitar(receipt,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(jdbc,routing,agents,audit);}
    @Test void anuladoNoImprime(){when(snapshots.obtener(receipt,tenant,company)).thenReturn(new PaymentReceiptSnapshotRepository.Stored(branch,cash,"VOIDED","{}"));assertThatThrownBy(()->service.solicitar(receipt,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(jdbc,routing,agents,audit);}
    @Test void desconexionNoAbrePdfNiConsultaSnapshot(){ready(false);when(routing.resolver(cash)).thenThrow(new ReglaNegocioException("No disponible"));assertThatThrownBy(()->service.solicitar(receipt,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verify(snapshots,never()).documento(any());verifyNoInteractions(audit);}
    @Test void salidaExigePermisoYTransaccionIndependiente() throws Exception {
        var method=PaymentReceiptPrintService.class.getMethod("solicitar",UUID.class,UUID.class);
        assertThat(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
        assertThat(method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value()).contains("recibos.imprimir","VENTAS");
    }
    @Test void sucursalAjenaNoPermiteResolverSalida(){
        ready(true);var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"DEMO","Admin","admin","",true,false,Set.of(UUID.randomUUID()),Set.of("recibos.imprimir"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
        assertThatThrownBy(()->service.solicitar(receipt,UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(jdbc);verify(snapshots,never()).documento(any());
    }
    static PaymentReceiptPrintDocument doc(){return new PaymentReceiptPrintDocument(new PaymentReceiptPrintDocument.CompanySnapshot("DEMO",null,null,null,"DO"),"REC-001",LocalDateTime.of(2026,11,1,17,25),new PaymentReceiptPrintDocument.CustomerSnapshot("Juan",null),null,null,null,"Abono a cuenta","DOP",List.of(),BigDecimal.ZERO,List.of(new PaymentReceiptPrintDocument.PaymentMethod("Efectivo",BigDecimal.TEN,null)),BigDecimal.TEN,null,null,null,List.of("Cobro recibido"));}
}
