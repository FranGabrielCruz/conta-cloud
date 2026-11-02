package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
class CashCountConfirmationTest {
    @Test void confirmarCapturaUnaVezYReplayNoRepiteConteoNiNumeracion(){
        UUID t=UUID.randomUUID(),e=UUID.randomUUID(),userId=UUID.randomUUID(),branch=UUID.randomUUID(),sessionId=UUID.randomUUID(),countId=UUID.randomUUID(),denId=UUID.randomUUID(),currencyId=UUID.randomUUID();
        var p=new TenantPrincipal(userId,t,e,null,"DEMO","Juan","juan","",true,true,Set.of(),Set.of("recuentos_caja.crear"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
        try{
            var sessions=mock(CashRegisterSessionRepository.class);var movements=mock(FinancialMovementRepository.class);var denominations=mock(DenominacionCajaRepository.class);var counts=mock(RecuentoCajaRepository.class);var details=mock(DetalleRecuentoCajaRepository.class);var audit=mock(AuditoriaService.class);var idempotency=mock(IdempotentOperationService.class);var jdbc=mock(JdbcTemplate.class);var documents=mock(CashCountDocumentService.class);
            var s=mock(SesionCaja.class);when(s.getId()).thenReturn(sessionId);when(s.getTenantId()).thenReturn(t);when(s.getEmpresaId()).thenReturn(e);when(s.getSucursalId()).thenReturn(branch);when(s.getMonedaId()).thenReturn(currencyId);when(s.getStatus()).thenReturn(EstadoSesionCaja.OPEN);when(s.getAbiertoPor()).thenReturn(userId);when(s.getFondoInicial()).thenReturn(BigDecimal.TEN);
            var cash=mock(Caja.class);when(cash.getNombre()).thenReturn("Caja 01");when(s.getCaja()).thenReturn(cash);var b=mock(Sucursal.class);when(b.getNombre()).thenReturn("Principal");when(s.getSucursal()).thenReturn(b);var m=mock(Moneda.class);when(m.getCodigoIso()).thenReturn("DOP");when(s.getMoneda()).thenReturn(m);var u=mock(Usuario.class);when(u.getNombre()).thenReturn("Juan");when(s.getUsuarioApertura()).thenReturn(u);
            when(sessions.bloquear(sessionId,t,e)).thenReturn(Optional.of(s));var den=mock(DenominacionCaja.class);when(den.getId()).thenReturn(denId);when(den.getValor()).thenReturn(BigDecimal.TEN);when(den.getTipo()).thenReturn(TipoDenominacionCaja.BANKNOTE);when(denominations.findAllByTenantIdAndEmpresaIdAndMonedaIdAndActiveTrueOrderByOrdenAsc(t,e,currencyId)).thenReturn(List.of(den));when(movements.movimientosValidosSesion(t,e,sessionId)).thenReturn(List.of());
            when(idempotency.begin(eq(t),eq(e),eq("CASH_COUNT_CREATE"),any(UUID.class),anyString())).thenReturn(new IdempotentOperationService.Start(true,null,null),new IdempotentOperationService.Start(false,"RecuentoCaja",countId));
            when(jdbc.queryForObject(contains("returning current_value"),eq(Long.class),eq(t),eq(e))).thenReturn(12L);
            List<DetalleRecuentoCaja> savedDetails=new ArrayList<>();when(details.save(any(DetalleRecuentoCaja.class))).thenAnswer(a->{savedDetails.add(a.getArgument(0));return a.getArgument(0);});when(details.findAllByRecuentoIdAndTenantIdAndEmpresaIdOrderByOrdenAsc(countId,t,e)).thenReturn(savedDetails);when(details.findAllByRecuentoIdOrderByOrdenAsc(countId)).thenReturn(savedDetails);
            when(counts.saveAndFlush(any(RecuentoCaja.class))).thenAnswer(a->{RecuentoCaja r=a.getArgument(0);ReflectionTestUtils.setField(r,"id",countId);when(counts.findByIdAndTenantIdAndEmpresaId(countId,t,e)).thenReturn(Optional.of(r));return r;});
            doAnswer(a->{((RecuentoCaja)a.getArgument(0)).conservarDocumentoPos("snapshot");return null;}).when(documents).conservar(any(),eq(s),anyList());
            var service=new RecuentoCajaService(sessions,movements,denominations,counts,details,audit,idempotency,jdbc,documents);
            var input=new GuardarRecuentoCajaInput(sessionId,UUID.randomUUID(),List.of(new GuardarRecuentoCajaInput.Cantidad(denId,1)),null);
            assertThat(service.guardar(input).id()).isEqualTo(countId);assertThat(service.guardar(input).id()).isEqualTo(countId);verify(counts).saveAndFlush(any(RecuentoCaja.class));verify(details).save(any(DetalleRecuentoCaja.class));verify(documents).conservar(any(),eq(s),anyList());
            var order=inOrder(details,documents,idempotency);order.verify(details).flush();order.verify(documents).conservar(any(),eq(s),anyList());order.verify(idempotency).complete(t,e,"CASH_COUNT_CREATE",input.idempotencia(),"RecuentoCaja",countId);
        }finally{SecurityContextHolder.clearContext();}
    }
}
