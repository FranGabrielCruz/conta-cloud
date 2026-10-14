package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ReporteExistenciaInventarioData;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExistenciaInventarioPdfService {
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private volatile JasperReport report;
    public byte[] generar(ReporteExistenciaInventarioData data){try{
        JasperPrint print=JasperFillManager.fillReport(report(),parameters(data),new JRBeanCollectionDataSource(data.lineas(),false));
        return JasperExportManager.exportReportToPdf(print);
    }catch(JRException ex){throw new ReglaNegocioException("No fue posible generar el PDF. Intente nuevamente.",ex);}}
    static Map<String,Object> parameters(ReporteExistenciaInventarioData d){Map<String,Object> p=new HashMap<>();
        p.put("companyTradeName",value(d.empresaNombreComercial()));p.put("companyLegalName",optional(d.empresaRazonSocial()));
        p.put("companyTaxIdentification",PurchaseOrderPdfService.formatTaxIdentification(d.empresaIdentificacion()));
        p.put("companyAddress",optional(d.empresaDireccion()));p.put("companyPhone",PurchaseOrderPdfService.formatPhone(d.empresaTelefono()));
        p.put("companyEmail",optional(d.empresaCorreo()));p.put("companyLogo",d.empresaLogo()==null?null:new ByteArrayInputStream(d.empresaLogo()));
        p.put("asOf",d.al());p.put("search",d.busqueda());p.put("branch",d.sucursal());p.put("warehouse",d.almacen());
        p.put("category",d.categoria());p.put("status",d.estado());p.put("stock",d.stock());p.put("products",d.productosEncontrados());
        p.put("showBranch",d.mostrarSucursal());p.put("showWarehouse",d.mostrarAlmacen());p.put("generatedBy",d.generadoPor());
        p.put("generatedAt",d.generadoEn()==null?"":DATE_TIME.format(d.generadoEn()));p.put("hasRows",!d.lineas().isEmpty());return p;}
    private JasperReport report(){if(report!=null)return report;synchronized(this){if(report==null){try(InputStream in=getClass().getResourceAsStream("/reports/inventory-balance/inventory-balance.jrxml")){if(in==null)throw new ReglaNegocioException("No se encontró la plantilla de existencias.");report=JasperCompileManager.compileReport(in);}catch(IOException|JRException ex){throw new ReglaNegocioException("No fue posible preparar el reporte de existencias.",ex);}}return report;}}
    private static String value(String x){return x==null||x.isBlank()?"—":x.trim();}private static String optional(String x){return x==null||x.isBlank()?null:x.trim();}
}
