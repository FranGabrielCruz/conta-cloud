package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.repositories.AsientoContableRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsientoContableServiceTest {
    @Test void partidaDobleUsaDecimalesExactosYRechazaLineasVacias() {
        var lineas=List.of(linea("0.10","0"),linea("0.20","0"),linea("0","0.30"));
        AsientoContableService.partidaDoble(lineas);
        assertThat(AsientoContableService.totales(lineas).diferencia()).isEqualByComparingTo("0.00");
        assertThatThrownBy(()->AsientoContableService.partidaDoble(List.of(linea("0.10","0"),linea("0","0.20"))))
            .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->AsientoContableService.partidaDoble(List.of(linea("0","0"),linea("0","0"))))
            .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->AsientoContableService.normalizar(entrada(linea("1","1"))))
            .hasMessageContaining("solo puede");
        assertThatThrownBy(()->AsientoContableService.normalizar(entrada(linea("1.001","0"))))
            .hasMessageContaining("dos decimales");
        assertThatThrownBy(()->AsientoContableService.normalizar(entrada(linea("-1","0"))))
            .hasMessageContaining("no negativo");
    }

    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void consultarNoAutorizaEscrituraReversionNiPdf() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(AsientoContableRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(AsientoContableService.class,()->new AsientoContableService(repo,null,null,null,null));
            ctx.refresh();
            var s=ctx.getBean(AsientoContableService.class);
            var id=UUID.randomUUID();
            DepreciacionServiceTest.autenticar(Set.of("asientos.ver"),true);
            assertThatThrownBy(()->s.crear(null,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.editar(id,0,null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.contabilizar(id,0,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.guardarYContabilizar(null,0,null,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.reversar(id,0,null,null,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->s.reporte(id)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {SecurityContextHolder.clearContext();}
    }

    private static Movimiento linea(String debe,String haber) {
        return new Movimiento(UUID.randomUUID(),null,new BigDecimal(debe),new BigDecimal(haber));
    }

    private static Entrada entrada(Movimiento linea) {
        return new Entrada(LocalDate.of(2026,11,15),Tipo.DIARIO,UUID.randomUUID(),"Prueba",null,null,List.of(linea));
    }
}
