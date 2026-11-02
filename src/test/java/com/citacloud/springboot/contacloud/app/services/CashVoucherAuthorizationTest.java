package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.repositories.ValeCajaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CashVoucherAuthorizationTest {
 @Configuration @EnableMethodSecurity static class SecurityConfig {}
 @Test void backendRequiresModuleAndReadAndPrintPermissionsBeforeLookup(){
  var repo=mock(ValeCajaRepository.class);var module=mock(EmpresaModuloService.class);
  try(var context=new AnnotationConfigApplicationContext()){
   context.register(SecurityConfig.class);context.registerBean("empresaModuloService",EmpresaModuloService.class,()->module);context.registerBean(CashVoucherDocumentService.class,()->new CashVoucherDocumentService(repo));context.refresh();var service=context.getBean(CashVoucherDocumentService.class);
   for(var permissions:List.of(Set.of("vales_caja.ver"),Set.of("vales_caja.imprimir"),Set.of("vales_caja.ver","vales_caja.imprimir"))){
    boolean both=permissions.size()==2;when(module.habilitado("CAJA_BANCOS")).thenReturn(!both);var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"DEMO","Juan","juan","",true,true,Set.of(),permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    assertThatThrownBy(()->service.cargar(UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThatThrownBy(()->service.generar(UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
   }
   verifyNoInteractions(repo);
  }finally{SecurityContextHolder.clearContext();}
 }
}
