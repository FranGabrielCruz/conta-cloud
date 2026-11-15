package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Estado;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Total;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LibroDiarioPdfTest {
    private static final LocalDate FECHA=LocalDate.of(2026,11,15);
    private static final Filtro FILTRO=new Filtro(FECHA,FECHA,Tipo.AJUSTE,Origen.MANUAL,"Caja");

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void identificaContinuacionDeAsientosGrandesYConservaSubtotalesUnicos() throws Exception {
        var previo=asiento(1,14);
        var compacto=asiento(2,8);
        var largo=asiento(3,70);
        try(var pdf=Loader.loadPDF(generar(List.of(previo,compacto,largo)))) {
            var texto=new PDFTextStripper();
            String completo=texto.getText(pdf);
            assertThat(completo).contains("AS-000003 — CONTINUACIÓN","Total AS-000003","TOTALES DEL PERÍODO",
                "Tipo: Ajuste","Origen: Manual","Buscar: Caja").doesNotContain("ContaCloud");
            assertThat(completo.split("Total AS-000003",-1)).hasSize(2);
            assertThat(completo.split("TOTALES DEL PERÍODO",-1)).hasSize(2);
            int paginasConCompacto=0;
            for(int i=1;i<=pdf.getNumberOfPages();i++) {
                texto.setStartPage(i);
                texto.setEndPage(i);
                var pagina=texto.getText(pdf);
                assertThat(pagina).contains("CUENTA / ASIENTO","DÉBITO","CRÉDITO").containsPattern("Página "+i+" de\\s+"+pdf.getNumberOfPages());
                if(pagina.contains("Movimiento 2-")) paginasConCompacto++;
            }
            assertThat(paginasConCompacto).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints={10,100,1000})
    void exportaElUltimoAsientoSinTruncar(int cantidad) throws Exception {
        var registros=IntStream.rangeClosed(1,cantidad).mapToObj(i->asiento(i,2)).toList();
        try(var pdf=Loader.loadPDF(generar(registros))) {
            var texto=new PDFTextStripper();
            texto.setStartPage(pdf.getNumberOfPages());
            assertThat(texto.getText(pdf)).contains("Total AS-%06d".formatted(cantidad),"TOTALES DEL PERÍODO");
        }
    }

    @Test void periodoVacioProducePdfConTotalesEnCero() throws Exception {
        try(var pdf=Loader.loadPDF(generar(List.of()))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("15/11/2026","Buscar: Caja",
                "No existen movimientos contables para los filtros seleccionados.","Diferencia: 0.00");
        }
    }

    private static byte[] generar(List<Asiento> asientos) {
        DepreciacionServiceTest.autenticar(Set.of("libro_diario.ver","libro_diario.pdf"),true);
        var s=mock(LibroDiarioService.class);
        var total=asientos.stream().map(a->a.total().debito()).reduce(BigDecimal.ZERO,BigDecimal::add);
        var totales=asientos.isEmpty()?List.<TotalMoneda>of():List.of(new TotalMoneda("DOP",asientos.size(),new Total(total,total)));
        when(s.reporte(FILTRO)).thenReturn(new Reporte(FILTRO,asientos,totales,
            new Empresa("Empresa de prueba","124543678","Dirección","8095551234","DO",null),
            FECHA.atStartOfDay(ZoneId.of("America/Santo_Domingo"))));
        return new LibroDiarioPdfService(s,mock(LocalFileStorageService.class)).generar(FILTRO).contenido();
    }

    private static Asiento asiento(int orden,int cantidad) {
        var movimientos=IntStream.rangeClosed(1,cantidad).mapToObj(i->new Movimiento("1.1.01.01","Caja General",
            "Movimiento "+orden+"-"+i, i%2==0?BigDecimal.ONE:BigDecimal.ZERO, i%2==0?BigDecimal.ZERO:BigDecimal.ONE)).toList();
        var monto=BigDecimal.valueOf(cantidad/2);
        return new Asiento(UUID.randomUUID(),"AS-%06d".formatted(orden),FECHA,Tipo.AJUSTE,Origen.MANUAL,Estado.CONTABILIZADO,
            "Asiento de prueba "+orden,null,null,"DOP",movimientos,new Total(monto,monto));
    }
}
