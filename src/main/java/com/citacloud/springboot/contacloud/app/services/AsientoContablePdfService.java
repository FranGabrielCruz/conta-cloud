package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Detalle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class AsientoContablePdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm xxx");
    private final AsientoContableService servicio;
    private final LocalFileStorageService storage;
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();

    public AsientoContablePdfService(AsientoContableService servicio,LocalFileStorageService storage) {
        this.servicio=servicio;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(UUID id) {
        var reporte=servicio.reporte(id);
        var d=reporte.detalle();
        var empresa=reporte.emisor();
        try {
            byte[] logo=empresa.logo()==null?null:storage.load(empresa.logo(),5L*1024*1024).orElse(null);
            var p=new HashMap<String,Object>();
            p.put("companyTradeName",empresa.nombre());
            p.put("companyTaxIdentification","DO".equals(empresa.pais())
                ?PurchaseOrderPdfService.formatTaxIdentification(empresa.rnc()):empresa.rnc());
            p.put("companyAddress",empresa.direccion());
            p.put("companyPhone","DO".equals(empresa.pais())?PurchaseOrderPdfService.formatPhone(empresa.telefono()):empresa.telefono());
            p.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            p.put("orderNumber",d.fila().numero());
            p.put("orderDate",FECHA.format(d.fila().fecha()));
            p.put("chartFilters",String.join("  ·  ",d.fila().estado().etiqueta(),d.fila().tipo().etiqueta(),
                d.fila().origen().etiqueta(),d.fila().moneda()));
            p.put("emissionFooter","Generado: "+HORA.format(ZonedDateTime.now()));
            var lleno=JasperFillManager.fillReport(plantilla(logo!=null),p,new JRMapCollectionDataSource(filas(d)));
            return new ArchivoPdfDto(d.fila().numero()+".pdf",JasperExportManager.exportReportToPdf(lleno));
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF del asiento.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo)throws IOException,JRException {
        var reporte=plantillas.get(logo);
        if(reporte==null) {
            var xml=LargeDocumentPdfTemplate.asientoContable(logo);
            reporte=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(logo,reporte);
        }
        return reporte;
    }

    private static List<Map<String,?>> filas(Detalle d) {
        List<Map<String,?>> filas=new ArrayList<>();
        texto(filas,"Concepto",d.fila().concepto());
        texto(filas,"Referencia",d.referencia());
        texto(filas,"Documento origen",d.fuente()==null?null:d.fuente().numero());
        texto(filas,"Asiento original",d.numeroOriginal());
        texto(filas,"Asiento de reversión",d.numeroReversion());
        texto(filas,"Motivo",d.motivo());
        texto(filas,"Observación",d.observacion());
        d.lineas().forEach(l->filas.add(fila(l.cuenta().etiqueta(),Objects.toString(l.descripcion(),""),
            dinero(l.debito()),dinero(l.credito()))));
        var t=d.fila().total();
        filas.add(fila("TOTAL","",dinero(t.debito()),dinero(t.credito())));
        texto(filas,"Diferencia",dinero(t.diferencia()));
        return filas;
    }

    private static Map<String,String> fila(String cuenta,String descripcion,String debito,String credito) {
        return Map.of("rowKind","LINE","dato0",cuenta,"dato1",descripcion,"dato2",debito,"dato3",credito);
    }

    private static void texto(List<Map<String,?>> filas,String etiqueta,String valor) {
        if(valor!=null) filas.add(Map.of("rowKind","TEXT","rowLabel",etiqueta+": "+valor));
    }

    private static String dinero(BigDecimal monto) {return String.format(Locale.US,"%,.2f",monto);}
}
