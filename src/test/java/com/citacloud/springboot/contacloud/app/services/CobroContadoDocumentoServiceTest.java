package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;import com.citacloud.springboot.contacloud.app.models.*;import com.citacloud.springboot.contacloud.app.repositories.*;import com.citacloud.springboot.contacloud.app.security.*;
import org.junit.jupiter.api.*;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;import java.util.*;import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class CobroContadoDocumentoServiceTest {
 final MetodoPagoVentaRepository methods=mock(MetodoPagoVentaRepository.class);final BankAccountRepository banks=mock(BankAccountRepository.class);final CuentaDineroResolver accounts=mock(CuentaDineroResolver.class);final CashRegisterSessionService sessions=mock(CashRegisterSessionService.class);final FinancialMovementRepository movements=mock(FinancialMovementRepository.class);final BankReconciliationMatchRepository reconciliation=mock(BankReconciliationMatchRepository.class);
 final UUID cash=UUID.randomUUID(),card=UUID.randomUUID(),bank=UUID.randomUUID(),register=UUID.randomUUID(),session=UUID.randomUUID(),branch=UUID.randomUUID(),currency=UUID.randomUUID();FacturaVenta invoice;CobroContadoDocumentoService service;
 @BeforeEach void setup(){
  var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,Set.of());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
  invoice=mock(FacturaVenta.class);when(invoice.getSucursalId()).thenReturn(branch);when(invoice.getMonedaId()).thenReturn(currency);
  var cashMethod=mock(MetodoPagoVenta.class);when(cashMethod.getId()).thenReturn(cash);when(cashMethod.getName()).thenReturn("Efectivo");when(cashMethod.isCash()).thenReturn(true);when(cashMethod.getType()).thenReturn(TipoMetodoPagoVenta.CASH);when(methods.bloquearActivo(eq(cash),any(),any())).thenReturn(Optional.of(cashMethod));
  var cardMethod=mock(MetodoPagoVenta.class);when(cardMethod.getId()).thenReturn(card);when(cardMethod.getName()).thenReturn("Tarjeta");when(cardMethod.getType()).thenReturn(TipoMetodoPagoVenta.CREDIT_CARD);when(methods.bloquearActivo(eq(card),any(),any())).thenReturn(Optional.of(cardMethod));
  var bankAccount=mock(CuentaBancaria.class);when(bankAccount.getId()).thenReturn(bank);when(bankAccount.isActivo()).thenReturn(true);when(bankAccount.getMonedaId()).thenReturn(currency);when(banks.findByIdAndTenantIdAndEmpresaId(eq(bank),any(),any())).thenReturn(Optional.of(bankAccount));
  var c=mock(Caja.class);when(c.getSucursalId()).thenReturn(branch);when(accounts.resolver(TipoCuentaDinero.CASH_REGISTER,register)).thenReturn(new CuentaDineroResolver.CuentaResuelta(TipoCuentaDinero.CASH_REGISTER,register,currency,null,c,null,"Caja"));when(sessions.requerirSesionAbierta(register)).thenReturn(session);
  service=new CobroContadoDocumentoService(methods,banks,accounts,sessions,movements,reconciliation,mock(NotaDebitoVentaRepository.class));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 EmitirNotaDebitoVentaInput input(AplicacionCobroInput... pagos){return new EmitirNotaDebitoVentaInput(UUID.randomUUID(),java.time.LocalDate.now(),UUID.randomUUID(),"Cargo",List.of(),UUID.randomUUID(),TipoCondicionPago.CASH,List.of(pagos),register,session);}
 AplicacionCobroInput cash(String amount,String received){return new AplicacionCobroInput(cash,new BigDecimal(amount),new BigDecimal(received),null,null,null);}
 AplicacionCobroInput card(String amount,String received){return new AplicacionCobroInput(card,new BigDecimal(amount),received==null?null:new BigDecimal(received),bank,"REF-TEST",null);}
 @Test void efectivoYMixtoRegistranSoloAplicadoYCalculanCambio(){var result=service.validar(input(cash("60","100"),card("40",null)),invoice,new BigDecimal("100"));assertThat(result).hasSize(2);assertThat(result.getFirst().monto()).isEqualByComparingTo("60");assertThat(result.getFirst().cambio()).isEqualByComparingTo("40");assertThat(result.getLast().cambio()).isZero();assertThat(result.getFirst().sesionId()).isEqualTo(session);assertThat(result.getLast().sesionId()).isNull();}
 @Test void tarjetaNoPuedeGenerarCambio(){assertThatThrownBy(()->service.validar(input(card("100","110")),invoice,new BigDecimal("100"))).hasMessageContaining("Solo el efectivo");}
 @Test void pagoIncompletoExcesivoODuplicadoSeRechaza(){for(var request:List.of(input(cash("90","100")),input(cash("110","110")),input(cash("50","50"),cash("50","50"))))assertThatThrownBy(()->service.validar(request,invoice,new BigDecimal("100"))).isInstanceOf(ReglaNegocioException.class);}
 @Test void metodoAjenoYCuentaAjenaSeRevalidan(){assertThatThrownBy(()->service.validar(input(new AplicacionCobroInput(UUID.randomUUID(),new BigDecimal("100"),null,null,null,null)),invoice,new BigDecimal("100"))).isInstanceOf(ReglaNegocioException.class);when(banks.findByIdAndTenantIdAndEmpresaId(eq(bank),any(),any())).thenReturn(Optional.empty());assertThatThrownBy(()->service.validar(input(card("100",null)),invoice,new BigDecimal("100"))).isInstanceOf(ReglaNegocioException.class);}
 @Test void cajaMonedaSucursalYSesionSeRevalidan(){when(sessions.requerirSesionAbierta(register)).thenReturn(UUID.randomUUID());assertThatThrownBy(()->service.validar(input(cash("100","100")),invoice,new BigDecimal("100"))).hasMessageContaining("turno");when(invoice.getSucursalId()).thenReturn(UUID.randomUUID());assertThatThrownBy(()->service.validar(input(cash("100","100")),invoice,new BigDecimal("100"))).hasMessageContaining("sucursal");}
 @Test void turnoCerradoBloqueaCobro(){when(sessions.requerirSesionAbierta(register)).thenThrow(new ReglaNegocioException("Caja cerrada"));assertThatThrownBy(()->service.validar(input(cash("100","100")),invoice,new BigDecimal("100"))).hasMessageContaining("cerrada");}

 @Test void cobroConciliadoNoPuedeAnularse(){
  var note=mock(NotaDebitoVentaDocumento.class);var payment=new NotaDebitoVentaDocumento.Pago(UUID.randomUUID(),cash,"Efectivo","CASH",new BigDecimal("100"),new BigDecimal("100"),BigDecimal.ZERO,null,register,session,"Caja");when(note.pagos()).thenReturn(List.of(payment));
  UUID id=UUID.randomUUID();var m=mock(MovimientoFinanciero.class);when(m.getId()).thenReturn(id);when(m.getEstado()).thenReturn(EstadoMovimientoFinanciero.REGISTERED);when(m.getSesionCajaId()).thenReturn(session);
  when(movements.findByTenantIdAndEmpresaIdAndTipoOrigenAndOrigenId(any(),any(),eq(TipoOrigenMovimiento.DEBIT_NOTE_PAYMENT),eq(payment.id()))).thenReturn(Optional.of(m));when(movements.bloquearPorOrigen(any(),any(),eq(TipoOrigenMovimiento.DEBIT_NOTE_PAYMENT),eq(payment.id()))).thenReturn(Optional.of(m));when(reconciliation.existsByMovimientoFinancieroId(id)).thenReturn(true);
  assertThat(service.anulable(note)).isFalse();assertThatThrownBy(()->service.revertir(note,"Error de prueba")).hasMessageContaining("conciliado");verify(movements,never()).saveAndFlush(any());
 }
 @Test void cobroDeTurnoCerradoNoPuedeAnularse(){
  var note=mock(NotaDebitoVentaDocumento.class);var payment=new NotaDebitoVentaDocumento.Pago(UUID.randomUUID(),cash,"Efectivo","CASH",new BigDecimal("100"),new BigDecimal("100"),BigDecimal.ZERO,null,register,session,"Caja");when(note.pagos()).thenReturn(List.of(payment));
  var m=mock(MovimientoFinanciero.class);when(m.getEstado()).thenReturn(EstadoMovimientoFinanciero.REGISTERED);when(m.getSesionCajaId()).thenReturn(session);when(movements.findByTenantIdAndEmpresaIdAndTipoOrigenAndOrigenId(any(),any(),any(),any())).thenReturn(Optional.of(m));
  doThrow(new ReglaNegocioException("Turno cerrado")).when(sessions).validarMovimientoModificable(session);when(sessions.requerirSesionAbierta(register)).thenThrow(new ReglaNegocioException("Turno cerrado"));assertThat(service.anulable(note)).isFalse();assertThatThrownBy(()->service.revertir(note,"Error de prueba")).hasMessageContaining("cerrado");
 }

 EmitirNotaDebitoVentaInput sinCaja(){var i=input(cash("100","100"));return new EmitirNotaDebitoVentaInput(i.facturaId(),i.fecha(),i.motivoId(),i.descripcion(),i.lineas(),i.idempotencia(),i.condicion(),i.pagos(),null,null);}
 CuentaDineroOpcionDto opcion(UUID id){return new CuentaDineroOpcionDto(id,TipoCuentaDinero.CASH_REGISTER,"Caja",currency,"DOP");}
 @Test void cajaUnicaAbiertaSeResuelveEnBackend(){
  when(accounts.opciones()).thenReturn(List.of(opcion(register)));when(sessions.sesionesDisponiblesCobro(any())).thenReturn(Map.of(register,session));
  var result=service.validar(sinCaja(),invoice,new BigDecimal("100"));assertThat(result.getFirst().cuentaId()).isEqualTo(register);assertThat(result.getFirst().sesionId()).isEqualTo(session);verify(sessions).requerirSesionAbierta(register);
 }
 @Test void ningunaSesionAbiertaBloqueaLaResolucionAutomatica(){
  when(accounts.opciones()).thenReturn(List.of(opcion(register)));when(sessions.sesionesDisponiblesCobro(any())).thenReturn(Map.of());
  assertThatThrownBy(()->service.validar(sinCaja(),invoice,new BigDecimal("100"))).hasMessage("No existe una sesión de caja abierta disponible para registrar este cobro.");verify(sessions,never()).requerirSesionAbierta(any());
 }
 @Test void variasSesionesAbiertasExigenSeleccionExplicita(){
  UUID other=UUID.randomUUID();var c=mock(Caja.class);when(c.getSucursalId()).thenReturn(branch);when(accounts.resolver(TipoCuentaDinero.CASH_REGISTER,other)).thenReturn(new CuentaDineroResolver.CuentaResuelta(TipoCuentaDinero.CASH_REGISTER,other,currency,null,c,null,"Otra"));
  when(accounts.opciones()).thenReturn(List.of(opcion(register),opcion(other)));when(sessions.sesionesDisponiblesCobro(any())).thenReturn(Map.of(register,session,other,UUID.randomUUID()));
  assertThatThrownBy(()->service.validar(sinCaja(),invoice,new BigDecimal("100"))).hasMessageContaining("Seleccione una caja");verify(sessions,never()).requerirSesionAbierta(any());
 }
}
