package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;import com.citacloud.springboot.contacloud.app.models.*;import com.citacloud.springboot.contacloud.app.repositories.*;import com.citacloud.springboot.contacloud.app.security.*;
import org.junit.jupiter.api.*;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;
import java.math.*;import java.time.*;import java.util.*;import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class NotaDebitoVentaServiceTest{
 final FacturaVentaRepository invoices=mock(FacturaVentaRepository.class);final NotaDebitoVentaRepository notes=mock(NotaDebitoVentaRepository.class);
 final CobroContadoDocumentoService cobros=mock(CobroContadoDocumentoService.class);
 final IdempotentOperationService idempotencia=mock(IdempotentOperationService.class);
 final ImpuestoRepository taxes=mock(ImpuestoRepository.class);final EmpresaRepository companies=mock(EmpresaRepository.class);final PeriodoFiscalRepository periods=mock(PeriodoFiscalRepository.class);
 final UUID invoice=UUID.randomUUID(),customer=UUID.randomUUID(),taxId=UUID.randomUUID(),reason=UUID.randomUUID();
 FacturaVenta f;NotaDebitoVentaService service;LocalDate today=LocalDate.now(ZoneId.of("America/Santo_Domingo"));
 @BeforeEach void setup(){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,Set.of());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
 f=mock(FacturaVenta.class);when(f.getId()).thenReturn(invoice);when(f.getSucursalId()).thenReturn(UUID.randomUUID());when(f.getClienteId()).thenReturn(customer);when(f.getStatus()).thenReturn(EstadoFacturaVenta.CONFIRMED);when(f.getFecha()).thenReturn(today.minusDays(1));when(f.getTotal()).thenReturn(new BigDecimal("100"));when(f.getMonedaCodigo()).thenReturn("USD");when(f.getClienteNombre()).thenReturn("Cliente");
 when(invoices.findByIdAndTenantIdAndEmpresaId(eq(invoice),any(),any())).thenReturn(Optional.of(f));when(invoices.bloquear(eq(invoice),any(),any())).thenReturn(Optional.of(f));when(idempotencia.begin(any(),any(),any(),any(),any())).thenReturn(new IdempotentOperationService.Start(true,null,null));
 var e=mock(Empresa.class);when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");when(e.getNombre()).thenReturn("Empresa");when(companies.findByIdAndTenantId(any(),any())).thenReturn(Optional.of(e));
 var t=mock(Impuesto.class);when(t.isActivo()).thenReturn(true);when(t.getTipo()).thenReturn(TipoImpuesto.PERCENTAGE);when(t.getPorcentaje()).thenReturn(new BigDecimal("18"));when(t.getNombre()).thenReturn("ITBIS");when(taxes.findByIdAndTenantIdAndEmpresaId(eq(taxId),any(),any())).thenReturn(Optional.of(t));when(notes.motivo(reason)).thenReturn("Cargo adicional");when(notes.saldo(invoice)).thenReturn(new BigDecimal("40"));
 service=new NotaDebitoVentaService(invoices,notes,taxes,companies,mock(DatosEmpresaRepository.class),periods,idempotencia,mock(AuditoriaService.class),new PurchaseOrderCalculationService(),null,cobros);
 }
 @AfterEach void clean(){SecurityContextHolder.clearContext();}
 EmitirNotaDebitoVentaInput input(List<EmitirNotaDebitoVentaInput.Linea> lines){return new EmitirNotaDebitoVentaInput(invoice,today,reason,"Servicio adicional",lines,UUID.randomUUID());}
 EmitirNotaDebitoVentaInput.Linea line(String qty,String price,String discount,UUID tax){return new EmitirNotaDebitoVentaInput.Linea("Cargo",new BigDecimal(qty),new BigDecimal(price),new BigDecimal(discount),tax);}
 @Test void calculaVariosCargosDescuentoImpuestoYMonedaSinModificarFactura(){var d=service.calcular(input(List.of(line("2","250","50",taxId),line("1","100","0",null))));assertThat(d.subtotal()).isEqualByComparingTo("600");assertThat(d.descuento()).isEqualByComparingTo("50");assertThat(d.impuesto()).isEqualByComparingTo("81");assertThat(d.total()).isEqualByComparingTo("631");assertThat(d.saldoPosterior()).isEqualByComparingTo("671");assertThat(d.moneda()).isEqualTo("USD");assertThat(f.getTotal()).isEqualByComparingTo("100");verify(invoices,never()).save(any());verify(notes,never()).incrementar(any(),any());}
 @Test void facturaSinClienteSePreparaYCalculaComoConsumidorFinal(){
  when(f.getClienteId()).thenReturn(null);when(f.getClienteNombre()).thenReturn("Nombre que no debe usarse");when(f.getCondicionPago()).thenReturn("Contado");when(notes.saldo(invoice)).thenReturn(BigDecimal.ZERO);
  var p=service.preparar(invoice);assertThat(p.cliente()).isEqualTo("Consumidor Final");assertThat(p.estado()).isEqualTo("Pagada");
  var preview=input(List.of(line("1","100","0",null)));var d=service.calcular(new EmitirNotaDebitoVentaInput(preview.facturaId(),preview.fecha(),preview.motivoId(),preview.descripcion(),preview.lineas(),preview.idempotencia(),TipoCondicionPago.CASH,List.of(),null,null));assertThat(d.cliente()).isEqualTo("Consumidor Final");assertThat(d.identificacion()).isNull();
  verify(notes,never()).cliente(isNull(),anyBoolean());verify(notes,never()).incrementar(any(),any());verify(invoices,never()).save(any());
 }
 @Test void facturaSinClienteNoEmiteCuentaPorCobrar(){
  when(f.getClienteId()).thenReturn(null);assertThatThrownBy(()->service.emitir(input(List.of(line("1","100","0",null))))).hasMessage("Para emitir esta nota de débito a crédito debe existir un cliente identificado en la factura.");
  verify(notes,never()).incrementar(any(),any());verify(notes,never()).insertar(any());verify(notes,never()).siguiente();
 }
 @Test void nombreClienteVacioTieneRepresentacionSegura(){for(String name:Arrays.asList(null,"","  ")){when(f.getClienteNombre()).thenReturn(name);assertThat(service.preparar(invoice).cliente()).isEqualTo("Consumidor Final");}}
 @Test void pagadaConClienteEsElegible(){when(notes.saldo(invoice)).thenReturn(BigDecimal.ZERO);var d=service.calcular(input(List.of(line("1","100","0",null))));assertThat(d.saldoAnterior()).isZero();assertThat(d.saldoPosterior()).isEqualByComparingTo("100");}
 @Test void facturaAnuladaNoElegible(){when(f.getStatus()).thenReturn(EstadoFacturaVenta.VOIDED);assertThatThrownBy(()->service.calcular(input(List.of(line("1","100","0",null))))).isInstanceOf(ReglaNegocioException.class);}
 @Test void facturaAjenaNoDisponible(){when(invoices.findByIdAndTenantIdAndEmpresaId(any(),any(),any())).thenReturn(Optional.empty());assertThatThrownBy(()->service.calcular(input(List.of(line("1","100","0",null))))).isInstanceOf(RecursoNoEncontradoException.class);}
 @Test void impuestoAjenoNoDisponible(){when(taxes.findByIdAndTenantIdAndEmpresaId(any(),any(),any())).thenReturn(Optional.empty());assertThatThrownBy(()->service.calcular(input(List.of(line("1","100","0",taxId))))).isInstanceOf(ReglaNegocioException.class);}
 @Test void noPermiteDescuentoSuperiorNiTotalCero(){assertThatThrownBy(()->service.calcular(input(List.of(line("1","100","101",null))))).isInstanceOf(ReglaNegocioException.class);assertThatThrownBy(()->service.calcular(input(List.of(line("1","100","100",null))))).isInstanceOf(ReglaNegocioException.class);}
 @Test void periodoCerradoBloquea(){var period=mock(PeriodoFiscal.class);when(period.getEstado()).thenReturn(PeriodoFiscal.Estado.CERRADO);when(periods.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(any(),any(),any(),any())).thenReturn(Optional.of(period));assertThatThrownBy(()->service.calcular(input(List.of(line("1","100","0",null))))).hasMessageContaining("período fiscal");}
 @Test void noAceptaCantidadNegativaNiPrecisionExcesiva(){for(String q:List.of("-1","0","1.12345","1000000000000000"))assertThatThrownBy(()->service.calcular(input(List.of(line(q,"100","0",null))))).isInstanceOf(ReglaNegocioException.class);}
 @Test void motivoAnulacionObligatorio(){for(String text:List.of(""," ","ab"))assertThatThrownBy(()->service.anular(new AnularNotaDebitoVentaInput(UUID.randomUUID(),0,text,UUID.randomUUID()))).isInstanceOf(ReglaNegocioException.class);}

 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
 void contadoConYSinClienteCobraSinCrearCuentaPendiente(boolean identified){
  if(!identified)when(f.getClienteId()).thenReturn(null);var old=input(List.of(line("1","100","0",null)));
  var request=new EmitirNotaDebitoVentaInput(old.facturaId(),old.fecha(),old.motivoId(),old.descripcion(),old.lineas(),old.idempotencia(),TipoCondicionPago.CASH,List.of(),UUID.randomUUID(),null);
  var payment=new NotaDebitoVentaDocumento.Pago(UUID.randomUUID(),UUID.randomUUID(),"Efectivo","CASH",new BigDecimal("100"),new BigDecimal("110"),BigDecimal.TEN,null,request.cajaId(),UUID.randomUUID(),"Caja");
  when(cobros.validar(eq(request),eq(f),any())).thenReturn(List.of(payment));when(notes.siguiente()).thenReturn("NDV-TEST");
  var d=service.emitir(request);assertThat(d.condicionNota()).isEqualTo(TipoCondicionPago.CASH);assertThat(d.pagado()).isEqualByComparingTo("100");assertThat(d.saldoNota()).isZero();assertThat(d.cambio()).isEqualByComparingTo("10");assertThat(d.saldoPosterior()).isEqualByComparingTo(d.saldoAnterior());verify(notes,never()).incrementar(any(),any());verify(cobros).registrar(d);verify(invoices,never()).save(any());if(!identified)assertThat(d.cliente()).isEqualTo("Consumidor Final");
 }
 @Test void creditoNoAdmitePagosYCondicionEsObligatoria(){
  var old=input(List.of(line("1","100","0",null)));
  assertThatThrownBy(()->service.calcular(new EmitirNotaDebitoVentaInput(old.facturaId(),old.fecha(),old.motivoId(),old.descripcion(),old.lineas(),old.idempotencia(),null,List.of(),null,null))).hasMessageContaining("condición");
  assertThatThrownBy(()->service.calcular(new EmitirNotaDebitoVentaInput(old.facturaId(),old.fecha(),old.motivoId(),old.descripcion(),old.lineas(),old.idempotencia(),TipoCondicionPago.CREDIT,List.of(new AplicacionCobroInput(UUID.randomUUID(),BigDecimal.ONE,null,null,null,null)),null,null))).hasMessageContaining("no admite cobros");
 }
}
