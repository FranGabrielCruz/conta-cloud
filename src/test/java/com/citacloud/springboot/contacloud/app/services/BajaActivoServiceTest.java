package com.citacloud.springboot.contacloud.app.services;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import com.citacloud.springboot.contacloud.app.repositories.BajaActivoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BajaActivoServiceTest {
    @Configuration
    @EnableMethodSecurity
    static class Seguridad {}

    @Test
    void permisoDeConsultaNoAutorizaCambiosNiImpresion() {
        try (var contexto = new AnnotationConfigApplicationContext()) {
            var repo = mock(BajaActivoRepository.class);
            var modulos = mock(EmpresaModuloService.class);
            when(modulos.habilitado("ACTIVOS_FIJOS")).thenReturn(true);
            contexto.register(Seguridad.class);
            contexto.registerBean("empresaModuloService", EmpresaModuloService.class, () -> modulos);
            contexto.registerBean(BajaActivoService.class, () -> new BajaActivoService(repo, null, null, null));
            contexto.refresh();
            var servicio = contexto.getBean(BajaActivoService.class);
            var id = UUID.randomUUID();
            DepreciacionServiceTest.autenticar(Set.of("bajas_activos.ver"), true);
            assertThatThrownBy(() -> servicio.crear(null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.editar(id, 0, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.confirmar(null, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> servicio.documento(id)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @CsvSource({"450000,150000,0", "250000,0,50000", "300000,0,0", "0,0,300000"})
    void calculaResultadoDeDisposicion(String obtenido, String ganancia, String perdida) {
        var resultado = BajaActivoService.resultado(new BigDecimal("300000"), new BigDecimal(obtenido));
        assertThat(resultado.ganancia()).isEqualByComparingTo(ganancia);
        assertThat(resultado.perdida()).isEqualByComparingTo(perdida);
    }
}
