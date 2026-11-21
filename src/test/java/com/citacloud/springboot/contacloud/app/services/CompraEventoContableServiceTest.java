package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.ProductRepository;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CompraEventoContableServiceTest {
 private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID();
 private final ProductRepository productos=mock(ProductRepository.class);
 private final ConfiguracionMotorService config=mock(ConfiguracionMotorService.class);
 private final AccountingPostingService posting=mock(AccountingPostingService.class);
 private final CompraEventoContableService eventos=new CompraEventoContableService(productos,config,posting);
 @BeforeEach void autenticar() {
  var p=new TenantPrincipal(UUID.randomUUID(),tenant,empresa,null,"TEST","Prueba","operador","",true,true,Set.of(),Set.of("facturas_proveedores.registrar"));
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
 }
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void rechazaOtraEmpresaUOtroTenantAntesDeConsultarOContabilizar() {
  for(var scope:List.of(new UUID[]{UUID.randomUUID(),empresa},new UUID[]{tenant,UUID.randomUUID()})) {
   var f=mock(FacturaProveedor.class);when(f.getTenantId()).thenReturn(scope[0]);when(f.getEmpresaId()).thenReturn(scope[1]);
   assertThatThrownBy(()->eventos.registrada(f,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
   assertThatThrownBy(()->eventos.anulada(f,"motivo")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
  verifyNoInteractions(productos,posting,config);
 }
 @Test void borradorNoEsEventoYAutomatizacionApagadaNoConsultaProductos() {
  var f=mock(FacturaProveedor.class);when(f.getTenantId()).thenReturn(tenant);when(f.getEmpresaId()).thenReturn(empresa);
  when(f.getEstado()).thenReturn(EstadoFacturaProveedor.DRAFT);
  assertThatThrownBy(()->eventos.registrada(f,null)).hasMessageContaining("Solo se contabilizan");
  when(f.getEstado()).thenReturn(EstadoFacturaProveedor.REGISTERED);
  assertThat(eventos.registrada(f,null)).isNull();verifyNoInteractions(productos,posting);
 }
}

