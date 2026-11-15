package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogoCuentasPdfTest {
    @Test
    void repiteColumnasYNumeraLasPaginasSinFiltrosVacios() throws Exception {
        var servicio = mock(CatalogoCuentasService.class);
        var filtro = new Filtro(null, null, null, "");
        var raiz = new Cuenta(UUID.randomUUID(), null, "1", "ACTIVOS", Tipo.AGRUPADORA, Clasificacion.ACTIVO,
            Naturaleza.DEUDORA, true, null, 0, true, true);
        var filas = java.util.stream.Stream.concat(java.util.stream.Stream.of(new Linea(raiz, 1)),
            IntStream.rangeClosed(1, 95).mapToObj(i -> new Linea(new Cuenta(UUID.randomUUID(), raiz.id(), "1." + i,
                "Cuenta de prueba " + i, Tipo.MOVIMIENTO, Clasificacion.ACTIVO, Naturaleza.DEUDORA, true, null, 0, false, false), 2)))
            .toList();
        when(servicio.reporte(filtro)).thenReturn(new Reporte(
            new Empresa("Empresa de prueba", "101234567", "Dirección de prueba", "8095551234", "DO", null),
            ZonedDateTime.now(ZoneId.of("America/Santo_Domingo")), filtro, filas));
        var pdf = new CatalogoCuentasPdfService(servicio, mock(LocalFileStorageService.class)).generar(filtro);
        try (var documento = Loader.loadPDF(pdf.contenido())) {
            int paginas = documento.getNumberOfPages();
            assertThat(paginas).isGreaterThan(1);
            var lector = new PDFTextStripper();
            assertThat(lector.getText(documento)).contains("101-23456-7", "809-555-1234", "Cuenta de prueba 95")
                .doesNotContain("Filtros aplicados", "ContaCloud");
            for (int i = 1; i <= paginas; i++) {
                lector.setStartPage(i);
                lector.setEndPage(i);
                assertThat(lector.getText(documento)).contains("CÓDIGO", "NATURALEZA", "Página " + i + " de " + paginas);
            }
        }
    }
}
