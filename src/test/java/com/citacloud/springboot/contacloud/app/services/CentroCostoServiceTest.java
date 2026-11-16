package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Entrada;
import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Filtro;
import com.citacloud.springboot.contacloud.app.repositories.CentroCostoRepository;
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

class CentroCostoServiceTest {
    @Test void normalizaCamposYRechazaNombreVacio() {
        var entrada=CentroCostoService.validar(new Entrada(" Desarrollo ",null,"  "));
        assertThat(entrada).isEqualTo(new Entrada("Desarrollo",null,null));
        assertThatThrownBy(()->CentroCostoService.validar(new Entrada(" ",null,null)))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Configuration @EnableMethodSecurity static class Seguridad {}

    @Test void exigePermisosGranularesAntesDeConsultarOModificar() {
        try(var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(CentroCostoRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(CentroCostoService.class,()->new CentroCostoService(repo,null,null));
            ctx.refresh();
            var servicio=ctx.getBean(CentroCostoService.class);
            var id=UUID.randomUUID();
            var entrada=new Entrada("Ventas",null,null);
            DepreciacionServiceTest.autenticar(Set.of("centros_costos.crear"),true);
            assertThatThrownBy(()->servicio.listar(new Filtro(null,""),0,10)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.crear(entrada,id)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("centros_costos.ver"),true);
            assertThatThrownBy(()->servicio.editar(id,0,entrada)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.activar(id,0)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->servicio.desactivar(id,0)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
