package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ReciboHistorialDto;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.repositories.NotaCreditoVentaConsultaRepository;
import com.citacloud.springboot.contacloud.app.repositories.NotaDebitoVentaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.views.components.AppGrid;
import com.citacloud.springboot.contacloud.app.views.components.AppStatusBadge;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoDocumentoVentaTest {
    @BeforeEach
    void autenticar() {
        var principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID(), "TEST", "Prueba",
            "prueba", "", true, Set.of("notas_credito.ver", "notas_debito.ver", "recibos.ver"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listadoDeCreditoRenderizaLosEstadosDelDocumento() {
        var vista = new NotasCreditoVentaView(null, null, null);
        for (var estado : List.of("CONFIRMED", "VOIDED", "DRAFT")) {
            var fila = new NotaCreditoVentaConsultaRepository.Resumen(UUID.randomUUID(), "NCV-1",
                LocalDate.now(), "Cliente", "FAC-1", null, "DOP", BigDecimal.TEN, estado);
            verificarNota(renderizar(vista, fila), estado);
        }
    }

    @Test
    void listadoDeDebitoRenderizaLosEstadosDelDocumento() {
        var vista = new NotasDebitoVentaView(null, null, null, null);
        for (var estado : List.of("CONFIRMED", "VOIDED", "DRAFT")) {
            var fila = new NotaDebitoVentaRepository.Resumen(UUID.randomUUID(), "NDV-1",
                LocalDate.now(), "FAC-1", "Cliente", "DOP", BigDecimal.TEN, estado);
            verificarNota(renderizar(vista, fila), estado);
        }
    }

    @Test
    void listadoDeRecibosMantieneEmitidoMientrasElReciboEsteVigente() {
        var vista = new RecibosVentaView(null, null, null, null);
        for (var estado : EstadoReciboCliente.values()) {
            var fila = new ReciboHistorialDto.Resumen(UUID.randomUUID(), "REC-1", LocalDateTime.now(),
                "Cliente", "DOP", BigDecimal.TEN, estado, List.of("FAC-1"));
            var badge = renderizar(vista, fila);
            if (estado == EstadoReciboCliente.VOIDED) {
                verificar(badge, "Anulado", "error");
            } else {
                verificar(badge, "Emitido", "success");
            }
        }
    }

    @Test
    void estadosAusentesSonNeutrosYActivoInactivoConservanSuApariencia() {
        verificar(EstadoDocumentoVenta.nota(null), "Desconocido", "contrast");
        verificar(EstadoDocumentoVenta.recibo(null), "Desconocido", "contrast");
        verificar(AppStatusBadge.activo(true), "Activo", "success");
        verificar(AppStatusBadge.activo(false), "Inactivo", "contrast");
    }

    private static void verificarNota(AppStatusBadge badge, String estado) {
        switch (estado) {
            case "CONFIRMED" -> verificar(badge, "Emitido", "success");
            case "VOIDED" -> verificar(badge, "Anulado", "error");
            default -> verificar(badge, "Desconocido", "contrast");
        }
    }

    private static void verificar(AppStatusBadge badge, String texto, String tema) {
        assertThat(badge.getText()).isEqualTo(texto);
        assertThat(badge.getElement().getThemeList()).containsExactlyInAnyOrder("badge", tema);
        assertThat(badge.getStyle().get("white-space")).isEqualTo("nowrap");
    }

    private static <T> AppStatusBadge renderizar(Component vista, T fila) {
        var grid = descendientes(vista).filter(AppGrid.class::isInstance)
            .map(c -> (Grid<?>) c).filter(c -> c.getColumnByKey("estado") != null)
            .findFirst().orElseThrow();
        var columna = grid.getColumnByKey("estado");
        assertThat(columna.isAutoWidth()).isTrue();
        assertThat(columna.getFlexGrow()).isZero();
        assertThat(grid.isAllRowsVisible()).isTrue();
        @SuppressWarnings("unchecked")
        var renderer = (ComponentRenderer<AppStatusBadge, T>) columna.getRenderer();
        return renderer.createComponent(fila);
    }

    private static Stream<Component> descendientes(Component componente) {
        return Stream.concat(Stream.of(componente), componente.getChildren().flatMap(EstadoDocumentoVentaTest::descendientes));
    }
}
