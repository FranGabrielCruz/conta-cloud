package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConteoFisicoReporteData;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class ConteoFisicoPdfService {
    private volatile JasperReport hoja;
    private volatile JasperReport resultado;

    public byte[] generar(ConteoFisicoReporteData data, boolean esResultado) {
        try {
            return JasperExportManager.exportReportToPdf(JasperFillManager.fillReport(
                    report(esResultado), params(data), new JRBeanCollectionDataSource(data.lineas(), false)));
        } catch (JRException exception) {
            throw new ReglaNegocioException("No fue posible generar el PDF del conteo físico.", exception);
        }
    }

    static Map<String, Object> params(ConteoFisicoReporteData data) {
        Map<String, Object> params = new HashMap<>();
        params.put("companyTradeName", value(data.empresaNombreComercial()));
        params.put("companyLegalName", optional(data.empresaRazonSocial()));
        params.put("companyTaxIdentification",
                PurchaseOrderPdfService.formatTaxIdentification(data.empresaIdentificacion()));
        params.put("companyAddress", optional(data.empresaDireccion()));
        params.put("companyPhone", PurchaseOrderPdfService.formatPhone(data.empresaTelefono()));
        params.put("companyEmail", optional(data.empresaCorreo()));
        params.put("companyLogo", data.empresaLogo() == null ? null : new ByteArrayInputStream(data.empresaLogo()));
        params.put("number", value(data.numero()));
        params.put("date", value(data.fecha()));
        params.put("warehouse", value(data.almacen()));
        params.put("branch", value(data.sucursal()));
        params.put("type", value(data.tipo()));
        params.put("mode", value(data.modo()));
        params.put("status", value(data.estado()).toUpperCase());
        params.put("reference", value(data.referencia()));
        params.put("notes", value(data.observacion()));
        params.put("showSystem", data.mostrarTeorico());
        params.put("confirmedBy", value(data.confirmadoPor()));
        params.put("confirmedAt", value(data.confirmadoEn()));
        params.put("generatedBy", value(data.generadoPor()));
        params.put("generatedAt", value(data.generadoEn()));
        return params;
    }

    private JasperReport report(boolean esResultado) throws JRException {
        JasperReport current = esResultado ? resultado : hoja;
        if (current != null) return current;
        synchronized (this) {
            current = esResultado ? resultado : hoja;
            if (current == null) {
                String path = esResultado
                        ? "/reports/inventory-count/inventory-count-result.jrxml"
                        : "/reports/inventory-count/inventory-count-sheet.jrxml";
                try (InputStream input = getClass().getResourceAsStream(path)) {
                    if (input == null) throw new ReglaNegocioException("No se encontró la plantilla del conteo físico.");
                    current = JasperCompileManager.compileReport(input);
                    if (esResultado) resultado = current;
                    else hoja = current;
                } catch (IOException exception) {
                    throw new ReglaNegocioException("No fue posible preparar el PDF del conteo físico.", exception);
                }
            }
            return current;
        }
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
