package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.ValeCajaDto;
import com.citacloud.springboot.contacloud.app.models.EstadoValeCaja;
import net.sf.jasperreports.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ValeCajaPdfService {
    private static final Logger log = LoggerFactory.getLogger(ValeCajaPdfService.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final long MAX_LOGO_BYTES = 5L * 1024 * 1024;
    private final ValeCajaService vales;
    private final LocalFileStorageService storage;
    private volatile JasperReport reporte;

    public ValeCajaPdfService(ValeCajaService vales, LocalFileStorageService storage) {
        this.vales = vales;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('vales_caja.imprimir')")
    public ArchivoPdfDto generar(UUID id) {
        ValeCajaDto vale = vales.obtener(id);
        byte[] logo = cargarLogo(vale);
        boolean anulado = vale.estado() == EstadoValeCaja.VOIDED;
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("companyName", vale.empresaNombre());
        parametros.put("companyTaxId", PurchaseOrderPdfService.formatTaxIdentification(vale.empresaIdentificacion()));
        parametros.put("companyAddress", vale.empresaDireccion());
        parametros.put("companyPhone", PurchaseOrderPdfService.formatPhone(vale.empresaTelefono()));
        parametros.put("companyEmail", vale.empresaCorreo());
        parametros.put("companyLogo", logo == null ? null : new ByteArrayInputStream(logo));
        parametros.put("hasLogo", logo != null);
        parametros.put("number", vale.numero());
        parametros.put("status", anulado ? "ANULADO" : "CONFIRMADO");
        parametros.put("cash", vale.caja());
        parametros.put("branch", vale.sucursal());
        parametros.put("dateTime", FECHA_HORA.format(vale.fechaHora()));
        parametros.put("user", vale.usuario());
        parametros.put("concept", vale.concepto());
        parametros.put("beneficiary", vale.beneficiario());
        parametros.put("reference", vale.referencia());
        parametros.put("hasReference", tieneTexto(vale.referencia()));
        parametros.put("notes", vale.observacion());
        parametros.put("hasObservation", tieneTexto(vale.observacion()));
        parametros.put("voided", anulado);
        parametros.put("voidDateTime", vale.anuladoEn() == null ? null : FECHA_HORA.format(vale.anuladoEn()));
        parametros.put("voidUser", vale.usuarioAnulacion());
        parametros.put("voidReason", vale.motivoAnulacion());
        parametros.put("totalText", vale.monedaCodigo() + " "
            + String.format(Locale.US, "%,.2f", vale.importe().setScale(2, RoundingMode.HALF_UP)));
        try {
            byte[] contenido = JasperExportManager.exportReportToPdf(
                JasperFillManager.fillReport(reporte(), parametros, new JREmptyDataSource()));
            return new ArchivoPdfDto("Vale_" + vale.numero() + ".pdf", contenido);
        } catch (JRException ex) {
            throw new ReglaNegocioException("No fue posible generar el PDF del vale de caja.", ex);
        }
    }

    private byte[] cargarLogo(ValeCajaDto vale) {
        if (!tieneTexto(vale.empresaLogoObjectKey())) return null;
        try {
            return storage.load(vale.empresaLogoObjectKey(), MAX_LOGO_BYTES).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("No fue posible cargar el logo histórico del vale {}", vale.id(), ex);
            return null;
        }
    }

    private JasperReport reporte() {
        if (reporte == null) synchronized (this) {
            if (reporte == null) {
                try (InputStream in = getClass().getResourceAsStream("/reports/cash-voucher/cash-voucher.jrxml")) {
                    if (in == null) throw new ReglaNegocioException("No se encontró la plantilla del vale de caja.");
                    reporte = JasperCompileManager.compileReport(in);
                } catch (IOException | JRException ex) {
                    throw new ReglaNegocioException("No fue posible compilar la plantilla del vale de caja.", ex);
                }
            }
        }
        return reporte;
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
