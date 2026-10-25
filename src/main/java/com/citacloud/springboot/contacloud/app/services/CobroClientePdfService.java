package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CobroClientePdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final CobroClienteService receipts;
    private final EmpresaRepository companies;
    private final DatosEmpresaRepository companyData;
    private final LocalFileStorageService storage;
    private volatile ReportBundle reports;

    public CobroClientePdfService(CobroClienteService receipts, EmpresaRepository companies,
                                  DatosEmpresaRepository companyData, LocalFileStorageService storage) {
        this.receipts = receipts; this.companies = companies; this.companyData = companyData; this.storage = storage;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.imprimir')")
    public ArchivoPdfDto generar(UUID id) {
        var principal = TenantContext.principalActual();
        CobroClienteDto receipt = receipts.detalle(id);
        Empresa company = companies.findByIdAndTenantId(principal.empresaId(), principal.tenantId())
                .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar la empresa del recibo."));
        DatosEmpresa data = companyData.findByEmpresaId(principal.empresaId()).orElse(null);
        byte[] logo = company.getLogoObjectKey() == null ? null
                : storage.load(company.getLogoObjectKey(), 5L * 1024 * 1024).orElse(null);
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", data == null ? company.getNombre() : first(data.getNombreComercial(), company.getNombre()));
        params.put("companyTaxId", PurchaseOrderPdfService.formatTaxIdentification(company.getIdentificacionFiscal()));
        params.put("companyAddress", data == null ? null : data.getDireccion());
        params.put("companyPhone", data == null ? null : PurchaseOrderPdfService.formatPhone(data.getTelefono()));
        params.put("companyEmail", data == null ? null : data.getCorreo());
        params.put("companyLogo", logo == null ? null : new ByteArrayInputStream(logo));
        params.put("number", receipt.numero()); params.put("date", DATE.format(receipt.fecha()));
        params.put("customer", receipt.cliente()); params.put("customerId", value(receipt.identificacion()));
        params.put("branch", receipt.sucursal());
        params.put("currency", receipt.moneda()); params.put("status", receipt.estado().name());
        params.put("reference", value(receipt.referencia())); params.put("notes", value(receipt.observacion()));
        params.put("user", value(receipt.usuario())); params.put("received", receipt.recibido());
        params.put("applied", receipt.aplicado()); params.put("available", receipt.disponible());
        ReportBundle templates = reports();
        params.put("applicationsReport", templates.applications());
        Collection<Map<String,?>> applicationRows=new ArrayList<>();
        receipt.aplicaciones().forEach(a -> applicationRows.add(Map.of(
                "invoice",a.factura()+(a.revertida()?" (REVERTIDA)":""),"previous",a.saldoAnterior(),
                "applied",a.monto(),"remaining",a.saldoRestante())));
        params.put("applicationsDataSource", new JRMapCollectionDataSource(applicationRows));
        params.put("methodsReport", templates.methods());
        Collection<Map<String,?>> methodRows=new ArrayList<>();
        receipt.medios().forEach(m -> methodRows.add(Map.of("method",m.medio(),"destination",value(m.destino()),
                "reference",value(m.referencia()),"amount",m.monto())));
        params.put("methodsDataSource", new JRMapCollectionDataSource(methodRows));
        try {
            byte[] bytes = JasperExportManager.exportReportToPdf(
                    JasperFillManager.fillReport(templates.main(), params, new JREmptyDataSource()));
            return new ArchivoPdfDto("Recibo_" + receipt.numero() + ".pdf", bytes);
        } catch (JRException ex) { throw new ReglaNegocioException("No fue posible generar el PDF del recibo.", ex); }
    }

    private ReportBundle reports() {
        if (reports != null) return reports;
        synchronized (this) {
            if (reports == null) {
                try {
                    reports = new ReportBundle(compile("/reports/customer-receipt/customer-receipt-compact.jrxml"),
                            compile("/reports/customer-receipt/customer-receipt-applications.jrxml"),
                            compile("/reports/customer-receipt/customer-receipt-methods.jrxml"));
                } catch (JRException ex) {
                    throw new ReglaNegocioException("No fue posible compilar la plantilla del recibo.", ex);
                }
            }
            return reports;
        }
    }
    private JasperReport compile(String resource) throws JRException {
        try(InputStream input=getClass().getResourceAsStream(resource)){
            if(input==null)throw new ReglaNegocioException("No se encontró la plantilla del recibo.");
            return JasperCompileManager.compileReport(input);
        }catch(IOException ex){throw new ReglaNegocioException("No fue posible leer la plantilla del recibo.",ex);}
    }
    private static String first(String first, String fallback) { return first == null || first.isBlank() ? fallback : first.trim(); }
    private static String value(String value) { return value == null || value.isBlank() ? "—" : value.trim(); }
    private record ReportBundle(JasperReport main,JasperReport applications,JasperReport methods){}
}
