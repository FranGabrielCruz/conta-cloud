package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
class CashClosingConfirmationTest {
 @Test void confirmationStoresSnapshotBeforeCompletionAndReplayDoesNotCloseAgain(){
  UUID t=UUID.randomUUID(),e=UUID.randomUUID(),u=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),sid=UUID.randomUUID(),id=UUID.randomUUID(),denid=UUID.randomUUID();
  var p=new TenantPrincipal(u,t,e,null,"DEMO","Juan Pérez","juan","",true,true,Set.of(),Set.of("cierres_caja.ejecutar"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
  try{
   var sessions=mock(CashRegisterSessionRepository.class);var movements=mock(FinancialMovementRepository.class);var denominations=mock(DenominacionCajaRepository.class);var closures=mock(CierreCajaRepository.class);var details=mock(DetalleCierreCajaRepository.class);var payments=mock(ResumenMedioPagoCierreCajaRepository.class);var companies=mock(EmpresaRepository.class);var audit=mock(AuditoriaService.class);var idem=mock(IdempotentOperationService.class);var jdbc=mock(JdbcTemplate.class);var data=mock(DatosEmpresaRepository.class);var documents=new CashClosingDocumentService(closures,details,payments,data,movements);
   var s=mock(SesionCaja.class);when(s.getId()).thenReturn(sid);when(s.getTenantId()).thenReturn(t);when(s.getEmpresaId()).thenReturn(e);when(s.getSucursalId()).thenReturn(branch);when(s.getCajaId()).thenReturn(cash);when(s.getMonedaId()).thenReturn(UUID.randomUUID());when(s.getStatus()).thenReturn(EstadoSesionCaja.OPEN);when(s.getAbiertoPor()).thenReturn(u);when(s.getAbiertoEn()).thenReturn(OffsetDateTime.parse("2026-11-02T12:00:00Z"));when(s.getFondoInicial()).thenReturn(BigDecimal.TEN);when(s.getCodigoVisible()).thenReturn("TURN-01");when(sessions.bloquear(sid,t,e)).thenReturn(Optional.of(s));
   var c=mock(Caja.class);when(c.getNombre()).thenReturn("Caja 01");when(s.getCaja()).thenReturn(c);var b=mock(Sucursal.class);when(b.getNombre()).thenReturn("Principal");when(s.getSucursal()).thenReturn(b);var currency=mock(Moneda.class);when(currency.getCodigoIso()).thenReturn("DOP");when(s.getMoneda()).thenReturn(currency);var user=mock(Usuario.class);when(user.getNombre()).thenReturn("Juan");when(user.getApellido()).thenReturn("Pérez");when(s.getUsuarioApertura()).thenReturn(user);
   var company=mock(Empresa.class);when(company.getId()).thenReturn(e);when(company.getTenantId()).thenReturn(t);when(company.getNombre()).thenReturn("DEMO");when(company.getZonaHoraria()).thenReturn("America/Santo_Domingo");when(company.getPaisCodigo()).thenReturn("DO");when(companies.findByIdAndTenantId(e,t)).thenReturn(Optional.of(company));
   var den=mock(DenominacionCaja.class);when(den.getId()).thenReturn(denid);when(den.getValor()).thenReturn(BigDecimal.TEN);when(den.getTipo()).thenReturn(TipoDenominacionCaja.BANKNOTE);when(denominations.findAllByTenantIdAndEmpresaIdAndMonedaIdAndActiveTrueOrderByOrdenAsc(eq(t),eq(e),any())).thenReturn(List.of(den));when(movements.movimientosValidosSesionHasta(eq(t),eq(e),eq(sid),any())).thenReturn(List.of());
   when(idem.begin(eq(t),eq(e),eq("CASH_CLOSURE_CONFIRM"),any(),anyString())).thenReturn(new IdempotentOperationService.Start(true,null,null),new IdempotentOperationService.Start(false,"CierreCaja",id));when(jdbc.queryForObject(contains("returning current_value"),eq(Long.class),eq(t),eq(e))).thenReturn(18L);
   var saved=new java.util.concurrent.atomic.AtomicReference<CierreCaja>();when(closures.saveAndFlush(any())).thenAnswer(a->{CierreCaja closure=a.getArgument(0);ReflectionTestUtils.setField(closure,"id",id);saved.set(closure);return closure;});when(closures.findByIdAndTenantIdAndEmpresaId(id,t,e)).thenAnswer(a->Optional.ofNullable(saved.get()));List<DetalleCierreCaja> rows=new ArrayList<>();when(details.save(any())).thenAnswer(a->{rows.add(a.getArgument(0));return a.getArgument(0);});when(details.findAllByCierreIdAndTenantIdAndEmpresaIdOrderByOrdenAsc(id,t,e)).thenReturn(rows);when(details.findAllByCierreIdOrderByOrdenAsc(id)).thenReturn(rows);
   doAnswer(a->{assertThat(saved.get().getDocumentoPos()).contains("CIE-000018","CUADRADO","denominations");return null;}).when(idem).complete(eq(t),eq(e),eq("CASH_CLOSURE_CONFIRM"),any(),eq("CierreCaja"),eq(id));
   var service=new CierreCajaService(sessions,movements,denominations,closures,details,payments,companies,audit,idem,jdbc,documents);var input=new ConfirmarCierreCajaInput(sid,UUID.randomUUID(),List.of(new ConfirmarCierreCajaInput.Cantidad(denid,1)),null);
   assertThat(service.confirmar(input).numero()).isEqualTo("CIE-000018");String json=saved.get().getDocumentoPos();assertThat(service.confirmar(input).id()).isEqualTo(id);assertThat(saved.get().getDocumentoPos()).isEqualTo(json);verify(closures).saveAndFlush(any());verify(s).cerrar(eq(BigDecimal.TEN),eq(BigDecimal.TEN),isNull(),eq(u),any());verify(jdbc).queryForObject(contains("returning current_value"),eq(Long.class),eq(t),eq(e));verify(details).save(any());
  }finally{SecurityContextHolder.clearContext();}
 }
}
