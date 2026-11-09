package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CarteraCobrarDto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CuentasCobrarPdfTest {
    private final CuentasCobrarService service=mock(CuentasCobrarService.class);
    private final CuentasCobrarPdfService pdf=new CuentasCobrarPdfService(service,mock(LocalFileStorageService.class));
    private final Cliente cliente=new Cliente(UUID.randomUUID(),"Cliente de prueba");
    private final LocalDate corte=LocalDate.of(2026,11,9);
    private final Filtro filtro=new Filtro(cliente.id(),Estado.VENCIDO,corte.minusDays(50),corte,"FAC-");
    private final CuentasCobrarService.Encabezado cabecera=new CuentasCobrarService.Encabezado(
        "Empresa de prueba","123456789","Calle Uno","8095551234","DO",null,corte,filtro,cliente.nombre());

    @Test void carteraImprimeTodoElConjuntoYLosTotalesPorMoneda()throws Exception{
        var cuentas=IntStream.rangeClosed(1,37).mapToObj(i->new Cuenta(UUID.randomUUID(),"FAC-PRUEBA-"+i,
            corte.minusDays(80),corte.minusDays(35),cliente,null,i==37?"USD":"DOP","Principal","Crédito",null,
            new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("20"),new BigDecimal("80"),Estado.VENCIDO)).toList();
        when(service.cartera(filtro)).thenReturn(new CuentasCobrarService.ReporteCartera(cabecera,cuentas,
            new Indicadores(List.of(moneda("DOP","2880","3600"),moneda("USD","80","100")),1)));
        var archivo=pdf.cartera(filtro);
        try(var doc=Loader.loadPDF(archivo.contenido())){
            String contenido=new PDFTextStripper().getText(doc);
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            assertThat(contenido).contains("CUENTAS POR COBRAR","Empresa de prueba","123-45678-9","809-555-1234",
                "Estado: Vencido","09/11/2026","FAC-PRUEBA-1","FAC-PRUEBA-37","2,880.00","USD","37 documentos")
                .doesNotContain("ContaCloud");
            assertThat(contenido.lines().filter(l->l.startsWith("FAC-PRUEBA-")).count()).isEqualTo(37);
        }
        java.nio.file.Files.write(java.nio.file.Path.of("target","cartera-cobrar-prueba.pdf"),archivo.contenido());
    }

    @Test void antiguedadMuestraLosCincoRangosYUnTotalParaCadaMoneda()throws Exception{
        when(service.antiguedad(filtro,corte)).thenReturn(new CuentasCobrarService.ReporteEdad(cabecera,List.of(
            edad("DOP","10000","5000","7000","3000","2000","27000"),edad("USD","12","0","0","0","0","12"))));
        var archivo=pdf.antiguedad(filtro,corte);
        try(var doc=Loader.loadPDF(archivo.contenido())){
            String contenido=new PDFTextStripper().getText(doc);
            assertThat(contenido).contains("ANTIGÜEDAD DE SALDOS","CORRIENTE","1–30 DÍAS","31–60 DÍAS",
                "61–90 DÍAS","+90 DÍAS","27,000.00","TOTAL DOP","TOTAL USD","12.00")
                .doesNotContain("27,012.00","ContaCloud");
        }
        java.nio.file.Files.write(java.nio.file.Path.of("target","antiguedad-cobrar-prueba.pdf"),archivo.contenido());
    }

    private static Moneda moneda(String codigo,String saldo,String total){
        return new Moneda(codigo,n(saldo),BigDecimal.ZERO,n(saldo),n(total));
    }
    private Edad edad(String moneda,String actual,String a,String b,String c,String d,String total){
        return new Edad(cliente,moneda,n(actual),n(a),n(b),n(c),n(d),n(total));
    }
    private static BigDecimal n(String valor){return new BigDecimal(valor);}
}
