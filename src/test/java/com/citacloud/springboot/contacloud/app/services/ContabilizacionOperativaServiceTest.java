package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import com.citacloud.springboot.contacloud.app.models.FacturaVenta;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ContabilizacionOperativaServiceTest {
 @org.junit.jupiter.api.BeforeEach void autenticar() {
  var p=new com.citacloud.springboot.contacloud.app.security.TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"TEST","Prueba","test","",true,true,Set.of(),Set.of());
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
 }
 @org.junit.jupiter.api.AfterEach void limpiar() {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 private ContabilizacionOperativaService adaptador(ConfiguracionMotorService config,AsientoContableService asientos) {
  return new ContabilizacionOperativaService(config,new AccountingPostingService(IntegracionesContablesTestSupport.registro(mock(CuentaFinancieraContableService.class)),config,asientos),mock(MonedaRepository.class));
 }
 @Test void facturaDevengadaUsaImportesConfirmadosFuenteYRedondeoCuadrado() {
  var config=mock(ConfiguracionMotorService.class);when(config.efectivo(Modulo.VENTAS)).thenReturn(true);
  var asientos=mock(AsientoContableService.class);var s=adaptador(config,asientos);
  var f=mock(FacturaVenta.class);var id=UUID.randomUUID();var moneda=UUID.randomUUID();var fecha=LocalDate.of(2026,11,21);
  when(f.getId()).thenReturn(id);when(f.getNumero()).thenReturn("FAC-001");when(f.getFecha()).thenReturn(fecha);
  when(f.getMonedaId()).thenReturn(moneda);when(f.getTotal()).thenReturn(new BigDecimal("118.0050"));
  when(f.getImpuestoTotal()).thenReturn(new BigDecimal("18.0000"));
  s.factura(f);
  @SuppressWarnings("unchecked") ArgumentCaptor<List<LineaConfigurada>> captor=ArgumentCaptor.forClass(List.class);
  verify(asientos).registrarAutomatico(eq(new Fuente(Origen.VENTAS,"FACTURA",id,"FAC-001","EMISION")),eq(fecha),eq(moneda),anyString(),captor.capture(),eq(0L),anyString(),anyString());
  var lineas=captor.getValue();
  assertThat(lineas).extracting(LineaConfigurada::clave).containsExactly("ACCOUNTS_RECEIVABLE_DEFAULT","SALES_REVENUE_DEFAULT","SALES_TAX_PAYABLE");
  assertThat(lineas.stream().map(x->x.debito().subtract(x.credito())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");
  assertThat(lineas.getFirst().debito()).isEqualByComparingTo("118.01");
 }
 @Test void maestroOModuloApagadoNoGeneraAsiento() {
  var config=mock(ConfiguracionMotorService.class);var asientos=mock(AsientoContableService.class);
  assertThat(adaptador(config,asientos).factura(mock(FacturaVenta.class))).isNull();
  verifyNoInteractions(asientos);
 }
 @Test void errorContableSePropagaATransaccionOperativa() {
  var config=mock(ConfiguracionMotorService.class);when(config.efectivo(Modulo.VENTAS)).thenThrow(new ReglaNegocioException("Configuración incompleta"));
  var asientos=mock(AsientoContableService.class);
  assertThatThrownBy(()->adaptador(config,asientos).factura(mock(FacturaVenta.class))).hasMessageContaining("incompleta");
  verifyNoInteractions(asientos);
 }
}

