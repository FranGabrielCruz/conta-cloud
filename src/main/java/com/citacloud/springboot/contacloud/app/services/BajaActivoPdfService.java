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
import java.util.UUID;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class BajaActivoPdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final BajaActivoService servicio;
    private final LocalFileStorageService storage;
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();

    public BajaActivoPdfService(BajaActivoService servicio,LocalFileStorageService storage) {
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
            p.put("orderDate",fecha(d.datos().fecha()));
            p.put("supplierName",s.activo().etiqueta());
            p.put("emissionFooter","Generado: "+DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm xxx").format(ZonedDateTime.now()));
            List<Map<String,?>> filas=new ArrayList<>();
            var a = s.activo();
            var datos = d.datos();
            var resultado = BajaActivoService.resultado(a.libros(), datos.obtenido());
            seccion(filas, "INFORMACIÓN DEL ACTIVO");
            filas.add(fila("Código", a.codigo(), "Activo", a.nombre(), "Estado", "Dado de baja"));
            filas.add(fila("Categoría", a.categoria(), "Marca", a.marca(), "Modelo", a.modelo()));
            filas.add(fila("Número de serie", a.serie(), "Fecha de adquisición", fecha(a.adquisicion()), "Moneda", a.moneda()));
            seccion(filas, "INFORMACIÓN DE LA BAJA");
            filas.add(fila("Tipo", s.tipo(), "Fecha efectiva", fecha(datos.fecha()), "Estado", "Confirmada"));
            filas.add(fila("Última depreciación", fecha(a.ultima()), "Confirmada por", s.confirmadoPor(), "Referencia", datos.referencia()));
            if (s.factura() != null) agregarTexto(filas, "FACTURA DE VENTA", s.factura());
            seccion(filas, "INFORMACIÓN FINANCIERA");
            filas.add(fila("Costo original", importe(a.moneda(), a.costo()), "Depreciación acumulada", importe(a.moneda(), a.depreciacion()),
                "Valor en libros", importe(a.moneda(), a.libros())));
            filas.add(fila("Valor obtenido", importe(a.moneda(), datos.obtenido()), "Ganancia", importe(a.moneda(), resultado.ganancia()),
                "Pérdida", importe(a.moneda(), resultado.perdida())));
            if (a.ajustes().signum() != 0) agregarTexto(filas, "AJUSTES NETOS", importe(a.moneda(), a.ajustes()));
            agregarTexto(filas, "MOTIVO", datos.motivo());
            if (datos.observacion() != null) agregarTexto(filas, "OBSERVACIÓN", datos.observacion());
            var lleno=JasperFillManager.fillReport(plantilla(logo!=null),p,new JRMapCollectionDataSource(filas));
            return new ArchivoPdfDto("Baja_"+d.codigo()+".pdf",JasperExportManager.exportReportToPdf(lleno));
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF de baja.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo)throws IOException,JRException {
        var plantilla=plantillas.get(logo);
        if(plantilla==null) {
            var xml=LargeDocumentPdfTemplate.mantenimiento(logo)
                .replace("MANTENIMIENTO DE ACTIVO", "BAJA DE ACTIVO FIJO").replace("fixed_asset_maintenance", "fixed_asset_disposal");
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

    private static String importe(String moneda,java.math.BigDecimal monto) {
        return String.format(Locale.US,"%s %,.2f",moneda,monto);
    }

    private static String valor(String texto){ return texto==null||texto.isBlank()?"—":texto; }
    private static String fecha(LocalDate fecha){ return fecha==null?null:FECHA.format(fecha); }
}
