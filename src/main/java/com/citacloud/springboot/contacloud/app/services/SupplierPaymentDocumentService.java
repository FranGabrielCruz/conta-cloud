package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SupplierPaymentDocumentService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final SupplierPaymentRepository payments;private final SupplierPaymentApplicationRepository applications;
    private final PurchaseInvoiceRepository invoices;private final EmpresaRepository companies;
    private final DatosEmpresaRepository companyData;private final UsuarioRepository users;
    private final LocalFileStorageService storage;private final SupplierPaymentPdfService pdf;

    public SupplierPaymentDocumentService(SupplierPaymentRepository payments,SupplierPaymentApplicationRepository applications,
            PurchaseInvoiceRepository invoices,EmpresaRepository companies,DatosEmpresaRepository companyData,
            UsuarioRepository users,LocalFileStorageService storage,SupplierPaymentPdfService pdf){
        this.payments=payments;this.applications=applications;this.invoices=invoices;this.companies=companies;
        this.companyData=companyData;this.users=users;this.storage=storage;this.pdf=pdf;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('pagos_proveedores.ver') and hasAuthority('pagos_proveedores.imprimir')")
    public byte[] pdf(UUID id){
        var principal=TenantContext.principalActual();
        PagoProveedor payment=payments.findByIdAndTenantIdAndEmpresaId(id,principal.tenantId(),principal.empresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Pago no encontrado."));
        if(payment.getEstado()==EstadoPagoProveedor.DRAFT)throw new ReglaNegocioException("El comprobante solo está disponible para pagos confirmados.");
        Empresa company=companies.findByIdAndTenantId(payment.getEmpresaId(),payment.getTenantId())
            .orElseThrow(()->new ReglaNegocioException("No fue posible identificar la empresa del pago."));
        DatosEmpresa data=companyData.findByEmpresaId(payment.getEmpresaId()).orElse(null);
        Usuario user=users.findByIdAndTenantId(principal.usuarioId(),principal.tenantId())
            .orElseThrow(()->new ReglaNegocioException("No fue posible identificar al usuario que genera el comprobante."));
        byte[] logo=company.getLogoObjectKey()==null?null:storage.load(company.getLogoObjectKey(),5L*1024*1024).orElse(null);
        String prefix=SupplierCreditNotePdfService.currencyPrefix(payment.getMoneda()==null?null:payment.getMoneda().getCodigoIso());
        List<SupplierPaymentReportLine> lines=new ArrayList<>(applications
            .findAllByPagoIdAndTenantIdAndEmpresaIdAndReversedFalseOrderByAplicadaEn(payment.getId(),payment.getTenantId(),payment.getEmpresaId())
            .stream().map(a->{FacturaProveedor invoice=invoices.findByIdAndTenantIdAndEmpresaId(a.getFacturaId(),payment.getTenantId(),payment.getEmpresaId()).orElse(null);
                return new SupplierPaymentReportLine(invoice==null?"—":text(invoice.getNumeroInterno()),invoice==null?"—":text(invoice.getNumeroProveedor()),
                    invoice==null||invoice.getFecha()==null?"—":DATE.format(invoice.getFecha()),a.getAplicadaEn()==null?"—":DATE.format(a.getAplicadaEn()),a.getSaldoAnterior(),a.getMonto(),a.getSaldoRestante(),prefix);}).toList());
        if(lines.isEmpty()&&payment.getFacturaId()!=null){
            invoices.findByIdAndTenantIdAndEmpresaId(payment.getFacturaId(),payment.getTenantId(),payment.getEmpresaId()).ifPresent(invoice->
                lines.add(new SupplierPaymentReportLine(text(invoice.getNumeroInterno()),text(invoice.getNumeroProveedor()),
                    invoice.getFecha()==null?"—":DATE.format(invoice.getFecha()),payment.getConfirmadoEn()==null?"—":DATE.format(payment.getConfirmadoEn()),
                    payment.getMonto(),payment.getMonto(),java.math.BigDecimal.ZERO,prefix)));
        }
        String source=source(payment);String commercial=data==null?company.getNombre():fallback(data.getNombreComercial(),company.getNombre());
        String legal=data==null?company.getNombre():fallback(data.getRazonSocial(),company.getNombre());
        Proveedor supplier=payment.getProveedor();
        return pdf.generate(new SupplierPaymentReportData(payment.getTenantId(),payment.getEmpresaId(),payment.getId(),payment.getNumero(),
            commercial,legal,company.getIdentificacionFiscal(),data==null?null:data.getDireccion(),data==null?null:data.getTelefono(),data==null?null:data.getCorreo(),logo,
            supplier==null?null:supplier.getNombreComercial(),supplier==null?null:supplier.getIdentificacionFiscal(),supplier==null?null:supplier.getTelefono(),supplier==null?null:supplier.getCorreo(),
            payment.getFecha(),state(payment.getEstado()),payment.getMoneda()==null?null:payment.getMoneda().getCodigoIso(),payment.getMedioPago()==null?null:payment.getMedioPago().getEtiqueta(),
            source,payment.getNumeroCheque(),payment.getReferencia(),payment.getNotas(),payment.getMotivoAnulacion(),payment.getMonto(),payment.getMontoAplicado(),payment.disponible(),
            PurchaseOrderService.issuedByDisplayName(user),OffsetDateTime.now(),lines));
    }

    private static String source(PagoProveedor payment){
        if(payment.getCaja()!=null)return "Caja · "+payment.getCaja().getNombre();
        if(payment.getCuentaBancaria()!=null){var account=payment.getCuentaBancaria();String suffix=account.getNumeroCuentaUltimos4()==null?"":" · •••• "+account.getNumeroCuentaUltimos4();return account.getBancoNombre()+" · "+account.getNombreCuenta()+suffix;}
        return "—";
    }
    private static String state(EstadoPagoProveedor status){return switch(status){case DRAFT->"BORRADOR";case AVAILABLE->"DISPONIBLE";case PARTIALLY_APPLIED->"PARCIALMENTE APLICADO";case APPLIED->"APLICADO";case VOIDED->"ANULADO";};}
    private static String text(String value){return value==null||value.isBlank()?"—":value.trim();}
    private static String fallback(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
}
