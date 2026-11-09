package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.CarteraCobrarDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.services.CuentasCobrarService.Encabezado;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CuentasCobrarPdfService {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final CuentasCobrarService cuentas;
    private final LocalFileStorageService storage;
    private final Map<String,JasperReport> plantillas=new HashMap<>();

    public CuentasCobrarPdfService(CuentasCobrarService cuentas,LocalFileStorageService storage){
        this.cuentas=cuentas;
        this.storage=storage;
    }

    public ArchivoPdfDto cartera(Filtro filtro){
        var reporte=cuentas.cartera(filtro);
        List<Map<String,?>> filas=new ArrayList<>();
        for(var c:reporte.cuentas()){
            filas.add(linea(c.documento(),fecha(c.fecha()),fecha(c.vencimiento()),c.cliente().nombre(),
                c.moneda(),dinero(c.total()),dinero(c.saldo()),c.estado().etiqueta()));
        }
        if(filas.isEmpty())filas.add(texto("TEXT","No hay documentos para los filtros seleccionados."));
        else{
            filas.add(texto("SECTION","TOTALES POR MONEDA · "+reporte.cuentas().size()+" documentos"));
            for(var m:reporte.indicadores().monedas()){
                filas.add(texto("TEXT",m.codigo()+"   ·   Total documentos: "+dinero(m.totalDocumentos())
                    +"   ·   Cartera: "+dinero(m.saldo())+"   ·   Por vencer: "+dinero(m.corriente())
                    +"   ·   Vencido: "+dinero(m.vencido())));
            }
        }
        return generar(reporte.encabezado(),filas,false,"Documentos: "+reporte.cuentas().size()
            +" · Clientes con saldo: "+reporte.indicadores().clientes());
    }

    public ArchivoPdfDto antiguedad(Filtro filtro,LocalDate corte){
        var reporte=cuentas.antiguedad(filtro,corte);
        List<Map<String,?>> filas=new ArrayList<>();
        Map<String,BigDecimal[]> totales=new TreeMap<>();
        for(var r:reporte.edades()){
            BigDecimal[] montos={r.corriente(),r.hasta30(),r.hasta60(),r.hasta90(),r.mas90(),r.total()};
            filas.add(lineaEdad(r.cliente().nombre(),r.moneda(),montos));
            var total=totales.computeIfAbsent(r.moneda(),m->new BigDecimal[]{
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});
            for(int i=0;i<montos.length;i++)total[i]=total[i].add(montos[i]);
        }
        if(filas.isEmpty())filas.add(texto("TEXT","No hay saldos pendientes al corte seleccionado."));
        else{
            filas.add(texto("SECTION","TOTALES POR MONEDA Y RANGO"));
            totales.forEach((moneda,montos)->filas.add(lineaEdad("TOTAL "+moneda,moneda,montos)));
        }
        return generar(reporte.encabezado(),filas,true,"Agrupado por cliente y moneda");
    }

    private ArchivoPdfDto generar(Encabezado e,List<Map<String,?>> filas,boolean edad,String resumen){
        var tipo=edad?"Antiguedad_Cuentas_Cobrar":"Cartera_Cuentas_Cobrar";
        try{
            byte[] logo=e.logo()==null?null:storage.load(e.logo(),5L*1024*1024).orElse(null);
            Map<String,Object> p=new HashMap<>();
            p.put("companyTradeName",e.empresa());
            p.put("companyTaxIdentification","DO".equals(e.pais())?PurchaseOrderPdfService.formatTaxIdentification(e.rnc()):e.rnc());
            p.put("companyAddress",e.direccion());
            p.put("companyPhone","DO".equals(e.pais())?PurchaseOrderPdfService.formatPhone(e.telefono()):e.telefono());
            p.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            p.put("orderNumber","");
            p.put("orderDate",fecha(e.corte()));
            p.put("supplierName",e.cliente());
            p.put("clientInformation",e.cliente()+"\n"+resumen);
            var f=e.filtro();
            String filtros="Estado: "+f.estado().etiqueta()+"\nVencimiento: "+fecha(f.venceDesde())+" a "+fecha(f.venceHasta());
            if(!f.buscar().isEmpty())filtros+="\nBuscar: "+f.buscar();
            p.put("affectedInformation",filtros);
            p.put("emissionFooter","Corte: "+fecha(e.corte()));
            var jasper=plantilla(logo!=null,edad);
            var bytes=JasperExportManager.exportReportToPdf(
                JasperFillManager.fillReport(jasper,p,new JRMapCollectionDataSource(filas)));
            LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("Reporte",tipo)
                .addKeyValue("Corte",e.corte()).addKeyValue("TraceId",TraceContext.currentOrCreate()).log("cuentas_cobrar_pdf");
            return new ArchivoPdfDto(tipo+"_"+e.corte()+".pdf",bytes);
        }catch(IOException|JRException|IllegalArgumentException|UncheckedIOException ex){
            LoggerFactory.getLogger(getClass()).atError().setCause(ex).addKeyValue("Reporte",tipo)
                .addKeyValue("TraceId",TraceContext.currentOrCreate()).log("cuentas_cobrar_pdf_error");
            throw new ReglaNegocioException("No fue posible generar el reporte de cuentas por cobrar.",ex);
        }
    }

    private synchronized JasperReport plantilla(boolean logo,boolean edad)throws IOException,JRException{
        String clave=logo+":"+edad;
        var resultado=plantillas.get(clave);
        if(resultado==null){
            var plantilla=LargeDocumentPdfTemplate.cartera(logo,105,92,edad);
            resultado=JasperCompileManager.compileReport(new ByteArrayInputStream(plantilla.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(clave,resultado);
        }
        return resultado;
    }

    private static Map<String,Object> lineaEdad(String cliente,String moneda,BigDecimal[] montos){
        String[] valores=new String[8];
        valores[0]=cliente;
        valores[1]=moneda;
        for(int i=0;i<montos.length;i++)valores[i+2]=dinero(montos[i]);
        return linea(valores);
    }
    private static Map<String,Object> linea(String... valores){
        Map<String,Object> fila=new HashMap<>();
        fila.put("rowKind","LINE");
        for(int i=0;i<valores.length;i++)fila.put("dato"+i,valores[i]);
        return fila;
    }
    private static Map<String,Object> texto(String tipo,String texto){
        return Map.of("rowKind",tipo,"rowLabel",texto);
    }
    private static String fecha(LocalDate fecha){return fecha==null?"—":FECHA.format(fecha);}
    private static String dinero(BigDecimal importe){return String.format(Locale.US,"%,.2f",importe);}
}
