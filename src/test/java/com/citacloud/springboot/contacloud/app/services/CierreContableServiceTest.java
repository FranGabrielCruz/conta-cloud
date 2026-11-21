package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.*;
import com.citacloud.springboot.contacloud.app.repositories.CierreContableRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CierreContableServiceTest {
    @Test void validaSoloPeríodosExistentesYResultado() {
        assertThat(CierreContableService.validarAnteriores(new PeriodosAnteriores(0,0)).correcta()).isTrue();
        assertThat(CierreContableService.validarAnteriores(new PeriodosAnteriores(3,0)).valor()).isEqualTo("Todos cerrados");
        assertThat(CierreContableService.validarAnteriores(new PeriodosAnteriores(3,1)).correcta()).isFalse();
        assertThat(CierreContableService.validarAnteriores(new PeriodosAnteriores(3,2)).valor()).isEqualTo("2 pendientes");
        var perdida=new Resultado(BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ONE);
        assertThat(perdida.neto()).isEqualByComparingTo("-1");
        assertThat(perdida.tipo()).isEqualTo("Pérdida");
        assertThat(new Resultado(BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE).tipo()).isEqualTo("Utilidad");
        assertThat(new Resultado(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO).tipo()).isEqualTo("Sin resultado");
    }

    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void separaPermisosDeConsultaCierreReaperturaYPdf() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(CierreContableRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(CierreContableService.class,()->new CierreContableService(repo,null,null,null,null,null,null));
            ctx.refresh();
            var servicio=ctx.getBean(CierreContableService.class);
            var id=UUID.randomUUID();
            DepreciacionServiceTest.autenticar(Set.of("cierres_contables.ejecutar"),true);
            assertThatThrownBy(()->servicio.detalle(id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.cerrar(id,id,null)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("cierres_contables.ver"),true);
            assertThatThrownBy(()->servicio.cerrar(id,id,null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.reabrir(id,id,id,"Motivo")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.reporte(id,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.configurarResultado(id)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
