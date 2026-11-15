package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.repositories.CuentaContableRepository;
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

class CatalogoCuentasServiceTest {
    @Test
    void conservaLaNaturalezaElegidaYValidaNombre() {
        var entrada = CatalogoCuentasService.normalizar(new Entrada(null, " Contra-cuenta ", Tipo.MOVIMIENTO,
            Naturaleza.ACREEDORA, null));
        assertThat(entrada.nombre()).isEqualTo("Contra-cuenta");
        assertThat(entrada.naturaleza()).isEqualTo(Naturaleza.ACREEDORA);
        assertThatThrownBy(() -> CatalogoCuentasService.normalizar(
            new Entrada(null, "", Tipo.MOVIMIENTO, Naturaleza.ACREEDORA, null)))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void componeSegmentosConElAnchoConfigurado() {
        assertThat(CatalogoCuentasService.componerCodigo("1.1.01.", "3", 2)).isEqualTo("1.1.01.03");
        assertThat(CatalogoCuentasService.componerCodigo("1-", "100", 2)).isEqualTo("1-100");
        assertThatThrownBy(() -> CatalogoCuentasService.componerCodigo("1".repeat(39) + ".", "1", 2))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Configuration
    @EnableMethodSecurity
    static class Seguridad {}

    @Test
    void permisoDeConsultaNoPermiteCambiosNiImpresion() {
        try (var contexto = new AnnotationConfigApplicationContext()) {
            var repo = mock(CuentaContableRepository.class);
            var modulos = mock(EmpresaModuloService.class);
            when(modulos.habilitado("CONTABILIDAD")).thenReturn(true);
            contexto.register(Seguridad.class);
            contexto.registerBean("empresaModuloService", EmpresaModuloService.class, () -> modulos);
            contexto.registerBean(CatalogoCuentasService.class, () -> new CatalogoCuentasService(repo, null, null, null));
            contexto.refresh();
            var servicio = contexto.getBean(CatalogoCuentasService.class);
            var id = UUID.randomUUID();
            DepreciacionServiceTest.autenticar(Set.of("catalogo_cuentas.ver"), true);
            assertThatThrownBy(() -> servicio.crear(null, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.editar(id, 0, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.desactivar(id, 0)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.activar(id, 0)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.reporte(null)).isInstanceOf(AccessDeniedException.class);
            DepreciacionServiceTest.autenticar(Set.of("catalogo_cuentas.imprimir"), true);
            assertThatThrownBy(() -> servicio.reporte(null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

}
