package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.ValeCajaRepository;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.reports.pos.CashVoucherPrintDocument.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import tools.jackson.databind.json.JsonMapper;

/** Captures confirmed values without output; reprints never execute a cash command. */
@Service
public class CashVoucherDocumentService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final ValeCajaRepository vales;
    public CashVoucherDocumentService(ValeCajaRepository vales){this.vales=vales;}
    static void conservar(ValeCaja v,SesionCaja session,Empresa company){
        verificar(v);
        if(!Objects.equals(session.getTenantId(),v.getTenantId())||!Objects.equals(session.getEmpresaId(),v.getEmpresaId())
            ||!Objects.equals(session.getCajaId(),v.getCajaId())||!Objects.equals(session.getSucursalId(),v.getSucursalId())
            ||!Objects.equals(session.getId(),v.getSesionId())||!Objects.equals(company.getId(),v.getEmpresaId())||!Objects.equals(company.getTenantId(),v.getTenantId()))throw noEncontrado();
        var user=session.getUsuarioApertura();String cashier=user==null?null:((user.getNombre()==null?"":user.getNombre())+" "+(user.getApellido()==null?"":user.getApellido())).trim();
        // The existing confirmation command is explicitly an EXPENSE with CASH medium.
        // No direction or medium is inferred from the sign, concept, or a current balance.
        var d=build(v,company.getPaisCodigo()==null?"XX":company.getPaisCodigo(),v.getFechaHora().atZoneSameInstant(ZoneId.of(company.getZonaHoraria())).toLocalDateTime(),cashier);
        v.conservarDocumentoPos(JSON.writeValueAsString(d));
    }
    ValeCaja obtener(UUID id){var p=TenantContext.principalActual();var v=vales.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(CashVoucherDocumentService::noEncontrado);verificar(v);return v;}
    CashVoucherPrintDocument documento(ValeCaja v){
        verificar(v);CashVoucherPrintDocument doc;
        if(v.getDocumentoPos()==null){
            // Older vouchers already store immutable identity, amount, origin and timestamp.
            // Their recorded offset is retained; no current company/user/catalog is consulted.
            doc=build(v,"XX",v.getFechaHora().toLocalDateTime(),null);
        }else try{doc=JSON.readValue(v.getDocumentoPos(),CashVoucherPrintDocument.class);}
        catch(RuntimeException ex){throw new ReglaNegocioException("No fue posible leer los datos históricos del vale de caja.");}
        return v.getEstado()==EstadoValeCaja.VOIDED?doc.withCancellation(clean(v.getMotivoAnulacion())==null?"Vale anulado":clean(v.getMotivoAnulacion())):doc;
    }
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('vales_caja.ver') and hasAuthority('vales_caja.imprimir')")
    public BillingPrintDocumentRegistry.Document cargar(UUID id){var v=obtener(id);return new BillingPrintDocumentRegistry.Document(v.getSucursalId(),v.getCajaId(),()->generar(v),()->generar(v),()->documento(v));}
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('vales_caja.ver') and hasAuthority('vales_caja.imprimir')")
    public ArchivoPdfDto generar(UUID id){return generar(obtener(id));}
    ArchivoPdfDto generar(ValeCaja v){
        try{var d=documento(v);return new ArchivoPdfDto("Vale_"+InvoiceFormats.fileName(d.voucherNumber()),new CashVoucher80mmPdfRenderer().render(d));}
        catch(InvoicePrintFailure|IllegalArgumentException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=CashVoucherRenderFailed cashVoucherId={} documentType=CASH_VOUCHER traceId={}",v.getId(),com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());throw new ReglaNegocioException("El vale está guardado, pero no fue posible generar su ticket.",ex);}
    }
    private static CashVoucherPrintDocument build(ValeCaja v,String country,LocalDateTime date,String cashier){
        return new CashVoucherPrintDocument(new CompanySnapshot(clean(v.getEmpresaNombre()),clean(v.getEmpresaIdentificacion()),clean(v.getEmpresaDireccion()),clean(v.getEmpresaTelefono()),country),v.getNumero(),date,
            clean(v.getSucursal()),clean(v.getCaja()),clean(cashier),MovementType.CASH_OUT,clean(v.getConcepto()),v.getImporte(),v.getMoneda(),"Efectivo",clean(v.getReferencia()),clean(v.getObservacion()),clean(v.getUsuario()),
            v.getBeneficiario()==null||v.getBeneficiario().isBlank()?null:new RelatedParty(PartyLabel.BENEFICIARIO,clean(v.getBeneficiario())),true,null,List.of("Vale de caja","Conserve este comprobante"));
    }
    private static void verificar(ValeCaja v){var p=TenantContext.principalActual();if(!Objects.equals(v.getTenantId(),p.tenantId())||!Objects.equals(v.getEmpresaId(),EmpresaContext.requerirEmpresaId())||!EmpresaContext.permiteSucursal(v.getSucursalId()))throw noEncontrado();}
    private static String clean(String s){if(s==null)return null;var b=new StringBuilder();s.codePoints().forEach(c->b.appendCodePoint(Character.isISOControl(c)&&c!='\n'?' ':c));return b.toString();}
    private static RecursoNoEncontradoException noEncontrado(){return new RecursoNoEncontradoException("Vale de caja no encontrado.");}
}
