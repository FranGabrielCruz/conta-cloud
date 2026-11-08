package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotaDebitoVentaAuthorizationTest {
    @Configuration @EnableMethodSecurity static class SecurityConfig {}
    AnnotationConfigApplicationContext context;NotaDebitoVentaService service;NotaDebitoVentaPdfService pdf;
    NotaDebitoVentaRepository query;FacturaVentaRepository invoices;EmpresaModuloService modules;
    @BeforeEach void setup(){
        query=mock(NotaDebitoVentaRepository.class);invoices=mock(FacturaVentaRepository.class);modules=mock(EmpresaModuloService.class);when(modules.habilitado("VENTAS")).thenReturn(true);
        context=new AnnotationConfigApplicationContext();context.register(SecurityConfig.class);context.registerBean(LocalFileStorageService.class,()->mock(LocalFileStorageService.class));context.registerBean("empresaModuloService",EmpresaModuloService.class,()->modules);
        context.registerBean(NotaDebitoVentaService.class,()->new NotaDebitoVentaService(invoices,query,mock(ImpuestoRepository.class),mock(EmpresaRepository.class),mock(DatosEmpresaRepository.class),mock(PeriodoFiscalRepository.class),mock(IdempotentOperationService.class),mock(AuditoriaService.class),new PurchaseOrderCalculationService(),null));
        context.registerBean(NotaDebitoVentaPdfService.class,()->new NotaDebitoVentaPdfService(query));context.refresh();service=context.getBean(NotaDebitoVentaService.class);pdf=context.getBean(NotaDebitoVentaPdfService.class);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
    void auth(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","prueba","",true,permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    @Test void verNoPermiteCrearEmitirNiDescargar(){
        auth(Set.of("notas_debito.ver"));assertThatThrownBy(()->service.facturas("",0,25)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.preparar(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.calcular(null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.emitir(null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->pdf.generar(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query,invoices);
    }
    @Test void soloVerNoPermiteAnularAunqueSeManipuleLaPeticion(){auth(Set.of("notas_debito.ver"));assertThatThrownBy(()->service.anular(new com.citacloud.springboot.contacloud.app.dto.AnularNotaDebitoVentaInput(UUID.randomUUID(),0,"Motivo",UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query,invoices);}
    @Test void emitirExigeCrearYEmitirAntesDeTodaEscritura(){
        for(var permissions:List.of(Set.of("notas_debito.crear"),Set.of("notas_debito.emitir"),Set.<String>of())){auth(permissions);assertThatThrownBy(()->service.emitir(null)).isInstanceOf(AccessDeniedException.class);}verifyNoInteractions(query,invoices);
    }
    @Test void moduloDeshabilitadoBloqueaInclusoTodosLosPermisos(){
        auth(Set.of("notas_debito.ver","notas_debito.crear","notas_debito.emitir","notas_debito.pdf"));when(modules.habilitado("VENTAS")).thenReturn(false);
        assertThatThrownBy(()->service.obtener(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.emitir(null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->pdf.generar(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query,invoices);
    }
    @Test void consultaRequierePermisoVerYUsaSuRepositorio(){
        UUID id=UUID.randomUUID();auth(Set.of("notas_debito.pdf"));assertThatThrownBy(()->service.obtener(id)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query);auth(Set.of("notas_debito.ver"));service.obtener(id);verify(query).documento(id);
    }
}
