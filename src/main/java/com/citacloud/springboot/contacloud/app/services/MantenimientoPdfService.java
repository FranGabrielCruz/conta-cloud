package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class MantenimientoPdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final MantenimientoService servicio;
    private final LocalFileStorageService storage;
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();

    public MantenimientoPdfService(MantenimientoService servicio,LocalFileStorageService storage) {
        this.servicio=servicio;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(UUID id) {
        var d=servicio.documento(id);
        var s=d.snapshot();
        var e=s.empresa();
        try {
            byte[] logo=e.logo()==null?null:storage.load(e.logo(),5L*1024*1024).orElse(null);
            Map<String,Object> p=new HashMap<>();
            p.put("companyTradeName",e.nombre());
            p.put("companyTaxIdentification","DO".equals(e.pais())?PurchaseOrderPdfService.formatTaxIdentification(e.rnc()):e.rnc());
            p.put("companyPhone","DO".equals(e.pais())?PurchaseOrderPdfService.formatPhone(e.telefono()):e.telefono());
            p.put("companyAddress",e.direccion());
            p.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            p.put("orderNumber",d.codigo());
            p.put("orderDate",fecha(d.realizada()));
            p.put("supplierName",s.activoCodigo()+" — "+s.activoNombre());
            p.put("emissionFooter","Generado: "+DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm xxx").format(ZonedDateTime.now()));
            List<Map<String,?>> filas=new ArrayList<>();
            seccion(filas,"INFORMACIÓN DEL ACTIVO");
            filas.add(fila("Código",s.activoCodigo(),"Activo",s.activoNombre(),"Estado del activo",estadoActivo(s.estadoPatrimonial())));
            filas.add(fila("Marca",s.marca(),"Modelo",s.modelo(),"Número de serie",s.serie()));
            seccion(filas,"INFORMACIÓN DEL MANTENIMIENTO");
            filas.add(fila("Tipo",s.tipo(),"Estado","COMPLETADO","Prioridad",d.datos().prioridad().etiqueta()));
            filas.add(fila("Fecha programada",fecha(d.datos().programada()),"Fecha realizada",fecha(d.realizada()),
                "Responsable",s.responsable()));
            filas.add(fila("Proveedor / Técnico",s.proveedor(),"Moneda",s.moneda(),"Próximo mantenimiento",fecha(d.datos().proxima())));
            agregarTexto(filas,"TRABAJO REALIZADO",d.trabajo());
            seccion(filas,"INFORMACIÓN ECONÓMICA");
            filas.add(fila("Costo estimado",importe(s.moneda(),d.datos().estimado()),"Costo real",importe(s.moneda(),d.real()),
                "Variación",importe(s.moneda(),d.variacion())));
            if(d.datos().observacion()!=null&&!d.datos().observacion().isBlank())
                agregarTexto(filas,"OBSERVACIÓN",d.datos().observacion());
            var lleno=JasperFillManager.fillReport(plantilla(logo!=null),p,new JRMapCollectionDataSource(filas));
            return new ArchivoPdfDto("Mantenimiento_"+d.codigo()+".pdf",JasperExportManager.exportReportToPdf(lleno));
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF de mantenimiento.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo)throws IOException,JRException {
        var plantilla=plantillas.get(logo);
        if(plantilla==null) {
            var xml=LargeDocumentPdfTemplate.mantenimiento(logo);
            plantilla=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(logo,plantilla);
        }
        return plantilla;
    }

    private static void agregarTexto(List<Map<String,?>> filas,String titulo,String texto) {
        seccion(filas,titulo);
        filas.add(Map.of("rowKind","TEXT","rowLabel",texto));
    }

    private static void seccion(List<Map<String,?>> filas,String titulo) {
        filas.add(Map.of("rowKind","SECTION","rowLabel",titulo));
    }

    private static Map<String,String> fila(String etiqueta1,String valor1,String etiqueta2,String valor2,String etiqueta3,String valor3) {
        return Map.of("rowKind","FIELDS","label0",etiqueta1,"value0",valor(valor1),"label1",etiqueta2,"value1",valor(valor2),
            "label2",etiqueta3,"value2",valor(valor3));
    }

    private static String estadoActivo(String estado) {
        return switch(Objects.toString(estado,"")) {
            case "ACTIVO" -> "Activo";
            case "INACTIVO" -> "Inactivo";
            case "BAJA" -> "Dado de baja";
            default -> "—";
        };
    }

    private static String importe(String moneda,java.math.BigDecimal monto) {
        return String.format(Locale.US,"%s %,.2f",moneda,monto);
    }

    private static String valor(String texto){ return texto==null||texto.isBlank()?"—":texto; }
    private static String fecha(LocalDate fecha){ return fecha==null?null:FECHA.format(fecha); }
}
