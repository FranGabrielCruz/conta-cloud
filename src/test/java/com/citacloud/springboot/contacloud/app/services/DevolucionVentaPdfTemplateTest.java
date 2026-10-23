package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.DevolucionVentaReporteLinea;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DevolucionVentaPdfTemplateTest {
    @Test void compilaYGeneraComprobanteSinMarcaDeLaAplicacion()throws Exception{
        InputStream source=getClass().getResourceAsStream("/reports/sales-return/sales-return.jrxml");assertNotNull(source);
        JasperReport report=JasperCompileManager.compileReport(source);Map<String,Object>p=new HashMap<>();
        p.put("companyName","Empresa Demo");p.put("companyTaxId","101-00000-1");p.put("companyPhone","809-555-0100");p.put("companyLogo",null);
        p.put("returnNumber","DEV-000001");p.put("creditNoteNumber","NCV-000001");p.put("invoiceNumber","FAC-000001");p.put("confirmedAt","22/10/2026 10:00");
        p.put("customerName","Cliente Demo");p.put("customerId","001-0000000-1");p.put("reason","Producto defectuoso");p.put("observation","Empaque deteriorado");p.put("inventory","Sí");p.put("warehouse","Almacén Principal");p.put("currency","DOP");
        p.put("subtotal",new BigDecimal("100"));p.put("discount",BigDecimal.ZERO);p.put("surcharge",BigDecimal.ZERO);p.put("tax",new BigDecimal("18"));p.put("total",new BigDecimal("118"));
        var rows=List.of(new DevolucionVentaReporteLinea(1,"PRD-000001","Producto","Unidad",BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("18"),new BigDecimal("118")));
        byte[] pdf=JasperExportManager.exportReportToPdf(JasperFillManager.fillReport(report,p,new JRBeanCollectionDataSource(rows)));
        assertTrue(pdf.length>1000);assertArrayEquals(new byte[]{'%','P','D','F','-'},Arrays.copyOf(pdf,5));
    }
}
