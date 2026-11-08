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

class NotaCreditoVentaAuthorizationTest {
    @Configuration @EnableMethodSecurity static class SecurityConfig {}
    AnnotationConfigApplicationContext context;NotaCreditoVentaService service;NotaCreditoVentaPdfService pdf;
    NotaCreditoVentaConsultaRepository query;FacturaVentaRepository invoices;EmpresaModuloService modules;
    @BeforeEach void setup(){
        query=mock(NotaCreditoVentaConsultaRepository.class);invoices=mock(FacturaVentaRepository.class);modules=mock(EmpresaModuloService.class);when(modules.habilitado("VENTAS")).thenReturn(true);
        context=new AnnotationConfigApplicationContext();context.register(SecurityConfig.class);context.registerBean(LocalFileStorageService.class,()->mock(LocalFileStorageService.class));context.registerBean("empresaModuloService",EmpresaModuloService.class,()->modules);
        context.registerBean(NotaCreditoVentaService.class,()->new NotaCreditoVentaService(invoices,mock(LineaFacturaVentaRepository.class),mock(MotivoDevolucionVentaRepository.class),query,mock(EmpresaRepository.class),mock(DatosEmpresaRepository.class),mock(PeriodoFiscalRepository.class),mock(IdempotentOperationService.class),mock(AuditoriaService.class)));
        context.registerBean(NotaCreditoVentaPdfService.class,()->new NotaCreditoVentaPdfService(query));context.refresh();service=context.getBean(NotaCreditoVentaService.class);pdf=context.getBean(NotaCreditoVentaPdfService.class);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
    void auth(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","prueba","",true,permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    @Test void verNoPermiteCrearEmitirNiDescargar(){
        auth(Set.of("notas_credito.ver"));assertThatThrownBy(()->service.facturas("",0,25)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.preparar(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.calcular(null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.emitir(null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->pdf.generar(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query,invoices);
    }
    @Test void soloVerNoPermiteAnularAunqueSeManipuleLaPeticion(){auth(Set.of("notas_credito.ver"));assertThatThrownBy(()->service.anular(new com.citacloud.springboot.contacloud.app.dto.AnularNotaCreditoVentaInput(UUID.randomUUID(),0,"Motivo",UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query,invoices);}
    @Test void emitirExigeCrearYEmitirAntesDeTodaEscritura(){
        for(var permissions:List.of(Set.of("notas_credito.crear"),Set.of("notas_credito.emitir"),Set.<String>of())){auth(permissions);assertThatThrownBy(()->service.emitir(null)).isInstanceOf(AccessDeniedException.class);}verifyNoInteractions(query,invoices);
    }
    @Test void moduloDeshabilitadoBloqueaInclusoTodosLosPermisos(){
        auth(Set.of("notas_credito.ver","notas_credito.crear","notas_credito.emitir","notas_credito.pdf"));when(modules.habilitado("VENTAS")).thenReturn(false);
        assertThatThrownBy(()->service.obtener(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.emitir(null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->pdf.generar(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query,invoices);
    }
    @Test void consultaRequierePermisoVerYUsaSuRepositorio(){
        UUID id=UUID.randomUUID();auth(Set.of("notas_credito.pdf"));assertThatThrownBy(()->service.obtener(id)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(query);auth(Set.of("notas_credito.ver"));service.obtener(id);verify(query).documento(id);
    }
}
