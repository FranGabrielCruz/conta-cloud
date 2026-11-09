package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.Filtro;
import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.Reporte;
import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class ActivoFijoPdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final ActivoFijoService activos;
    private final LocalFileStorageService storage;
    private final Map<Diseno,JasperReport> plantillas=new HashMap<>();

    public ActivoFijoPdfService(ActivoFijoService activos, LocalFileStorageService storage) {
        this.activos=activos;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(Filtro filtro) {
        var reporte=activos.reporte(filtro);
        try {
            byte[] logo=reporte.logo()==null?null:storage.load(reporte.logo(),5L*1024*1024).orElse(null);
            var parametros=parametros(reporte,logo);
            var diseno=new Diseno(logo!=null,!reporte.filtros().isEmpty());
            var documento=JasperFillManager.fillReport(plantilla(diseno),parametros,new JRMapCollectionDataSource(filas(reporte)));
            return new ArchivoPdfDto("Reporte_Activos_Fijos_"+reporte.fecha()+".pdf",JasperExportManager.exportReportToPdf(documento));
        } catch (IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el reporte de activos fijos.",ex);
        }
    }

    private synchronized JasperReport plantilla(Diseno diseno) throws IOException, JRException {
        var plantilla=plantillas.get(diseno);
        if (plantilla==null) {
            var xml=LargeDocumentPdfTemplate.activosFijos(diseno.logo(),diseno.filtros());
            plantilla=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(diseno,plantilla);
        }
        return plantilla;
    }

    private static Map<String,Object> parametros(Reporte r,byte[] logo) {
        var params=new HashMap<String,Object>();
        params.put("companyTradeName",r.empresa());
        params.put("companyTaxIdentification","DO".equals(r.pais())?PurchaseOrderPdfService.formatTaxIdentification(r.rnc()):r.rnc());
        params.put("companyAddress",r.direccion());
        params.put("companyPhone","DO".equals(r.pais())?PurchaseOrderPdfService.formatPhone(r.telefono()):r.telefono());
        params.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
        params.put("orderNumber","");
        params.put("orderDate",FECHA.format(r.fecha()));
        params.put("assetFilters",String.join("  •  ",r.filtros()));
        params.put("assetCount",String.format(Locale.US,"%,d",r.activos().size()));
        if (r.totales().size()==1) {
            var total=r.totales().getFirst();
            params.put("assetCost",total.moneda()+" "+importe(total.costo()));
            params.put("assetDepreciation",total.moneda()+" "+importe(total.depreciacion()));
            params.put("assetBook",total.moneda()+" "+importe(total.valorLibros()));
        }
        params.put("emissionFooter","Generado: "+FECHA.format(r.fecha()));
        return params;
    }

    private static List<Map<String,?>> filas(Reporte r) {
        List<Map<String,?>> filas=new ArrayList<>();
        for (var a:r.activos()) {
            filas.add(linea("LINE",a.codigo(),a.nombre(),a.categoria(),FECHA.format(a.fecha()),a.moneda(),
                importe(a.costo()),importe(a.depreciacion()),importe(a.valorLibros())));
        }
        if (filas.isEmpty()) {
            filas.add(Map.of("rowKind","TEXT","rowLabel","No se encontraron activos para los filtros seleccionados."));
        } else {
            if (r.totales().size()>1) filas.add(Map.of("rowKind","SECTION","rowLabel","TOTALES POR MONEDA"));
            for (var total:r.totales()) {
                filas.add(linea("ASSET_TOTAL","TOTAL "+total.moneda(),"","","","",
                    importe(total.costo()),importe(total.depreciacion()),importe(total.valorLibros())));
            }
        }
        return filas;
    }

    private static Map<String,Object> linea(String tipo,String... valores) {
        var fila=new HashMap<String,Object>();
        fila.put("rowKind",tipo);
        for (int i=0;i<valores.length;i++) fila.put("dato"+i,valores[i]);
        return fila;
    }

    private static String importe(BigDecimal valor) { return String.format(Locale.US,"%,.2f",valor); }

    private record Diseno(boolean logo,boolean filtros) {}
}
