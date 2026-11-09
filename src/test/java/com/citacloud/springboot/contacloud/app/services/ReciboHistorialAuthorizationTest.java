package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReciboHistorialAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Security {}
    @Test void permisosIndependientesSeAplicanAntesDeLeerOEscribir(){
        try(var context=new AnnotationConfigApplicationContext()){
            var repo=mock(CobroClienteRepository.class);var mod=mock(EmpresaModuloService.class);
            when(mod.habilitado("VENTAS")).thenReturn(true);
            context.register(Security.class);
            context.registerBean("empresaModuloService",EmpresaModuloService.class,()->mod);
            context.registerBean(CobroClienteService.class,()->new CobroClienteService(repo,null,null,null,null,null,null,null,null,null));
            context.registerBean(ReciboHistorialService.class,()->new ReciboHistorialService(context.getBean(CobroClienteService.class),repo,null,null,null));
            context.registerBean(CobroClientePdfService.class,()->new CobroClientePdfService(context.getBean(ReciboHistorialService.class),null));
            context.refresh();
            UUID id=UUID.randomUUID();
            autenticar("recibos.imprimir");
            assertThatThrownBy(()->context.getBean(ReciboHistorialService.class).detalle(id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->context.getBean(ReciboHistorialService.class).buscar("",null,null,null,0,10)).isInstanceOf(AccessDeniedException.class);
            autenticar("recibos.ver");
            assertThatThrownBy(()->context.getBean(CobroClientePdfService.class).generar(id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->context.getBean(CobroClienteService.class).anular(id,"Motivo")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->context.getBean(CobroClienteService.class).anularConResultado(id,"Motivo"))
                .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        }finally{SecurityContextHolder.clearContext();}
    }
    private void autenticar(String permiso){
        var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","prueba","",true,Set.of(permiso));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
}
