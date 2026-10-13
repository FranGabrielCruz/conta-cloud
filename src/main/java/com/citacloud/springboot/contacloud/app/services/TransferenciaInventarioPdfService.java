package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.TransferenciaInventarioReporteData;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class TransferenciaInventarioPdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private volatile JasperReport compiledReport;

    public byte[] generar(TransferenciaInventarioReporteData data, ZoneId zone) {
        try {
            JasperPrint print = JasperFillManager.fillReport(report(), parameters(data, zone),
                new JRBeanCollectionDataSource(data.lineas(), false));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException exception) {
            throw new ReglaNegocioException("No fue posible generar el PDF de la transferencia.", exception);
        }
    }

    static Map<String, Object> parameters(TransferenciaInventarioReporteData data, ZoneId zone) {
        Map<String, Object> p = new HashMap<>();
        p.put("companyTradeName", value(data.empresaNombreComercial()));
        p.put("companyLegalName", optional(data.empresaRazonSocial()));
        p.put("companyTaxIdentification", PurchaseOrderPdfService.formatTaxIdentification(data.empresaIdentificacion()));
        p.put("companyAddress", optional(data.empresaDireccion()));
        p.put("companyPhone", PurchaseOrderPdfService.formatPhone(data.empresaTelefono()));
        p.put("companyEmail", optional(data.empresaCorreo()));
        p.put("companyLogo", data.empresaLogo() == null ? null : new ByteArrayInputStream(data.empresaLogo()));
        p.put("transferNumber", value(data.numero()));
        p.put("status", value(data.estado()).toUpperCase());
        p.put("transferDate", data.fecha() == null ? "—" : DATE.format(data.fecha()));
        p.put("transferType", value(data.tipo()));
        p.put("reference", value(data.referencia()));
        p.put("notes", value(data.observacion()));
        p.put("sourceWarehouse", value(data.almacenOrigen())); p.put("sourceBranch", value(data.sucursalOrigen()));
        p.put("destinationWarehouse", value(data.almacenDestino())); p.put("destinationBranch", value(data.sucursalDestino()));
        p.put("dispatchedAt", format(data.despachadaEn(), zone)); p.put("dispatchedBy", value(data.despachadaPor()));
        p.put("receivedAt", format(data.recibidaEn(), zone)); p.put("receivedBy", value(data.recibidaPor()));
        p.put("hasReceipt", data.recibidaEn() != null); p.put("generatedBy", value(data.generadoPor()));
        p.put("generatedAt", format(data.generadoEn(), zone));
        return p;
    }

    private JasperReport report() throws JRException {
        JasperReport result = compiledReport;
        if (result != null) return result;
        synchronized (this) {
            if (compiledReport == null) {
                try (InputStream input = getClass().getResourceAsStream("/reports/inventory-transfer/inventory-transfer.jrxml")) {
                    if (input == null) throw new ReglaNegocioException("No se encontró la plantilla de transferencia de inventario.");
                    compiledReport = JasperCompileManager.compileReport(input);
                } catch (java.io.IOException exception) {
                    throw new ReglaNegocioException("No fue posible preparar el PDF de la transferencia.", exception);
                }
            }
            return compiledReport;
        }
    }

    private static String format(OffsetDateTime date, ZoneId zone) {
        return date == null ? "—" : DATE_TIME.format(date.atZoneSameInstant(zone));
    }
    private static String value(String value) { return value == null || value.isBlank() ? "—" : value.trim(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
