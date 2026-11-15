package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Filtro;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Linea;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class CatalogoCuentasPdfService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CatalogoCuentasService catalogo;
    private final LocalFileStorageService storage;
    private final Map<Diseno, JasperReport> plantillas = new HashMap<>();

    public CatalogoCuentasPdfService(CatalogoCuentasService catalogo, LocalFileStorageService storage) {
        this.catalogo = catalogo;
        this.storage = storage;
    }

    public ArchivoPdfDto generar(Filtro filtro) {
        var r = catalogo.reporte(filtro);
        var empresa = r.empresa();
        try {
            byte[] logo = empresa.logo() == null ? null : storage.load(empresa.logo(), 5L * 1024 * 1024).orElse(null);
            var params = new HashMap<String, Object>();
            params.put("companyTradeName", empresa.nombre());
            params.put("companyTaxIdentification", "DO".equals(empresa.pais())
                ? PurchaseOrderPdfService.formatTaxIdentification(empresa.rnc()) : empresa.rnc());
            params.put("companyAddress", empresa.direccion());
            params.put("companyPhone", "DO".equals(empresa.pais())
                ? PurchaseOrderPdfService.formatPhone(empresa.telefono()) : empresa.telefono());
            params.put("companyLogo", logo == null ? null : new ByteArrayInputStream(logo));
            params.put("orderNumber", "");
            params.put("orderDate", FECHA.format(r.generado()));
            params.put("emissionFooter", "Generado: " + FECHA_HORA.format(r.generado()));
            params.put("chartFilters", describir(r.filtro()));
            List<Map<String, ?>> filas = new ArrayList<>();
            r.cuentas().stream().map(CatalogoCuentasPdfService::fila).forEach(filas::add);
            if (filas.isEmpty()) filas.add(Map.of("rowKind", "TEXT", "rowLabel", "No se encontraron cuentas."));
            var documento = JasperFillManager.fillReport(plantilla(new Diseno(logo != null, r.filtro().restringido())),
                params, new JRMapCollectionDataSource(filas));
            return new ArchivoPdfDto("Catalogo_de_cuentas_" + r.generado().toLocalDate() + ".pdf",
                JasperExportManager.exportReportToPdf(documento));
        } catch (IOException | JRException ex) {
            throw new IllegalStateException("No fue posible generar el catálogo de cuentas.", ex);
        }
    }

    private synchronized JasperReport plantilla(Diseno diseno) throws IOException, JRException {
        var plantilla = plantillas.get(diseno);
        if (plantilla == null) {
            var xml = LargeDocumentPdfTemplate.catalogoCuentas(diseno.logo(), diseno.filtros());
            plantilla = JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            plantillas.put(diseno, plantilla);
        }
        return plantilla;
    }

    private static String describir(Filtro f) {
        var filtros = new ArrayList<String>();
        if (f.tipo() != null) filtros.add("Tipo: " + f.tipo().etiqueta());
        if (f.naturaleza() != null) filtros.add("Naturaleza: " + f.naturaleza().etiqueta());
        if (f.activo() != null) filtros.add("Estado: " + (f.activo() ? "Activo" : "Inactivo"));
        if (f.buscar() != null && !f.buscar().isBlank()) filtros.add("Buscar: " + f.buscar());
        return String.join("  •  ", filtros);
    }

    private static Map<String, String> fila(Linea l) {
        var c = l.cuenta();
        var codigo = "\u00a0".repeat(Math.min(l.nivel() - 1, 12) * 3) + c.codigo();
        return Map.of("rowKind", "LINE", "dato0", codigo, "dato1", c.nombre(),
            "dato2", c.tipo().etiqueta(), "dato3", c.clasificacion().etiqueta(),
            "dato4", c.naturaleza().etiqueta(), "dato5", c.activo() ? "Activo" : "Inactivo");
    }

    private record Diseno(boolean logo, boolean filtros) {}
}
