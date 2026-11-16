package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.*;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BalanzaPdfService {
    private static final Logger LOG=LoggerFactory.getLogger(BalanzaPdfService.class);
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Map<Boolean,JasperReport> plantillas=new HashMap<>();
    private final BalanzaService servicio;
    private final LocalFileStorageService storage;

    public BalanzaPdfService(BalanzaService servicio,LocalFileStorageService storage) {
        this.servicio=servicio;
        this.storage=storage;
    }

    public ArchivoPdfDto generar(Filtro filtro) {
        long inicio=System.nanoTime();
        var reporte=servicio.reporte(filtro);
        var r=reporte.resultado();
        var f=r.filtro();
        var e=reporte.emisor();
        try {
            byte[] logo=e.logo()==null?null:storage.load(e.logo(),5L*1024*1024).orElse(null);
            var p=new HashMap<String,Object>();
            p.put("companyTradeName",e.nombre());
            p.put("companyTaxIdentification","DO".equals(e.pais())?PurchaseOrderPdfService.formatTaxIdentification(e.rnc()):e.rnc());
            p.put("companyAddress",e.direccion());
            p.put("companyPhone","DO".equals(e.pais())?PurchaseOrderPdfService.formatPhone(e.telefono()):e.telefono());
            p.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            p.put("orderNumber","");
            p.put("orderDate",FECHA.format(f.fromDate())+" al "+FECHA.format(f.toDate()));
            p.put("chartFilters","Nivel: "+(f.level()==null?"Todos":f.level())+"   ·   Mostrar: "+f.display().etiqueta()
                +(f.search().isEmpty()?"":"\nBuscar: "+f.search()));
            p.put("emissionFooter","Generado: "+HORA.format(reporte.generado()));
            var virtualizador=new JRSwapFileVirtualizer(32,new JRSwapFile(System.getProperty("java.io.tmpdir"),4096,128),true);
            try {
                p.put(JRParameter.REPORT_VIRTUALIZER,virtualizador);
                var lleno=JasperFillManager.fillReport(plantilla(logo!=null),p,new JRMapCollectionDataSource(filas(r)));
                var bytes=JasperExportManager.exportReportToPdf(lleno);
                LOG.atInfo().addKeyValue("TraceId",TraceContext.currentOrCreate()).addKeyValue("Cuentas",r.total())
                    .addKeyValue("Paginas",lleno.getPages().size()).addKeyValue("Bytes",bytes.length)
                    .addKeyValue("DuracionMs",(System.nanoTime()-inicio)/1_000_000).log("balanza_pdf_generada");
                return new ArchivoPdfDto("Balanza_de_Comprobacion_"+f.fromDate()+"_"+f.toDate()+".pdf",bytes);
            } finally {
                virtualizador.cleanup();
            }
        } catch(IOException|JRException ex) {
            throw new IllegalStateException("No fue posible generar el PDF de la Balanza de Comprobación.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo) throws IOException,JRException {
        var reporte=plantillas.get(logo);
        if(reporte==null) {
            var xml=LargeDocumentPdfTemplate.balanza(logo);
            reporte=JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(logo,reporte);
        }
        return reporte;
    }

    private static List<Map<String,?>> filas(Resultado r) {
        var filas=new ArrayList<Map<String,?>>();
        for(var c:r.cuentas()) {
            String nombre="\u00a0".repeat(Math.min(c.nivel()-1,12)*3)+c.nombre()+(c.activa()?"":" · Inactiva");
            filas.add(fila(c.codigo(),nombre,c.tipo()==Tipo.AGRUPADORA,c.importes()));
        }
        if(r.cuentas().isEmpty()) filas.add(Map.of("rowKind","TEXT","rowLabel","No existen cuentas para los filtros seleccionados."));
        filas.add(Map.of("rowKind","SECTION","rowLabel","TOTALES DE LA CONSULTA"));
        for(var t:r.totales().filtrados()) filas.add(fila("","Totales "+t.moneda(),true,List.of(t)));
        filas.add(Map.of("rowKind","SECTION","rowLabel","COMPROBACIÓN GENERAL DEL PERÍODO"));
        for(var t:r.totales().generales()) {
            filas.add(fila("","General "+t.moneda(),true,List.of(t)));
            filas.add(Map.of("rowKind","TEXT","rowLabel","Diferencias · Saldo inicial: "+importe(t.diferenciaInicial())
                +"   ·   Movimientos: "+importe(t.diferenciaMovimientos())+"   ·   Saldo final: "+importe(t.diferenciaFinal())));
        }
        return filas;
    }

    private static Map<String,?> fila(String codigo,String nombre,boolean negrita,List<Importes> importes) {
        var fila=new HashMap<String,Object>();
        fila.put("rowKind","LINE");
        fila.put("rowBold",negrita);
        fila.put("dato0",codigo);
        fila.put("dato1",nombre);
        for(int i=0;i<6;i++) {
            int indice=i;
            String texto=importes.isEmpty()?"0.00":importes.stream().map(m->
                (importes.size()>1?m.moneda()+" ":"")+importe(m.columnas().get(indice))).collect(Collectors.joining("\n"));
            fila.put("dato"+(i+2),texto);
        }
        return fila;
    }

    private static String importe(BigDecimal valor) { return String.format(Locale.US,"%,.2f",valor); }
}
