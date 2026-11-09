package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.repositories.ActivoFijoRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.citacloud.springboot.contacloud.app.services.ActivoFijoService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivoFijoServiceTest {
    private final LocalDate fecha=LocalDate.of(2026,11,9);
    private final UUID moneda=UUID.randomUUID();
    private final UUID sucursal=UUID.randomUUID();

    @Configuration @EnableMethodSecurity
    static class Seguridad {}

    @Test
    void validaCostoResidualYFechasSinModificarLosValoresValidos() {
        var bien=new Depreciacion(true,Metodo.LINEA_RECTA,60,new BigDecimal("5000"),fecha.plusMonths(1));
        assertThat(validar(datos("Activo",new BigDecimal("75000"),bien)).depreciacion().inicio()).isEqualTo(fecha.plusMonths(1));
        assertThatThrownBy(()->validar(datos(" ",BigDecimal.ONE,bien))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->validar(datos("Activo",BigDecimal.ZERO,bien))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->validar(datos("Activo",BigDecimal.ONE,bien))).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("residual");
        var sinMetodo=new Depreciacion(true,null,60,BigDecimal.ZERO,fecha);
        assertThatThrownBy(()->validar(datos("Activo",BigDecimal.ONE,sinMetodo))).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("método");
        var anterior=new Depreciacion(true,Metodo.LINEA_RECTA,60,BigDecimal.ZERO,fecha.minusDays(1));
        assertThatThrownBy(()->validar(datos("Activo",BigDecimal.ONE,anterior))).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("inicio");
    }

    @Test
    void rechazaParametrosDeDepreciacionParaUnActivoNoDepreciable() {
        var terreno=new Depreciacion(false,null,null,null,null);
        assertThat(validar(datos("Terreno",BigDecimal.TEN,terreno)).depreciacion()).isEqualTo(terreno);
        var inconsistente=new Depreciacion(false,Metodo.LINEA_RECTA,null,null,null);
        assertThatThrownBy(()->validar(datos("Terreno",BigDecimal.TEN,inconsistente))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void verificaPermisosEnBackendAntesDeConsultarOEscribir() {
        try (var contexto=new AnnotationConfigApplicationContext()) {
            var repo=mock(ActivoFijoRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado(MODULO)).thenReturn(true);
            contexto.register(Seguridad.class);
            contexto.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            contexto.registerBean(ActivoFijoService.class,()->new ActivoFijoService(repo,null,null,null));
            contexto.refresh();
            var svc=contexto.getBean(ActivoFijoService.class);
            autenticar(REPORTAR,CREAR,EDITAR);
            assertThatThrownBy(()->svc.consultar(null,0,10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->svc.detalle(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->svc.reporte(null)).isInstanceOf(AccessDeniedException.class);
            autenticar(VER);
            assertThatThrownBy(()->svc.crear(null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->svc.editar(UUID.randomUUID(),0,null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->new ActivoFijoPdfService(svc,null).generar(null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Datos datos(String nombre,BigDecimal costo,Depreciacion dep) {
        return new Datos(nombre,"TERRENOS",null,null,null,new Adquisicion(fecha,costo,moneda,null,null,null),
            new Asignacion(sucursal,null,null),dep,null);
    }

    private static void autenticar(String... permisos) {
        var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,Set.of(permisos));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
