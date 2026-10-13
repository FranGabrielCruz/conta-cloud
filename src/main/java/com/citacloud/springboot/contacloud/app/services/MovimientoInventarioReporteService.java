package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MovimientoInventarioReporteService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final MovimientoInventarioConsultaService queries;
    private final MovimientoInventarioPdfService pdf;
    private final EmpresaRepository companies;
    private final DatosEmpresaRepository companyData;
    private final UsuarioRepository users;
    private final ProductRepository products;
    private final SucursalRepository branches;
    private final AlmacenRepository warehouses;
    private final LocalFileStorageService storage;

    public MovimientoInventarioReporteService(MovimientoInventarioConsultaService queries,
            MovimientoInventarioPdfService pdf, EmpresaRepository companies, DatosEmpresaRepository companyData,
            UsuarioRepository users, ProductRepository products, SucursalRepository branches,
            AlmacenRepository warehouses, LocalFileStorageService storage) {
        this.queries = queries;
        this.pdf = pdf;
        this.companies = companies;
        this.companyData = companyData;
        this.users = users;
        this.products = products;
        this.branches = branches;
        this.warehouses = warehouses;
        this.storage = storage;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.ver') and hasAuthority('inventario_movimientos.pdf')")
    public ArchivoPdfDto generarGeneral(String buscar, LocalDate desde, LocalDate hasta, UUID sucursalId,
            UUID almacenId, UUID productoId, String tipo, OrigenMovimientoInventario origen) {
        MovimientosInventarioReporteConsultaDto result = queries.generalReporte(buscar, desde, hasta, sucursalId,
            almacenId, productoId, tipo, origen);
        if (result.movimientos().isEmpty())
            throw new ReglaNegocioException("No hay movimientos para generar el reporte.");
        Metadata metadata = metadata();
        Producto product = productoId == null ? null : product(productoId, metadata);
        String normalizedType = normalizeType(tipo);
        List<LineaReporteMovimientoInventario> lines = result.movimientos().stream()
            .map(movement -> line(movement, metadata.zone())).toList();
        ReporteMovimientoInventarioData data = baseData(metadata, desde, hasta, buscar,
            branchName(sucursalId, metadata), warehouseName(almacenId, metadata),
            product == null ? "Todos" : product.getNombre(), product == null ? "Todos" : product.getCodigo(),
            product == null || product.getUnidadMedida() == null ? "Todas" : product.getUnidadMedida().getNombre(),
            typeLabel(normalizedType), origen == null ? "Todos" : origen.getEtiqueta(),
            result.resumen().movimientos(), result.resumen().productosAfectados(), BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, !"SALIDA".equals(normalizedType),
            !"ENTRADA".equals(normalizedType), almacenId == null, lines);
        byte[] content = pdf.generarMovimientos(data);
        return new ArchivoPdfDto(generalFileName(desde, hasta), content);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.kardex') and hasAuthority('inventario_movimientos.pdf')")
    public ArchivoPdfDto generarKardex(UUID productoId, UUID sucursalId, UUID almacenId,
            LocalDate desde, LocalDate hasta) {
        KardexInventarioReporteConsultaDto result = queries.kardexReporte(productoId, sucursalId, almacenId,
            desde, hasta);
        Metadata metadata = metadata();
        Producto product = product(productoId, metadata);
        ResumenKardexDto summary = result.resumen();
        BigDecimal reconciled = summary.saldoInicial().add(summary.entradas()).subtract(summary.salidas());
        if (reconciled.compareTo(summary.saldoFinal()) != 0)
            throw new ReglaNegocioException("El Kardex presenta una inconsistencia de saldos y no puede imprimirse.");
        List<LineaReporteMovimientoInventario> lines = result.movimientos().stream()
            .map(movement -> line(movement, metadata.zone())).toList();
        ReporteMovimientoInventarioData data = baseData(metadata, desde, hasta, "Sin búsqueda",
            branchName(sucursalId, metadata), warehouseName(almacenId, metadata), product.getNombre(),
            product.getCodigo(), product.getUnidadMedida() == null ? "—" : product.getUnidadMedida().getNombre(),
            "Todos", "Todos", result.movimientos().size(), 1, summary.saldoInicial(), summary.entradas(),
            summary.salidas(), summary.saldoFinal(), true, true, almacenId == null, lines);
        byte[] content = pdf.generarKardex(data);
        return new ArchivoPdfDto(kardexFileName(product.getCodigo(), desde, hasta), content);
    }

    private ReporteMovimientoInventarioData baseData(Metadata metadata, LocalDate from, LocalDate to,
            String search, String branch, String warehouse, String product, String productCode, String unit,
            String type, String origin, long movements, long affectedProducts, BigDecimal initial,
            BigDecimal entries, BigDecimal exits, BigDecimal ending, boolean showEntry, boolean showExit,
            boolean showWarehouse, List<LineaReporteMovimientoInventario> lines) {
        return new ReporteMovimientoInventarioData(metadata.principal().tenantId(), metadata.company().getId(),
            metadata.tradeName(), metadata.legalName(), metadata.company().getIdentificacionFiscal(),
            metadata.data() == null ? null : metadata.data().getDireccion(),
            metadata.data() == null ? null : metadata.data().getTelefono(),
            metadata.data() == null ? null : metadata.data().getCorreo(), metadata.logo(), period(from, to),
            clean(search, "Sin búsqueda"), branch, warehouse, product, productCode, unit, type, origin,
            movements, affectedProducts, initial, entries, exits, ending, showEntry, showExit, showWarehouse,
            PurchaseOrderService.issuedByDisplayName(metadata.user()), OffsetDateTime.now(metadata.zone()), lines);
    }

    private Metadata metadata() {
        var principal = TenantContext.principalActual();
        Empresa company = companies.findByIdAndTenantId(principal.empresaId(), principal.tenantId())
            .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar la empresa del reporte."));
        DatosEmpresa data = companyData.findByEmpresaId(principal.empresaId()).orElse(null);
        Usuario user = users.findByIdAndTenantId(principal.usuarioId(), principal.tenantId())
            .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar al usuario del reporte."));
        byte[] logo = company.getLogoObjectKey() == null ? null
            : storage.load(company.getLogoObjectKey(), 5L * 1024 * 1024).orElse(null);
        String tradeName = data == null ? company.getNombre() : clean(data.getNombreComercial(), company.getNombre());
        String legalName = data == null ? company.getNombre() : clean(data.getRazonSocial(), company.getNombre());
        ZoneId zone;
        try {
            zone = ZoneId.of(clean(company.getZonaHoraria(), "America/Santo_Domingo"));
        } catch (DateTimeException ignored) {
            zone = ZoneId.of("America/Santo_Domingo");
        }
        return new Metadata(principal, company, data, user, tradeName, legalName, logo, zone);
    }

    private Producto product(UUID id, Metadata metadata) {
        return products.findByIdAndTenantIdAndEmpresaId(id, metadata.principal().tenantId(), metadata.company().getId())
            .orElseThrow(() -> new ReglaNegocioException("El producto seleccionado no es válido."));
    }

    private String branchName(UUID id, Metadata metadata) {
        if (id == null) return "Todas";
        return branches.findByIdAndEmpresaId(id, metadata.company().getId()).map(Sucursal::getNombre)
            .orElseThrow(() -> new ReglaNegocioException("La sucursal seleccionada no es válida."));
    }

    private String warehouseName(UUID id, Metadata metadata) {
        if (id == null) return "Todos";
        return warehouses.findByIdAndTenantIdAndEmpresaId(id, metadata.principal().tenantId(), metadata.company().getId())
            .map(Almacen::getNombre).orElseThrow(() -> new ReglaNegocioException("El almacén seleccionado no es válido."));
    }

    private static LineaReporteMovimientoInventario line(MovimientoInventarioDto movement, ZoneId zone) {
        return new LineaReporteMovimientoInventario(format(movement.fechaEfectiva(), zone), value(movement.documento()),
            value(movement.producto()), value(movement.productoCodigo()), value(movement.almacen()),
            value(movement.sucursal()), movement.origen().getEtiqueta(), concept(movement.origen()),
            quantity(movement.entrada()), quantity(movement.salida()), number(movement.existenciaPosterior()));
    }

    static String number(BigDecimal value) {
        if (value == null) return "—";
        DecimalFormat format = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US));
        format.setMinimumFractionDigits(2);
        return format.format(value);
    }

    private static String quantity(BigDecimal value) {
        return value == null || value.signum() == 0 ? "—" : number(value);
    }

    private static String format(OffsetDateTime value, ZoneId zone) {
        return value == null ? "—" : DATE_TIME.format(value.atZoneSameInstant(zone));
    }

    private static String period(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "Todos los períodos";
        if (from == null) return "Hasta " + DATE.format(to);
        if (to == null) return "Desde " + DATE.format(from);
        return DATE.format(from) + " - " + DATE.format(to);
    }

    private static String generalFileName(LocalDate from, LocalDate to) {
        return "Movimientos_Inventario_" + fileDate(from, "Inicio") + "_" + fileDate(to, "Actual") + ".pdf";
    }

    private static String kardexFileName(String productCode, LocalDate from, LocalDate to) {
        String safeCode = value(productCode).replaceAll("[^A-Za-z0-9_-]", "_");
        return "Kardex_" + safeCode + "_" + fileDate(from, "Inicio") + "_" + fileDate(to, "Actual") + ".pdf";
    }

    private static String fileDate(LocalDate date, String fallback) {
        return date == null ? fallback : FILE_DATE.format(date);
    }

    private static String normalizeType(String type) {
        return switch (type == null ? "TODOS" : type.toUpperCase(Locale.ROOT)) {
            case "ENTRADA" -> "ENTRADA";
            case "SALIDA" -> "SALIDA";
            default -> "TODOS";
        };
    }

    private static String typeLabel(String type) {
        return switch (type) { case "ENTRADA" -> "Entrada"; case "SALIDA" -> "Salida"; default -> "Todos"; };
    }

    private static String concept(OrigenMovimientoInventario origin) {
        return switch (origin) {
            case INVENTARIO_INICIAL -> "Inventario inicial";
            case RECEPCION -> "Recepción de proveedor";
            case TRANSFERENCIA -> "Transferencia";
            case AJUSTE -> "Ajuste de inventario";
            case CONTEO_FISICO -> "Conteo físico";
            case VENTA -> "Venta";
            case DEVOLUCION -> "Devolución";
            case REVERSION -> "Reversión";
        };
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Metadata(com.citacloud.springboot.contacloud.app.security.TenantPrincipal principal,
            Empresa company, DatosEmpresa data, Usuario user, String tradeName, String legalName,
            byte[] logo, ZoneId zone) {}
}
