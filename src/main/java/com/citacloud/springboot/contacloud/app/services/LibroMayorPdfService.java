package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
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
public class LibroMayorPdfService {
    private static final Logger LOG=LoggerFactory.getLogger(LibroMayorPdfService.class);
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private record Variante(boolean logo,boolean agrupadora) {}
    private final Map<Variante,JasperReport> plantillas=new HashMap<>();
    private final LibroMayorService servicio;
    private final LocalFileStorageService storage;

    public LibroMayorPdfService(LibroMayorService servicio,LocalFileStorageService storage) {
        this.servicio=servicio;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(Filtro filtro) {
        var reporte=servicio.reporte(filtro);
        var r=reporte.resultado();
        var e=reporte.emisor();
        long inicio=System.nanoTime();
        try {
            byte[] logo=e.logo()==null?null:storage.load(e.logo(),5L*1024*1024).orElse(null);
            var parametros=new HashMap<String,Object>();
            parametros.put("companyTradeName",e.nombre());
            parametros.put("companyTaxIdentification","DO".equals(e.pais())?PurchaseOrderPdfService.formatTaxIdentification(e.rnc()):e.rnc());
            parametros.put("companyAddress",e.direccion());
            parametros.put("companyPhone","DO".equals(e.pais())?PurchaseOrderPdfService.formatPhone(e.telefono()):e.telefono());
            parametros.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            parametros.put("orderNumber","");
            parametros.put("orderDate",FECHA.format(r.desde())+" al "+FECHA.format(r.hasta()));
            parametros.put("chartFilters",informacion(reporte));
            parametros.put("emissionFooter","Generado: "+HORA.format(reporte.generado()));

            var virtualizador=new JRSwapFileVirtualizer(32,new JRSwapFile(System.getProperty("java.io.tmpdir"),4096,128),true);
            try {
                parametros.put(JRParameter.REPORT_VIRTUALIZER,virtualizador);
                var lleno=JasperFillManager.fillReport(plantilla(new Variante(logo!=null,r.cuenta().agrupadora())),
                    parametros,fuente(filas(r)));
                var bytes=JasperExportManager.exportReportToPdf(lleno);
                LOG.atInfo().addKeyValue("TraceId",TraceContext.currentOrCreate()).addKeyValue("AccountId",r.cuenta().id())
                    .addKeyValue("Movimientos",r.total()).addKeyValue("Paginas",lleno.getPages().size())
                    .addKeyValue("Bytes",bytes.length).addKeyValue("DuracionMs",(System.nanoTime()-inicio)/1_000_000)
                    .log("libro_mayor_pdf_generado");
                return new ArchivoPdfDto("Libro_Mayor_"+r.cuenta().codigo()+"_"+r.desde()+"_"+r.hasta()+".pdf",bytes);
            } finally {
                virtualizador.cleanup();
            }
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF del Libro Mayor.",ex);
        }
    }

    private synchronized JasperReport plantilla(Variante v) throws IOException,JRException {
        var reporte=plantillas.get(v);
        if(reporte==null) {
            var xml=LargeDocumentPdfTemplate.libroMayor(v.logo(),v.agrupadora());
            reporte=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(v,reporte);
        }
        return reporte;
    }

    private static String informacion(Reporte reporte) {
        var r=reporte.resultado();
        var c=r.cuenta();
        var f=reporte.filtro();
        var info=new StringJoiner("\n").add("Cuenta: "+c.etiqueta())
            .add("Tipo: "+c.tipo().etiqueta()+"   ·   Naturaleza: "+c.naturaleza().etiqueta()
                +"   ·   Estado: "+(c.activo()?"Activa":"Inactiva"))
            .add("Período: "+FECHA.format(r.desde())+" al "+FECHA.format(r.hasta()));
        if(c.agrupadora()) info.add("Consulta consolidada de cuentas descendientes.");
        var filtros=new StringJoiner("   ·   ");
        if(f.type()!=null) filtros.add("Tipo de asiento: "+f.type().etiqueta());
        if(f.origin()!=null) filtros.add("Origen: "+f.origin().etiqueta());
        if(!f.search().isEmpty()) filtros.add("Buscar: "+f.search());
        if(filtros.length()>0) info.add(filtros.toString());
        var inicial=new StringJoiner("   ·   ","Saldo inicial: ","");
        r.resumen().forEach(t->inicial.add((t.moneda()+" "+saldo(t.inicial())).strip()));
        return info.add(inicial.toString()).toString();
    }

    private static Iterator<Map<String,String>> filas(Resultado r) {
        var movimientos=r.movimientos().stream().map(m->fila(m,r.cuenta().agrupadora()));
        var resumen=new ArrayList<Map<String,String>>();
        if(r.movimientos().isEmpty()) resumen.add(texto("No existen movimientos para los filtros seleccionados."));
        resumen.add(Map.of("rowKind","SECTION","rowLabel","RESUMEN DEL PERÍODO"));
        for(var t:r.resumen()) {
            resumen.add(texto((t.moneda()+"    Saldo inicial: "+saldo(t.inicial())+"    Débitos: "+importe(t.debitos())
                +"    Créditos: "+importe(t.creditos())+"    Variación: "+saldo(t.variacion())
                +"    Saldo final: "+saldo(t.cierre())).strip()));
        }
        resumen.add(texto("Débitos, créditos y variación corresponden a los filtros. Los saldos reflejan todo el período."));
        return Stream.concat(movimientos,resumen.stream()).iterator();
    }

    private static Map<String,String> texto(String valor) {
        return Map.of("rowKind","TEXT","rowLabel",valor);
    }

    private static Map<String,String> fila(Movimiento m,boolean agrupadora) {
        var detalle=new StringJoiner("\n").add(m.concepto());
        if(m.descripcion()!=null&&!m.descripcion().equals(m.concepto())) detalle.add(m.descripcion());
        var origen=new StringJoiner(" · ").add(m.tipo().etiqueta()).add(m.origen().etiqueta()).add(m.estado().etiqueta());
        if(m.referencia()!=null) origen.add(m.referencia());
        if(m.documento()!=null&&!Objects.equals(m.referencia(),m.documento())) origen.add(m.documento());
        detalle.add(origen.toString());
        var valores=new ArrayList<String>();
        valores.add(FECHA.format(m.fecha()));
        if(agrupadora) valores.add(m.codigo()+" — "+m.cuenta());
        valores.add(m.numero());
        valores.add(detalle.toString());
        valores.add(importe(m.debito()));
        valores.add(importe(m.credito()));
        valores.add(m.moneda()+"\n"+saldo(m.saldo()));
        var fila=new HashMap<String,String>();
        fila.put("rowKind","LINE");
        for(int i=0;i<valores.size();i++) fila.put("dato"+i,valores.get(i));
        return fila;
    }

    private static JRDataSource fuente(Iterator<Map<String,String>> filas) {
        return new JRDataSource() {
            private Map<String,String> actual;
            @Override public boolean next() {
                if(!filas.hasNext()) return false;
                actual=filas.next();
                return true;
            }
            @Override public Object getFieldValue(JRField campo) { return actual.get(campo.getName()); }
        };
    }

    private static String saldo(Saldo s) {
        return importe(s.importe())+(s.lado()==Lado.CERO?"":" "+s.lado());
    }

    private static String importe(BigDecimal monto) { return String.format(Locale.US,"%,.2f",monto); }
}
