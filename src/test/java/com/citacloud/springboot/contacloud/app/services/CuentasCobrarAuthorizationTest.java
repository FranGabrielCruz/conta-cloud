package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CuentasCobrarAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void requierePermisosDeConsultaYReporteAntesDeAccederALosDatos(){
        try(var contexto=new AnnotationConfigApplicationContext()){
            var repo=mock(CuentasCobrarConsultaRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("VENTAS")).thenReturn(true);
            contexto.register(Seguridad.class);
            contexto.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            contexto.registerBean(CuentasCobrarService.class,()->new CuentasCobrarService(repo,null,null));
            contexto.refresh();
            var consulta=contexto.getBean(CuentasCobrarService.class);
            var pdf=new CuentasCobrarPdfService(consulta,null);
            autenticar("cuentas_cobrar.reportar");
            assertThatThrownBy(()->consulta.detalle(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->consulta.consultar(null,false,0,10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->pdf.cartera(null)).isInstanceOf(AccessDeniedException.class);
            autenticar("cuentas_cobrar.ver");
            assertThatThrownBy(()->pdf.cartera(null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->pdf.antiguedad(null,null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        }finally{SecurityContextHolder.clearContext();}
    }

    private static void autenticar(String permiso){
        var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,Set.of(permiso));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
