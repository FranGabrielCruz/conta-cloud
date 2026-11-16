package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.*;
import com.citacloud.springboot.contacloud.app.repositories.BalanzaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BalanzaServiceTest {
    private static final LocalDate FECHA=LocalDate.of(2026,11,15);

    @Test void calculaCambioDeNaturalezaYValidaFiltros() {
        var deudora=Importes.cuenta("DOP",new BigDecimal("-15"),new BigDecimal("20"),BigDecimal.ZERO);
        assertThat(deudora.inicialCredito()).isEqualByComparingTo("15");
        assertThat(deudora.finalDebito()).isEqualByComparingTo("5");
        assertThat(deudora.finalCredito()).isZero();
        var acreedora=Importes.cuenta("DOP",BigDecimal.TEN,BigDecimal.ZERO,new BigDecimal("30"));
        assertThat(acreedora.inicialDebito()).isEqualByComparingTo("10");
        assertThat(acreedora.finalDebito()).isZero();
        assertThat(acreedora.finalCredito()).isEqualByComparingTo("20");
        assertThat(acreedora.diferenciaFinal()).isEqualByComparingTo("-20");
        var filtro=BalanzaService.validar(new Filtro(FECHA,FECHA,null,null,"  Caja  "));
        assertThat(filtro.display()).isEqualTo(Mostrar.MOVIMIENTOS_O_SALDO);
        assertThat(filtro.search()).isEqualTo("Caja");
        assertThatThrownBy(()->BalanzaService.validar(new Filtro(FECHA,FECHA.minusDays(1),null,null,"")))
            .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->BalanzaService.validar(new Filtro(FECHA,FECHA,0,null,"")))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void exigePermisosDeConsultaYExportacion() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(BalanzaRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(BalanzaService.class,()->new BalanzaService(repo,null,null));
            ctx.refresh();
            var s=ctx.getBean(BalanzaService.class);
            var f=new Filtro(FECHA,FECHA,null,null,"");
            DepreciacionServiceTest.autenticar(Set.of("balanza.pdf"),true);
            assertThatThrownBy(()->s.consultar(f,0,10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.reporte(f)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("balanza.ver"),true);
            assertThatThrownBy(()->s.reporte(f)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.consultar(f,0,11)).isInstanceOf(ReglaNegocioException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
