package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.FacturaVentaReporteLinea;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FacturaVentaPdfTemplateTest {
    @Test void compilaYGeneraUnComprobanteMultipagina() throws Exception {
        InputStream source=getClass().getResourceAsStream("/reports/sales-invoice/sales-invoice.jrxml");
        assertNotNull(source);JasperReport report=JasperCompileManager.compileReport(source);
        Map<String,Object> p=new HashMap<>();p.put("companyName","Empresa de Demostración");p.put("companyLegalName","Empresa Demo, SRL");
        p.put("companyTaxId","101-00000-1");p.put("companyAddress","Avenida Principal 100, Santo Domingo");p.put("companyPhone","809-555-0100");p.put("companyEmail","ventas@demo.test");p.put("companyLogo",null);
        p.put("number","FAC-000001");p.put("date","17/10/2026");p.put("confirmedAt","17/10/2026 14:30");p.put("customerName","Consumidor Final");p.put("customerId","—");p.put("seller","Administrador Demo");p.put("branch","Sucursal Principal");p.put("currency","DOP");p.put("paymentCondition","Contado");p.put("notes","Gracias por su compra.");
        p.put("subtotal",new BigDecimal("36000.00"));p.put("tax",new BigDecimal("6480.00"));p.put("total",new BigDecimal("42480.00"));p.put("received",new BigDecimal("43000.00"));p.put("change",new BigDecimal("520.00"));
        List<FacturaVentaReporteLinea> lines=new ArrayList<>();for(int i=1;i<=45;i++)lines.add(new FacturaVentaReporteLinea(i,"PRD-"+String.format("%06d",i),"Producto de demostración "+i,"und",BigDecimal.ONE,new BigDecimal("800.00"),new BigDecimal("144.00"),new BigDecimal("944.00")));
        byte[] pdf=JasperExportManager.exportReportToPdf(JasperFillManager.fillReport(report,p,new JRBeanCollectionDataSource(lines)));
        assertTrue(pdf.length>1000);assertArrayEquals(new byte[]{'%','P','D','F','-'},Arrays.copyOf(pdf,5));
        Path out=Path.of("target","pdf-test","factura-venta.pdf");Files.createDirectories(out.getParent());Files.write(out,pdf);
    }
}
