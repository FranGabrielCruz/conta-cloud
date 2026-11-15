package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Filtro;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Lado;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Saldo;
import com.citacloud.springboot.contacloud.app.repositories.LibroMayorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

class LibroMayorServiceTest {
    private static final LocalDate HOY=LocalDate.of(2026,11,15);
    private static final UUID CUENTA=UUID.randomUUID();

    @Test void validaCuentaFechasYSignoContable() {
        assertThat(LibroMayorService.validar(new Filtro(CUENTA,HOY,HOY,null,null,"  Caja  ")).search()).isEqualTo("Caja");
        assertThatThrownBy(()->LibroMayorService.validar(new Filtro(null,HOY,HOY,null,null,"")))
            .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->LibroMayorService.validar(new Filtro(CUENTA,HOY,HOY.minusDays(1),null,null,"")))
            .isInstanceOf(ReglaNegocioException.class);
        assertThat(Saldo.de(new BigDecimal("-152.00"))).isEqualTo(new Saldo(new BigDecimal("152.00"),Lado.CR));
        assertThat(Saldo.de(BigDecimal.ZERO).lado()).isEqualTo(Lado.CERO);
        assertThat(Saldo.de(BigDecimal.ONE).lado()).isEqualTo(Lado.DB);
    }

    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void consultaYCuentasYExportacionValidanPermisos() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(LibroMayorRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(LibroMayorService.class,()->new LibroMayorService(repo,null,null));
            ctx.refresh();
            var s=ctx.getBean(LibroMayorService.class);
            var f=new Filtro(CUENTA,HOY,HOY,null,null,"");
            DepreciacionServiceTest.autenticar(Set.of("libro_mayor.pdf"),true);
            assertThatThrownBy(s::cuentas).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.consultar(f,0,10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.reporte(f)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("libro_mayor.ver"),true);
            assertThatThrownBy(()->s.reporte(f)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.consultar(f,0,11)).isInstanceOf(ReglaNegocioException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
