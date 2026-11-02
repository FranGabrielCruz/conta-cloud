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
class CashVoucherConfirmationTest {
 @Test void snapshotIsCapturedBeforeCompletionAndReplayDoesNotRepeatMovement(){
  UUID t=UUID.randomUUID(),e=UUID.randomUUID(),u=UUID.randomUUID(),b=UUID.randomUUID(),cash=UUID.randomUUID(),sid=UUID.randomUUID(),id=UUID.randomUUID(),cid=UUID.randomUUID(),mid=UUID.randomUUID();
  var p=new TenantPrincipal(u,t,e,null,"DEMO","Juan Pérez","juan","",true,true,Set.of(),Set.of("vales_caja.crear"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
  try{
   var vouchers=mock(ValeCajaRepository.class);var concepts=mock(ConceptoValeCajaRepository.class);var sessions=mock(CashRegisterSessionRepository.class);var movements=mock(FinancialMovementRepository.class);var idem=mock(IdempotentOperationService.class);var numbers=mock(MovimientoCajaNumeroService.class);var audit=mock(AuditoriaService.class);var jdbc=mock(JdbcTemplate.class);var companies=mock(EmpresaRepository.class);var data=mock(DatosEmpresaRepository.class);
   var s=mock(SesionCaja.class);when(s.getId()).thenReturn(sid);when(s.getTenantId()).thenReturn(t);when(s.getEmpresaId()).thenReturn(e);when(s.getCajaId()).thenReturn(cash);when(s.getSucursalId()).thenReturn(b);when(s.getMonedaId()).thenReturn(UUID.randomUUID());when(s.getAbiertoPor()).thenReturn(u);when(s.getStatus()).thenReturn(EstadoSesionCaja.OPEN);when(sessions.bloquear(sid,t,e)).thenReturn(Optional.of(s));
   var c=mock(Caja.class);when(c.getNombre()).thenReturn("Caja 01");when(s.getCaja()).thenReturn(c);var branch=mock(Sucursal.class);when(branch.getNombre()).thenReturn("Principal");when(s.getSucursal()).thenReturn(branch);var currency=mock(Moneda.class);when(currency.getCodigoIso()).thenReturn("DOP");when(s.getMoneda()).thenReturn(currency);
   var concept=mock(ConceptoValeCaja.class);when(concept.getId()).thenReturn(cid);when(concept.getNombre()).thenReturn("Materiales");when(concepts.findByIdAndTenantIdAndEmpresaIdAndActiveTrue(cid,t,e)).thenReturn(Optional.of(concept));var company=mock(Empresa.class);when(company.getId()).thenReturn(e);when(company.getTenantId()).thenReturn(t);when(company.getNombre()).thenReturn("DEMO");when(company.getZonaHoraria()).thenReturn("America/Santo_Domingo");when(company.getPaisCodigo()).thenReturn("DO");when(companies.findByIdAndTenantId(e,t)).thenReturn(Optional.of(company));
   when(idem.begin(eq(t),eq(e),eq("CASH_VOUCHER_CREATE"),any(),anyString())).thenReturn(new IdempotentOperationService.Start(true,null,null),new IdempotentOperationService.Start(false,"CASH_VOUCHER",id));when(jdbc.queryForObject(contains("RETURNING current_value"),eq(Long.class),eq(t),eq(e))).thenReturn(25L);when(numbers.siguiente(t,e)).thenReturn("MOV-25");
   var saved=new java.util.concurrent.atomic.AtomicReference<ValeCaja>();when(vouchers.saveAndFlush(any())).thenAnswer(a->{ValeCaja v=a.getArgument(0);ReflectionTestUtils.setField(v,"id",id);saved.set(v);return v;});when(vouchers.findByIdAndTenantIdAndEmpresaId(id,t,e)).thenAnswer(a->Optional.ofNullable(saved.get()));when(movements.saveAndFlush(any())).thenAnswer(a->{MovimientoFinanciero m=a.getArgument(0);ReflectionTestUtils.setField(m,"id",mid);return m;});
   doAnswer(a->{assertThat(saved.get().getDocumentoPos()).contains("CASH_OUT","Materiales");assertThat(saved.get().getMovimientoId()).isEqualTo(mid);return null;}).when(idem).complete(eq(t),eq(e),eq("CASH_VOUCHER_CREATE"),any(),eq("CASH_VOUCHER"),eq(id));
   var service=new ValeCajaService(vouchers,concepts,sessions,movements,idem,numbers,audit,jdbc,mock(CashRegisterRepository.class),mock(SucursalRepository.class),mock(UsuarioRepository.class),companies,data);var input=new ValeCajaInput(sid,cid,new BigDecimal("2500"),"Carlos",null,null,UUID.randomUUID());
   assertThat(service.confirmar(input).numero()).isEqualTo("VAL-000025");String json=saved.get().getDocumentoPos();assertThat(service.confirmar(input).id()).isEqualTo(id);assertThat(saved.get().getDocumentoPos()).isEqualTo(json);verify(movements).saveAndFlush(any());verify(numbers).siguiente(t,e);verify(jdbc).queryForObject(contains("RETURNING current_value"),eq(Long.class),eq(t),eq(e));
  }finally{SecurityContextHolder.clearContext();}
 }
}
