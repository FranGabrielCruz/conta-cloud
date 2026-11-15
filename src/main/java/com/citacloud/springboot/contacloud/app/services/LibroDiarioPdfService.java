package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LibroDiarioPdfService {
    private static final Logger LOG=LoggerFactory.getLogger(LibroDiarioPdfService.class);
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final LibroDiarioService servicio;
    private final LocalFileStorageService storage;
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();

    public LibroDiarioPdfService(LibroDiarioService servicio,LocalFileStorageService storage) {
        this.servicio=servicio;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(Filtro filtro) {
        long inicio=System.nanoTime();
        var r=servicio.reporte(filtro);
        long consultaMs=(System.nanoTime()-inicio)/1_000_000;
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
            p.put("orderDate",FECHA.format(r.filtro().desde())+" — "+FECHA.format(r.filtro().hasta()));
            p.put("chartFilters",filtros(r.filtro()));
            p.put("emissionFooter","Generado: "+HORA.format(r.generado()));
            var virtualizador=new JRSwapFileVirtualizer(32,new JRSwapFile(System.getProperty("java.io.tmpdir"),4096,128),true);
            try {
                p.put(JRParameter.REPORT_VIRTUALIZER,virtualizador);
                var lleno=JasperFillManager.fillReport(plantilla(logo!=null),p,fuente(filas(r).iterator()));
                var bytes=JasperExportManager.exportReportToPdf(lleno);
                registrar(r,consultaMs,(System.nanoTime()-inicio)/1_000_000,bytes.length);
                return new ArchivoPdfDto("Libro_Diario_"+r.filtro().desde()+"_"+r.filtro().hasta()+".pdf",bytes);
            } finally {virtualizador.cleanup();}
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF del Libro Diario.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo)throws IOException,JRException {
        var reporte=plantillas.get(logo);
        if(reporte==null) {
            var xml=LargeDocumentPdfTemplate.libroDiario(logo);
            reporte=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(logo,reporte);
        }
        return reporte;
    }

    private static String filtros(Filtro f) {
        var filtros=new ArrayList<String>();
        if(f.tipo()!=null) filtros.add("Tipo: "+f.tipo().etiqueta());
        if(f.origen()!=null) filtros.add("Origen: "+f.origen().etiqueta());
        if(!f.buscar().isEmpty()) filtros.add("Buscar: "+f.buscar());
        return String.join("  ·  ",filtros);
    }

    private static Stream<Map<String,String>> filas(Reporte r) {
        var asientos=r.asientos().stream().flatMap(a->{
            var datos=new StringJoiner("  ·  ").add(a.tipo().etiqueta()).add(a.origen().etiqueta()).add(a.estado().etiqueta());
            if(a.referencia()!=null) datos.add("Referencia: "+a.referencia());
            if(a.documento()!=null) datos.add("Documento origen: "+a.documento());
            var cabecera=Map.of("journalNumber",a.numero(),"journalHeading",
                FECHA.format(a.fecha())+"   "+a.numero()+"   "+a.moneda(),
                "journalConcept",a.concepto(),"journalMetadata",datos.toString());
            var movimientos=a.movimientos().stream().map(l->fila(l.codigo()+" — "+l.cuenta(),
                Objects.toString(l.descripcion(),""),l.debito(),l.credito()));
            return Stream.concat(movimientos,Stream.of(fila("Total "+a.numero(),"",a.total().debito(),a.total().credito())))
                .<Map<String,String>>map(fila->{
                    var renglon=new HashMap<>(fila);
                    renglon.putAll(cabecera);
                    return renglon;
                });
        });
        var totales=new ArrayList<Map<String,String>>();
        totales.add(Map.of("rowKind","SECTION","rowLabel","TOTALES DEL PERÍODO"));
        if(r.totales().isEmpty()) {
            totales.add(Map.of("rowKind","TEXT","rowLabel","No existen movimientos contables para los filtros seleccionados."));
            totales.add(fila("Total","Diferencia: 0.00",BigDecimal.ZERO,BigDecimal.ZERO));
        } else {
            for(var t:r.totales()) totales.add(fila(t.moneda(),"Diferencia: "+importe(t.total().diferencia()),
                t.total().debito(),t.total().credito()));
        }
        return Stream.concat(asientos,totales.stream());
    }

    private static JRDataSource fuente(Iterator<Map<String,String>> filas) {
        return new JRDataSource() {
            private Map<String,String> actual;
            @Override public boolean next() {
                if(!filas.hasNext()) return false;
                actual=filas.next();
                return true;
            }
            @Override public Object getFieldValue(JRField campo) {return actual.get(campo.getName());}
        };
    }

    private static void registrar(Reporte r,long consultaMs,long duracionMs,int bytes) {
        var p=TenantContext.principalActual();
        var f=r.filtro();
        var trace=TraceContext.currentOrCreate();
        LOG.atInfo().addKeyValue("TraceId",trace).addKeyValue("UserId",p.usuarioId())
            .addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("FromDate",f.desde()).addKeyValue("ToDate",f.hasta())
            .addKeyValue("Type",f.tipo()).addKeyValue("Origin",f.origen())
            .addKeyValue("Asientos",r.asientos().size()).addKeyValue("Lineas",r.asientos().stream().mapToLong(a->a.movimientos().size()).sum())
            .addKeyValue("ConsultaMs",consultaMs).addKeyValue("DuracionMs",duracionMs).addKeyValue("Bytes",bytes)
            .log("libro_diario_pdf_generado");
        r.totales().stream().filter(t->t.total().diferencia().signum()!=0).forEach(t->
            LOG.atWarn().addKeyValue("TraceId",trace).addKeyValue("CompanyId",p.empresaId())
                .addKeyValue("Moneda",t.moneda()).log("libro_diario_diferencia_contable"));
    }

    private static Map<String,String> fila(String cuenta,String descripcion,BigDecimal debe,BigDecimal haber) {
        return Map.of("rowKind","LINE","dato0",cuenta,"dato1",descripcion,"dato2",importe(debe),"dato3",importe(haber));
    }

    private static String importe(BigDecimal monto) {return String.format(Locale.US,"%,.2f",monto);}
}
