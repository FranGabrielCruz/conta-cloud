package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import tools.jackson.databind.json.JsonMapper;

/** Historical data capture has no dependency on renderers, agents or business commands. */
@Service
public class CashCountDocumentService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final RecuentoCajaRepository recuentos;private final EmpresaRepository empresas;private final DatosEmpresaRepository datos;
    public CashCountDocumentService(RecuentoCajaRepository recuentos,EmpresaRepository empresas,DatosEmpresaRepository datos){this.recuentos=recuentos;this.empresas=empresas;this.datos=datos;}
    public void conservar(RecuentoCaja r,SesionCaja session,List<DetalleRecuentoCaja> details){
        verificar(r);var p=TenantContext.principalActual();
        if(!Objects.equals(session.getTenantId(),r.getTenantId())||!Objects.equals(session.getEmpresaId(),r.getEmpresaId())||!Objects.equals(session.getCajaId(),r.getCajaId())||!Objects.equals(session.getSucursalId(),r.getSucursalId()))throw noEncontrado();
        var e=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(CashCountDocumentService::noEncontrado);
        var data=datos.findByEmpresaId(p.empresaId()).orElse(null);var zone=ZoneId.of(e.getZonaHoraria());
        String company=data==null||data.getNombreComercial()==null||data.getNombreComercial().isBlank()?e.getNombre():data.getNombreComercial();
        var user=session.getUsuarioApertura();String cashier=user==null?null:((user.getNombre()==null?"":user.getNombre())+" "+(user.getApellido()==null?"":user.getApellido())).trim();
        var doc=new CashCountPrintDocument(new CashCountPrintDocument.CompanySnapshot(clean(company),clean(e.getIdentificacionFiscal()),data==null?null:clean(data.getDireccion()),data==null?null:clean(data.getTelefono()),e.getPaisCodigo()==null?"XX":e.getPaisCodigo()),
            r.getNumero(),r.getCorteEn().atZoneSameInstant(zone).toLocalDateTime(),session.getAbiertoEn()==null?null:session.getAbiertoEn().atZoneSameInstant(zone).toLocalDateTime(),r.getContadoEn().atZoneSameInstant(zone).toLocalDateTime(),
            clean(r.getSucursal().getNombre()),clean(r.getCaja().getNombre()),clean(cashier),clean(p.nombre()),r.getMoneda().getCodigoIso(),
            details.stream().map(d->new CashCountPrintDocument.Denomination(d.getDenomination(),d.getQuantity(),d.getSubtotal(),d.getOrden())).toList(),false,
            r.getEsperado(),r.getContado(),r.getDiferencia(),switch(r.getResult()){case BALANCED->CashCountPrintDocument.Status.CUADRADO;case SHORTAGE->CashCountPrintDocument.Status.FALTANTE;case OVERAGE->CashCountPrintDocument.Status.SOBRANTE;},
            clean(r.getObservation()),List.of("Documento de recuento","Conserve este comprobante"));
        r.conservarDocumentoPos(JSON.writeValueAsString(doc));
    }
    RecuentoCaja obtener(UUID id){var p=TenantContext.principalActual();var r=recuentos.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(CashCountDocumentService::noEncontrado);verificar(r);return r;}
    CashCountPrintDocument historicoSiExiste(UUID id){var r=obtener(id);return r.getDocumentoPos()==null?null:documento(r);}
    CashCountPrintDocument documento(RecuentoCaja r){
        verificar(r);if(r.getDocumentoPos()==null)throw new ReglaNegocioException("Este recuento anterior no conserva los datos históricos necesarios para el ticket POS. Puede consultar su PDF normal.");
        try{return JSON.readValue(r.getDocumentoPos(),CashCountPrintDocument.class);}
        catch(RuntimeException ex){throw new ReglaNegocioException("No fue posible leer el snapshot histórico del recuento.");}
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('recuentos_caja.imprimir')")
    public ArchivoPdfDto generar(UUID id){return generar(obtener(id));}
    ArchivoPdfDto generar(RecuentoCaja r){
        try{var doc=documento(r);return new ArchivoPdfDto("Recuento_"+InvoiceFormats.fileName(doc.cashCountNumber()),new CashCount80mmPdfRenderer().render(doc));}
        catch(InvoicePrintFailure|IllegalArgumentException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=CashCountRenderFailed cashCountId={} traceId={}",r.getId(),com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());throw new ReglaNegocioException("El recuento está guardado, pero no fue posible generar su ticket.",ex);}
    }
    private static void verificar(RecuentoCaja r){var p=TenantContext.principalActual();if(!Objects.equals(r.getTenantId(),p.tenantId())||!Objects.equals(r.getEmpresaId(),EmpresaContext.requerirEmpresaId())||!EmpresaContext.permiteSucursal(r.getSucursalId()))throw noEncontrado();}
    private static String clean(String s){return s==null?null:s.replaceAll("[\\p{Cntrl}&&[^\\n]]"," ");}
    private static RecursoNoEncontradoException noEncontrado(){return new RecursoNoEncontradoException("Recuento de caja no encontrado.");}
}
