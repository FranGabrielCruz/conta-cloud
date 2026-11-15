package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.Filtro;
import com.citacloud.springboot.contacloud.app.repositories.LibroDiarioRepository;
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

class LibroDiarioServiceTest {
    private static final LocalDate HOY=LocalDate.of(2026,11,15);

    @Test void validaFechasIncluyentesYBusqueda() {
        assertThat(LibroDiarioService.validar(new Filtro(HOY,HOY,null,null,"  Caja  ")).buscar()).isEqualTo("Caja");
        assertThatThrownBy(()->LibroDiarioService.validar(new Filtro(HOY,null,null,null,""))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->LibroDiarioService.validar(new Filtro(HOY,HOY.minusDays(1),null,null,"")))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void consultaYExportacionRequierenPermisosSeparados() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(LibroDiarioRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(LibroDiarioService.class,()->new LibroDiarioService(repo,null,null));
            ctx.refresh();
            var s=ctx.getBean(LibroDiarioService.class);
            var f=new Filtro(HOY,HOY,null,null,"");
            DepreciacionServiceTest.autenticar(Set.of("libro_diario.pdf"),true);
            assertThatThrownBy(()->s.consultar(f,0,10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.reporte(f)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("libro_diario.ver"),true);
            assertThatThrownBy(()->s.reporte(f)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.consultar(f,0,11)).isInstanceOf(ReglaNegocioException.class);
            verifyNoInteractions(repo);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
