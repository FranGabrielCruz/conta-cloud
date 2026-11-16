package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.*;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Naturaleza;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BalanzaPdfPaginationTest {
    private static final LocalDate DESDE=LocalDate.of(2026,11,1);
    private static final Filtro FILTRO=new Filtro(DESDE,DESDE.plusDays(29),null,Mostrar.TODAS,"");
    private static final Importes CERO=Importes.cuenta("DOP",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);

    @Test void mantieneUnaSolaPaginaCuandoCabenLasCuentasYElResumen() throws Exception {
        var paginas=paginas(13);
        assertThat(paginas).singleElement().satisfies(p->assertThat(p)
            .contains("CUENTA","Cuenta prueba 013","TOTALES DE LA CONSULTA","Diferencias","Página 1 de 1"));
    }

    @Test void ultimaFilaQueCabeDejaSoloElResumenEnLaPaginaSiguiente() throws Exception {
        var paginas=paginas(17);
        assertThat(paginas).hasSize(2);
        assertThat(paginas.get(0)).contains("Cuenta prueba 017","MOVIMIENTOS").doesNotContain("TOTALES DE LA CONSULTA");
        assertThat(paginas.get(1)).contains("TOTALES DE LA CONSULTA","COMPROBACIÓN GENERAL","Diferencias","Página 2 de 2")
            .doesNotContain("Cuenta prueba","CÓDIGO");
    }

    @Test void repiteElEncabezadoSoloCuandoHayCuentasPendientes() throws Exception {
        var paginas=paginas(18);
        assertThat(paginas).hasSize(2);
        assertThat(paginas.get(0)).contains("CÓDIGO","Cuenta prueba 001").doesNotContain("Cuenta prueba 018");
        assertThat(paginas.get(1)).contains("CÓDIGO","Cuenta prueba 018","TOTALES DE LA CONSULTA","Diferencias");
    }

    @Test void ceroResultadosGeneraUnaPaginaInformativaSinEncabezadosDeTabla() throws Exception {
        assertThat(paginas(0)).singleElement().satisfies(p->assertThat(p)
            .contains("No existen cuentas para los filtros seleccionados.","TOTALES DE LA CONSULTA","Diferencias")
            .doesNotContain("CÓDIGO","Cuenta prueba"));
    }

    private List<String> paginas(int cantidad) throws Exception {
        var servicio=mock(BalanzaService.class);
        var cuentas=IntStream.rangeClosed(1,cantidad).mapToObj(i->new Cuenta(UUID.randomUUID(),null,null,
            "1.100."+i,"Cuenta prueba %03d".formatted(i),Tipo.MOVIMIENTO,Naturaleza.DEUDORA,true,1,List.of(CERO))).toList();
        var resultado=new Resultado(FILTRO,cuentas,new Totales(List.of(CERO),List.of(CERO)),cantidad,0,0);
        when(servicio.reporte(FILTRO)).thenReturn(new Reporte(resultado,new Empresa("Empresa de prueba",null,null,null,null,null),
            DESDE.atStartOfDay(ZoneOffset.UTC)));
        var pdf=new BalanzaPdfService(servicio,mock(LocalFileStorageService.class)).generar(FILTRO);
        try(var documento=Loader.loadPDF(pdf.contenido())) {
            var paginas=new ArrayList<String>();
            var extractor=new PDFTextStripper();
            for(int i=1;i<=documento.getNumberOfPages();i++) {
                extractor.setStartPage(i);
                extractor.setEndPage(i);
                paginas.add(extractor.getText(documento));
            }
            return paginas;
        }
    }
}
