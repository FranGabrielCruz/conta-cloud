package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class CotizacionPdfServiceTest {
    @Test void generaDocumentoMultipaginaConEncabezadoTotalesYPie() throws Exception {
        List<CotizacionReportLine> lines=IntStream.rangeClosed(1,42).mapToObj(i->new CotizacionReportLine(i,
            "Producto o servicio de prueba "+i+" con descripción comercial", "Unidad (UND)",new BigDecimal("2"),
            new BigDecimal("25000"),BigDecimal.ZERO,new BigDecimal("9000"),new BigDecimal("59000"))).toList();
        var data=new CotizacionReportData(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"COT-000025",
            "DEMO","Demo, SRL","131456789","Av. Principal 123, Santo Domingo","8095551234","ventas@demo.do",null,
            "Comercial Rodríguez","131456789","Calle Cliente 45","8095559876","cliente@example.com",
            LocalDate.of(2026,10,11),LocalDate.of(2026,10,26),"Sucursal Principal","DOP","REF-2026-25",
            "Entrega estimada de 3 a 5 días.",new BigDecimal("2100000"),BigDecimal.ZERO,new BigDecimal("378000"),
            new BigDecimal("2478000"),"ENVIADA","Administrador Demo",OffsetDateTime.of(2026,10,11,15,30,0,0,ZoneOffset.ofHours(-4)),lines);
        byte[] bytes=new CotizacionPdfService().generar(data);
        assertTrue(bytes.length>5000);assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'},Arrays.copyOf(bytes,4));
        Path output=Path.of("target","pdf-preview","cotizacion-multipagina.pdf");Files.createDirectories(output.getParent());Files.write(output,bytes);
    }
}
