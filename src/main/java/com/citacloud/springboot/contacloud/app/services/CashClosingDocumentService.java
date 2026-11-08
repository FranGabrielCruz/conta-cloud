package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.reports.pos.CashClosingPrintDocument.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import tools.jackson.databind.json.JsonMapper;

/** Captures confirmed session data; rendering and reprinting never execute a closing command. */
@Service
public class CashClosingDocumentService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final CierreCajaRepository cierres;private final DetalleCierreCajaRepository detalles;
    private final ResumenMedioPagoCierreCajaRepository medios;private final DatosEmpresaRepository datos;private final FinancialMovementRepository movimientos;
    public CashClosingDocumentService(CierreCajaRepository cierres,DetalleCierreCajaRepository detalles,ResumenMedioPagoCierreCajaRepository medios,DatosEmpresaRepository datos,FinancialMovementRepository movimientos){this.movimientos=movimientos;this.cierres=cierres;this.detalles=detalles;this.medios=medios;this.datos=datos;}
    public void conservar(CierreCaja c,SesionCaja s,Empresa e){
        verificar(c);
        if(!Objects.equals(s.getTenantId(),c.getTenantId())||!Objects.equals(s.getEmpresaId(),c.getEmpresaId())||!Objects.equals(s.getCajaId(),c.getCajaId())||!Objects.equals(s.getSucursalId(),c.getSucursalId())||!Objects.equals(s.getId(),c.getSesionId())||!Objects.equals(e.getId(),c.getEmpresaId())||!Objects.equals(e.getTenantId(),c.getTenantId()))throw noEncontrado();
        var data=datos.findByEmpresaId(c.getEmpresaId()).orElse(null);var zone=ZoneId.of(e.getZonaHoraria());
        var user=s.getUsuarioApertura();String cashier=user==null?c.getAbiertoPorNombre():((user.getNombre()==null?"":user.getNombre())+" "+(user.getApellido()==null?"":user.getApellido())).trim();
        var d=build(c,e.getPaisCodigo()==null?"XX":e.getPaisCodigo(),data==null?null:data.getDireccion(),data==null?null:data.getTelefono(),c.getAbiertoEn().atZoneSameInstant(zone).toLocalDateTime(),c.getCerradoEn().atZoneSameInstant(zone).toLocalDateTime(),cashier);
        var collections=new ArrayList<PaymentMethod>();int order=1;
        for(var payment:movimientos.formasPagoSesion(c.getTenantId(),c.getEmpresaId(),c.getSesionId())){
            String name=payment.getCodigo().startsWith("LEGACY_")?MedioPagoMovimiento.valueOf(payment.getNombre()).getEtiqueta():payment.getNombre();
            collections.add(new PaymentMethod(clean(name),payment.getImporte(),order++));
        }
        var total=collections.stream().map(PaymentMethod::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        d=d.withReportContext(c.getTurno(),cashSummary(c),collections,total);
        checkCount(c,d);c.conservarDocumentoPos(JSON.writeValueAsString(d));
    }
    CierreCaja obtener(UUID id){var p=TenantContext.principalActual();var c=cierres.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(CashClosingDocumentService::noEncontrado);verificar(c);return c;}
    CashClosingPrintDocument documento(CierreCaja c){
        verificar(c);CashClosingPrintDocument d;
        if(c.getDocumentoPos()==null){d=build(c,"XX",null,null,c.getAbiertoEn().toLocalDateTime(),c.getCerradoEn().toLocalDateTime(),c.getAbiertoPorNombre());}
        else try{d=JSON.readValue(c.getDocumentoPos(),CashClosingPrintDocument.class);}
        catch(RuntimeException ex){throw new ReglaNegocioException("No fue posible leer los datos históricos del cierre de caja.");}
        if(d.shiftNumber()==null||d.cashSummary()==null)d=d.withReportContext(d.shiftNumber()==null?c.getTurno():d.shiftNumber(),d.cashSummary()==null?cashSummary(c):d.cashSummary(),d.collectionMethods(),d.totalCollected());
        checkCount(c,d);return d;
    }
    @PreAuthorize("hasAuthority('cierres_caja.imprimir') and hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial')")
    public BillingPrintDocumentRegistry.Document cargar(UUID id){var c=obtener(id);return new BillingPrintDocumentRegistry.Document(c.getSucursalId(),c.getCajaId(),()->generar(c),()->generar(c),()->documento(c));}
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('cierres_caja.imprimir') and hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial')")
    public ArchivoPdfDto generar(UUID id){return generar(obtener(id));}
    ArchivoPdfDto generar(CierreCaja c){
        try{var d=documento(c);return new ArchivoPdfDto("Cierre_"+InvoiceFormats.fileName(d.closingNumber()),new CashClosing80mmPdfRenderer().render(d));}
        catch(InvoicePrintFailure|IllegalArgumentException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=CashClosingRenderFailed cashClosingId={} documentType=CASH_CLOSING traceId={}",c.getId(),com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());throw new ReglaNegocioException("El cierre está guardado, pero no fue posible generar su ticket.",ex);}
    }
    private CashClosingPrintDocument build(CierreCaja c,String country,String address,String phone,LocalDateTime opening,LocalDateTime closing,String cashier){
        var rows=detalles.findAllByCierreIdAndTenantIdAndEmpresaIdOrderByOrdenAsc(c.getId(),c.getTenantId(),c.getEmpresaId());
        var payments=medios.findAllByCierreIdAndTenantIdAndEmpresaIdOrderByOrdenAsc(c.getId(),c.getTenantId(),c.getEmpresaId());
        // Existing payment summaries include all INCOME movements, not just sales collections.
        var paymentTotal=payments.isEmpty()?null:new AmountLine("TOTAL ENTRADAS",payments.stream().map(ResumenMedioPagoCierreCaja::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
        return new CashClosingPrintDocument(new CompanySnapshot(clean(c.getEmpresaNombre()),clean(c.getEmpresaIdentificacion()),clean(address),clean(phone),country),c.getNumero(),clean(c.getCaja()),clean(c.getSucursal()),clean(cashier),opening,closing,c.getMonedaCodigo(),
            List.of(new AmountLine("Ventas en efectivo",c.getVentasEfectivo())),null,
            payments.stream().map(p->new PaymentMethod(clean(p.getMedio().getEtiqueta()),p.getAmount(),p.getOrden())).toList(),"Entradas registradas por medio de pago",paymentTotal,
            List.of(new AmountLine("Fondo inicial",c.getFondoInicial()),new AmountLine("Cobros en efectivo",c.getCobrosEfectivo()),new AmountLine("Otras entradas",c.getOtrasEntradasEfectivo()),new AmountLine("Vales de caja",c.getValesCaja()),new AmountLine("Devoluciones en efectivo",c.getDevolucionesEfectivo()),new AmountLine("Otras salidas",c.getOtrasSalidasEfectivo()),new AmountLine("Entradas de caja",c.getTotalEntradasEfectivo()),new AmountLine("Salidas de caja",c.getTotalSalidasEfectivo())),
            rows.stream().map(r->new Denomination(r.getDenomination(),r.getQuantity(),r.getSubtotal(),r.getOrden())).toList(),false,c.getContado(),c.getEsperado(),c.getDiferencia(),switch(c.getResult()){case BALANCED->Status.CUADRADO;case SHORTAGE->Status.FALTANTE;case OVERAGE->Status.SOBRANTE;},List.of(),clean(c.getObservation()),clean(c.getCerradoPorNombre()),true,List.of("Cierre de caja","Conserve este comprobante"));
    }
    private static CashSummary cashSummary(CierreCaja c){return new CashSummary(c.getFondoInicial(),c.getTotalEntradasEfectivo(),c.getTotalSalidasEfectivo());}
    private static void checkCount(CierreCaja c,CashClosingPrintDocument d){
        // Diagnostic only. Never replace the confirmed subtotal, total, difference or state.
        var sum=d.denominations().stream().map(Denomination::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(sum.compareTo(d.countedCash())!=0)org.slf4j.LoggerFactory.getLogger(CashClosingDocumentService.class).warn("event=CashClosingCountMismatch cashClosingId={} documentType=CASH_CLOSING traceId={}",c.getId(),com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());
    }
    private static void verificar(CierreCaja c){var p=TenantContext.principalActual();if(!Objects.equals(c.getTenantId(),p.tenantId())||!Objects.equals(c.getEmpresaId(),EmpresaContext.requerirEmpresaId())||!EmpresaContext.permiteSucursal(c.getSucursalId()))throw noEncontrado();}
    private static String clean(String s){if(s==null)return null;var b=new StringBuilder();s.codePoints().forEach(c->b.appendCodePoint(Character.isISOControl(c)&&c!='\n'?' ':c));return b.toString();}
    private static RecursoNoEncontradoException noEncontrado(){return new RecursoNoEncontradoException("Cierre de caja no encontrado.");}
}
