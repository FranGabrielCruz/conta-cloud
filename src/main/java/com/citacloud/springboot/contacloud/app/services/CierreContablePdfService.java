package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.Cierre;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class CierreContablePdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CierreContableService servicio;
    private final LocalFileStorageService storage;
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();

    public CierreContablePdfService(CierreContableService servicio,LocalFileStorageService storage) {
        this.servicio=servicio;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(UUID periodo,UUID cierre) {
        var c=servicio.reporte(periodo,cierre);
        var r=c.resumen();
        var e=r.emisor();
        try {
            byte[] logo=e.logo()==null?null:storage.load(e.logo(),5L*1024*1024).orElse(null);
            var p=new HashMap<String,Object>();
            p.put("companyTradeName",e.nombre());
            p.put("companyTaxIdentification","DO".equals(e.pais())?PurchaseOrderPdfService.formatTaxIdentification(e.rnc()):e.rnc());
            p.put("companyAddress",e.direccion());
            p.put("companyPhone","DO".equals(e.pais())?PurchaseOrderPdfService.formatPhone(e.telefono()):e.telefono());
            p.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            p.put("orderNumber","");
            p.put("orderDate",FECHA.format(r.desde())+" al "+FECHA.format(r.hasta()));
            p.put("chartFilters",r.periodo()+" · "+r.evaluacion().tipo().etiqueta());
            p.put("emissionFooter","Cierre: "+HORA.format(c.fecha().atZoneSameInstant(ZoneId.of(r.zonaHoraria()))));
            var lleno=JasperFillManager.fillReport(plantilla(logo!=null),p,new JRMapCollectionDataSource(filas(c)));
            return new ArchivoPdfDto("Cierre_Contable_"+r.hasta()+".pdf",JasperExportManager.exportReportToPdf(lleno));
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF del cierre contable.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo) throws IOException,JRException {
        var reporte=plantillas.get(logo);
        if(reporte==null) {
            var xml=LargeDocumentPdfTemplate.cierreContable(logo);
            reporte=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(logo,reporte);
        }
        return reporte;
    }

    private static List<Map<String,?>> filas(Cierre c) {
        var filas=new ArrayList<Map<String,?>>();
        var r=c.resumen();
        var e=r.evaluacion();
        filas.add(fila("SECTION","INFORMACIÓN DEL CIERRE"));
        filas.add(fila("TEXT","Cerrado por: "+r.usuario()+"  ·  Estado: "+(c.reapertura()==null?"Cerrado":"Reabierto")));
        if(e.inicioEjercicio()!=null) filas.add(fila("TEXT","Ejercicio desde: "+FECHA.format(e.inicioEjercicio())));
        if(c.asientoNumero()!=null) filas.add(fila("TEXT","Asiento de cierre: "+c.asientoNumero()));
        if(e.cuentaResultado()!=null) filas.add(fila("TEXT","Cuenta de resultado: "+e.cuentaResultado().etiqueta()));
        if(!r.observacion().isEmpty()) filas.add(fila("TEXT","Observación: "+r.observacion()));
        if(!e.validaciones().isEmpty()) {
            filas.add(fila("SECTION","VALIDACIONES PREVIAS"));
            e.validaciones().forEach(v->filas.add(fila("TEXT",v.nombre()+" · "+v.detalle())));
        }
        var t=e.resultado();
        filas.add(fila("SECTION","RESULTADO CONTABLE · "+e.moneda()));
        filas.add(fila("TEXT","Ingresos: "+importe(t.ingresos())+"    Costos: "+importe(t.costos())+"    Gastos: "+importe(t.gastos())));
        filas.add(fila("TEXT","Resultado: "+importe(t.neto())+" · "+t.tipo()));
        if(c.reapertura()!=null) {
            var a=c.reapertura();
            filas.add(fila("SECTION","REAPERTURA"));
            filas.add(fila("TEXT",a.usuario()+" · "+HORA.format(a.fecha().atZoneSameInstant(ZoneId.of(r.zonaHoraria())))));
            filas.add(fila("TEXT","Motivo: "+a.motivo()));
            if(a.reversaNumero()!=null) filas.add(fila("TEXT","Asiento de reversión: "+a.reversaNumero()));
        }
        return filas;
    }

    private static Map<String,?> fila(String clase,String texto) { return Map.of("rowKind",clase,"rowLabel",texto); }
    private static String importe(BigDecimal monto) { return String.format(Locale.US,"%,.2f",monto); }
}
