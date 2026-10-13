package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ReporteMovimientoInventarioData;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class MovimientoInventarioPdfService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private volatile JasperReport movimientosReport;
    private volatile JasperReport kardexReport;

    public byte[] generarMovimientos(ReporteMovimientoInventarioData data) {
        return generate(report(false), parameters(data, false), data);
    }

    public byte[] generarKardex(ReporteMovimientoInventarioData data) {
        return generate(report(true), parameters(data, true), data);
    }

    private byte[] generate(JasperReport report, Map<String, Object> parameters,
            ReporteMovimientoInventarioData data) {
        try {
            JasperPrint print = JasperFillManager.fillReport(report, parameters,
                new JRBeanCollectionDataSource(data.lineas(), false));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException exception) {
            throw new ReglaNegocioException("No fue posible generar el PDF. Intente nuevamente.", exception);
        }
    }

    static Map<String, Object> parameters(ReporteMovimientoInventarioData data, boolean kardex) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("companyTradeName", value(data.empresaNombreComercial()));
        parameters.put("companyLegalName", optional(data.empresaRazonSocial()));
        parameters.put("companyTaxIdentification", PurchaseOrderPdfService.formatTaxIdentification(data.empresaIdentificacion()));
        parameters.put("companyAddress", optional(data.empresaDireccion()));
        parameters.put("companyPhone", PurchaseOrderPdfService.formatPhone(data.empresaTelefono()));
        parameters.put("companyEmail", optional(data.empresaCorreo()));
        parameters.put("companyLogo", data.empresaLogo() == null ? null : new ByteArrayInputStream(data.empresaLogo()));
        parameters.put("period", value(data.periodo()));
        parameters.put("search", value(data.busqueda()));
        parameters.put("branch", value(data.sucursal()));
        parameters.put("warehouse", value(data.almacen()));
        parameters.put("product", value(data.producto()));
        parameters.put("productCode", value(data.productoCodigo()));
        parameters.put("unit", value(data.unidad()));
        parameters.put("type", value(data.tipo()));
        parameters.put("origin", value(data.origen()));
        parameters.put("movements", data.movimientos());
        parameters.put("affectedProducts", data.productosAfectados());
        parameters.put("initialBalance", MovimientoInventarioReporteService.number(data.saldoInicial()));
        parameters.put("entries", MovimientoInventarioReporteService.number(data.entradas()));
        parameters.put("exits", MovimientoInventarioReporteService.number(data.salidas()));
        parameters.put("finalBalance", MovimientoInventarioReporteService.number(data.saldoFinal()));
        parameters.put("showEntry", data.mostrarEntrada());
        parameters.put("showExit", data.mostrarSalida());
        parameters.put("showWarehouse", data.mostrarAlmacen());
        parameters.put("layout", data.mostrarEntrada() && data.mostrarSalida() ? "ALL"
            : data.mostrarEntrada() ? "IN" : "OUT");
        parameters.put("generatedBy", value(data.generadoPor()));
        parameters.put("generatedAt", data.generadoEn() == null ? "" : DATE_TIME.format(data.generadoEn()));
        parameters.put("hasMovements", !data.lineas().isEmpty());
        parameters.put("documentTitle", kardex ? "KARDEX DE INVENTARIO" : "MOVIMIENTOS DE INVENTARIO");
        return parameters;
    }

    private JasperReport report(boolean kardex) {
        JasperReport cached = kardex ? kardexReport : movimientosReport;
        if (cached != null) return cached;
        synchronized (this) {
            cached = kardex ? kardexReport : movimientosReport;
            if (cached == null) {
                String path = kardex ? "/reports/inventory-movement/inventory-kardex.jrxml"
                    : "/reports/inventory-movement/inventory-movements.jrxml";
                try (InputStream input = getClass().getResourceAsStream(path)) {
                    if (input == null) throw new ReglaNegocioException("No se encontró la plantilla del reporte de inventario.");
                    cached = JasperCompileManager.compileReport(input);
                    if (kardex) kardexReport = cached; else movimientosReport = cached;
                } catch (java.io.IOException | JRException exception) {
                    throw new ReglaNegocioException("No fue posible preparar el reporte de inventario.", exception);
                }
            }
            return cached;
        }
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
