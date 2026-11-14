package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.Entrada;
import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.Prioridad;
import com.citacloud.springboot.contacloud.app.repositories.MantenimientoRepository;
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

class MantenimientoServiceTest {
    @Test void validaImportesYProgramacionEnBackend() {
        var fecha=LocalDate.of(2026,11,14);
        assertThat(MantenimientoService.validar(entrada(BigDecimal.ZERO,fecha.plusDays(1))).estimado()).isEqualByComparingTo("0.00");
        assertThatThrownBy(()->MantenimientoService.validar(entrada(new BigDecimal("1.234"),null))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->MantenimientoService.validar(entrada(BigDecimal.ONE.negate(),null))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->MantenimientoService.validar(entrada(BigDecimal.ONE,fecha))).isInstanceOf(ReglaNegocioException.class);
    }

    @Configuration @EnableMethodSecurity
    static class Seguridad {}

    @Test void consultaNoConcedePermisosParaOperacionesNiPdf() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(MantenimientoRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("ACTIVOS_FIJOS")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(MantenimientoService.class,()->new MantenimientoService(repo,null,null,null,null,30));
            ctx.refresh();
            var s=ctx.getBean(MantenimientoService.class);
            var id=UUID.randomUUID();
            DepreciacionServiceTest.autenticar(Set.of("mantenimiento_activos.imprimir"),true);
            assertThatThrownBy(()->s.detalle(id)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("mantenimiento_activos.ver"),true);
            assertThatThrownBy(()->s.crear(null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.editar(id,0,null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.iniciar(id,0,true,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.completar(id,0,null,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.cancelar(id,0,"Motivo",id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.documento(id)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally { SecurityContextHolder.clearContext(); }
    }

    private static Entrada entrada(BigDecimal costo,LocalDate proxima) {
        return new Entrada(UUID.randomUUID(),"PREVENTIVO",Prioridad.MEDIA,LocalDate.of(2026,11,14),
            proxima,null,null,UUID.randomUUID(),costo,null,"Cambiar filtros",null);
    }
}
