package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
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

class ActivoFijoPdfTest {
    private static final LocalDate FECHA=LocalDate.of(2026,11,9);

    @Test
    void imprimeTodoElConjuntoConFiltrosYTotalesSeparados() throws Exception {
        var servicio=mock(ActivoFijoService.class);
        var pdf=new ActivoFijoPdfService(servicio,mock(LocalFileStorageService.class));
        var filtro=new Filtro("TERRENOS",null,null,Estado.ACTIVO,"AF-PRUEBA");
        var activos=IntStream.rangeClosed(1,37).mapToObj(i->new Resumen(UUID.randomUUID(),"AF-PRUEBA-"+i,
            "Terreno "+i,"Terrenos","Principal",null,Estado.ACTIVO,"Disponible",FECHA,i==37?"USD":"DOP",
            new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("100"))).toList();
        var totales=List.of(total("DOP",36,"3600"),total("USD",1,"100"));
        when(servicio.reporte(filtro)).thenReturn(new Reporte("Empresa de prueba","123456789","Calle Uno",
            "8095551234","DO",null,FECHA,List.of("Categoría: Terrenos","Estado: Activo","Búsqueda: AF-PRUEBA"),activos,totales));
        var archivo=pdf.generar(filtro);
        try (var doc=Loader.loadPDF(archivo.contenido())) {
            var contenido=new PDFTextStripper().getText(doc);
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            assertThat(contenido).contains("REPORTE DE ACTIVOS FIJOS","Empresa de prueba","123-45678-9","809-555-1234",
                "09/11/2026","AF-PRUEBA-37","3,600.00","TOTAL DOP","TOTAL USD","ACTIVOS INCLUIDOS","TOTALES POR MONEDA")
                .doesNotContain("ContaCloud","3,700.00","COSTO DE ADQUISICIÓN","FILTROS APLICADOS","Sucursal:");
            assertThat(contenido.replaceAll(" +"," ")).contains("Categoría: Terrenos • Estado: Activo • Búsqueda: AF-PRUEBA");
            assertThat(contenido.lines().filter(l->l.startsWith("AF-PRUEBA-")).count()).isEqualTo(37);
            for (int pagina=1;pagina<=doc.getNumberOfPages();pagina++) {
                var texto=new PDFTextStripper();
                texto.setStartPage(pagina);
                texto.setEndPage(pagina);
                assertThat(texto.getText(doc)).contains("CÓDIGO","ADQUISICIÓN","VALOR EN LIBROS");
                if (pagina<doc.getNumberOfPages())
                    assertThat(texto.getText(doc)).doesNotContain("TOTAL DOP","TOTAL USD");
            }
        }
        vistaPrevia("activos-filtrado",archivo.contenido());
        verify(servicio).reporte(filtro);
    }

    @Test
    void sinFiltrosResumeCostoDepreciacionYLibrosEnUnaMoneda() throws Exception {
        var servicio=mock(ActivoFijoService.class);
        var pdf=new ActivoFijoPdfService(servicio,mock(LocalFileStorageService.class));
        var costo=new BigDecimal("150000");
        var depreciacion=new BigDecimal("30000");
        var libros=new BigDecimal("120000");
        var activo=new Resumen(UUID.randomUUID(),"AF-000001","Carro","Vehículos","Principal",null,Estado.ACTIVO,
            "Disponible",FECHA,"DOP",costo,depreciacion,libros);
        when(servicio.reporte(null)).thenReturn(new Reporte("Empresa de prueba","123456789","Calle Uno","8095551234",
            "DO",null,FECHA,List.of(),List.of(activo),List.of(new Totales("DOP",1,1,0,costo,depreciacion,libros))));
        var archivo=pdf.generar(null);
        try(var doc=Loader.loadPDF(archivo.contenido())) {
            assertThat(new PDFTextStripper().getText(doc))
                .contains("RESUMEN","ACTIVOS INCLUIDOS","COSTO DE ADQUISICIÓN","DEPRECIACIÓN ACUM.","VALOR EN LIBROS",
                    "DOP 150,000.00","DOP 30,000.00","DOP 120,000.00","TOTAL DOP")
                .doesNotContain("FILTROS APLICADOS","Categoría:","Sucursal:","Ubicación:","Estado:","Búsqueda:",
                    "Buscar:","TOTALES POR MONEDA","ContaCloud");
        }
        vistaPrevia("activos-sin-filtros",archivo.contenido());
    }

    private static void vistaPrevia(String nombre,byte[] contenido) throws java.io.IOException {
        if(Boolean.getBoolean("activos.pdf.preview"))
            java.nio.file.Files.write(java.nio.file.Path.of("target",nombre+".pdf"),contenido);
    }

    private static Totales total(String moneda,long cantidad,String importe) {
        return new Totales(moneda,cantidad,cantidad,0,new BigDecimal(importe),BigDecimal.ZERO,new BigDecimal(importe));
    }
}
