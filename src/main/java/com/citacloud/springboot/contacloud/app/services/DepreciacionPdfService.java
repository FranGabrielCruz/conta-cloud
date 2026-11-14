package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DepreciacionPdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter PERIODO=DateTimeFormatter.ofPattern("MM/yyyy");
    private final DepreciacionService depreciaciones;
    private final LocalFileStorageService storage;
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();

    public DepreciacionPdfService(DepreciacionService depreciaciones,LocalFileStorageService storage) {
        this.depreciaciones=depreciaciones;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(UUID id) {
        var detalle=depreciaciones.documento(id);
        var proceso=detalle.proceso();
        var empresa=proceso.empresa();
        try {
            byte[] logo=empresa.logo()==null?null:storage.load(empresa.logo(),5L*1024*1024).orElse(null);
            var params=new HashMap<String,Object>();
            params.put("companyTradeName",empresa.nombre());
            boolean dominicana="DO".equals(empresa.pais());
            params.put("companyTaxIdentification",dominicana?PurchaseOrderPdfService.formatTaxIdentification(empresa.rnc()):empresa.rnc());
            params.put("companyPhone",dominicana?PurchaseOrderPdfService.formatPhone(empresa.telefono()):empresa.telefono());
            params.put("companyAddress",empresa.direccion());
            params.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            params.put("orderNumber",proceso.codigo());
            params.put("orderDate",FECHA.format(proceso.fecha()));
            String periodo="Período: "+PERIODO.format(proceso.periodo());
            params.put("currencyCode",periodo);
            String monedas=String.join(", ",detalle.totales().stream().map(t->t.moneda()).toList());
            params.put("clientInformation",periodo+" · Fecha: "+FECHA.format(proceso.fecha())+" · Procesado\n"
                +"Procesado por: "+proceso.nombreUsuario()+"\nActivos incluidos: "+detalle.lineas().size()+" · Moneda: "+monedas);
            params.put("affectedInformation",resumen(detalle.totales()));
            params.put("emissionFooter","Procesado: "+DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm xxx").format(proceso.procesadoEn()));
            var filas=new ArrayList<Map<String,?>>();
            for (var l:detalle.lineas()) filas.add(fila(l.codigo(),l.nombre(),l.moneda(),monto(l.costo()),monto(l.anterior()),
                monto(l.importe()),monto(l.acumulada()),monto(l.libros())));
            for (var t:detalle.totales()) filas.add(fila("","TOTAL "+t.moneda(),"",monto(t.costo()),monto(t.anterior()),
                monto(t.importe()),monto(t.acumulada()),monto(t.libros())));
            var report=JasperFillManager.fillReport(plantilla(logo!=null),params,new JRMapCollectionDataSource(filas));
            var archivo=new ArchivoPdfDto("Depreciacion_"+proceso.codigo()+".pdf",JasperExportManager.exportReportToPdf(report));
            LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("ProcesoId",id)
                .addKeyValue("TraceId",TraceContext.currentOrCreate()).log("depreciacion_pdf_generado");
            return archivo;
        } catch (IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF de depreciación.",ex);
        }
    }

    private static String resumen(java.util.List<com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Total> totales) {
        if (totales.size()!=1) return "Importes desglosados por moneda al final del detalle.";
        var t=totales.getFirst();
        return "Depreciación del período: "+t.moneda()+" "+monto(t.importe())
            +"\nCosto de activos: "+monto(t.costo())+" · Dep. anterior: "+monto(t.anterior())
            +"\nDep. acumulada: "+monto(t.acumulada())+" · Valor en libros: "+monto(t.libros());
    }

    private synchronized JasperReport plantilla(boolean logo)throws IOException,JRException {
        var plantilla=plantillas.get(logo);
        if (plantilla==null) {
            var xml=LargeDocumentPdfTemplate.depreciacion(logo);
            plantilla=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(logo,plantilla);
        }
        return plantilla;
    }

    private static Map<String,Object> fila(String... valores) {
        var fila=new HashMap<String,Object>();
        fila.put("rowKind","LINE");
        for (int i=0;i<valores.length;i++) fila.put("dato"+i,valores[i]);
        return fila;
    }

    private static String monto(BigDecimal valor) { return String.format(Locale.US,"%,.2f",valor); }
}
