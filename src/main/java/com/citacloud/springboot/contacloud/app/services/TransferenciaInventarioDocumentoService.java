package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Service
public class TransferenciaInventarioDocumentoService {
    private final TransferenciaInventarioRepository transfers;
    private final LineaTransferenciaInventarioRepository lines;
    private final EmpresaRepository companies;
    private final DatosEmpresaRepository companyData;
    private final UsuarioRepository users;
    private final LocalFileStorageService storage;
    private final TransferenciaInventarioPdfService pdf;

    public TransferenciaInventarioDocumentoService(TransferenciaInventarioRepository transfers,
            LineaTransferenciaInventarioRepository lines, EmpresaRepository companies,
            DatosEmpresaRepository companyData, UsuarioRepository users, LocalFileStorageService storage,
            TransferenciaInventarioPdfService pdf) {
        this.transfers = transfers; this.lines = lines; this.companies = companies;
        this.companyData = companyData; this.users = users; this.storage = storage; this.pdf = pdf;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_transferencias.ver') and hasAuthority('inventario_transferencias.pdf')")
    public ArchivoPdfDto generar(UUID id) {
        var principal = TenantContext.principalActual();
        TransferenciaInventario transfer = transfers.findByIdAndTenantIdAndEmpresaId(id,
            principal.tenantId(), principal.empresaId()).orElseThrow(() -> new RecursoNoEncontradoException("Transferencia no encontrada."));
        if (!EmpresaContext.permiteSucursal(transfer.getOrigen().getSucursalId())
                || !EmpresaContext.permiteSucursal(transfer.getDestino().getSucursalId()))
            throw new ReglaNegocioException("No tienes acceso a las sucursales de esta transferencia.");
        if (transfer.getEstado() != EstadoTransferenciaInventario.IN_TRANSIT
                && transfer.getEstado() != EstadoTransferenciaInventario.RECEIVED)
            throw new ReglaNegocioException("El PDF oficial solo está disponible después de confirmar o despachar la transferencia.");

        Empresa company = companies.findByIdAndTenantId(principal.empresaId(), principal.tenantId())
            .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar la empresa del documento."));
        DatosEmpresa data = companyData.findByEmpresaId(principal.empresaId()).orElse(null);
        Usuario generatedBy = users.findByIdAndTenantId(principal.usuarioId(), principal.tenantId())
            .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar al usuario que genera el documento."));
        ZoneId zone = zone(company.getZonaHoraria());
        byte[] logo = company.getLogoObjectKey() == null ? null
            : storage.load(company.getLogoObjectKey(), 5L * 1024 * 1024).orElse(null);

        var reportLines = lines.findAllByTransferenciaIdOrderByOrden(transfer.getId()).stream().map(line ->
            new TransferenciaInventarioReporteData.Linea(line.getOrden(), line.getCodigoProducto(),
                line.getNombreProducto(), same(line.getNombreProducto(), line.getDescripcion()) ? "—" : line.getDescripcion(),
                line.getUnidad(), MovimientoInventarioReporteService.number(line.getCantidadDespachada()),
                transfer.getRecibidaEn() == null ? "—" : MovimientoInventarioReporteService.number(line.getCantidadRecibida()),
                transfer.getRecibidaEn() == null ? "—" : MovimientoInventarioReporteService.number(line.getDiferencia()),
                value(line.getMotivoDiferencia()))).toList();

        TransferenciaInventarioReporteData report = new TransferenciaInventarioReporteData(
            data == null ? company.getNombre() : first(data.getNombreComercial(), company.getNombre()),
            data == null ? company.getNombre() : first(data.getRazonSocial(), company.getNombre()),
            company.getIdentificacionFiscal(), data == null ? null : data.getDireccion(),
            data == null ? null : data.getTelefono(), data == null ? null : data.getCorreo(), logo,
            transfer.getNumero(), transfer.getEstado().getEtiqueta(), transfer.getFecha(), transfer.getTipo().getEtiqueta(),
            transfer.getReferencia(), transfer.getNotas(),
            first(transfer.getAlmacenOrigenHistorico(), transfer.getOrigen().getNombre()),
            first(transfer.getSucursalOrigenHistorica(), transfer.getOrigen().getSucursal().getNombre()),
            first(transfer.getAlmacenDestinoHistorico(), transfer.getDestino().getNombre()),
            first(transfer.getSucursalDestinoHistorica(), transfer.getDestino().getSucursal().getNombre()),
            transfer.getDespachadaEn(), actor(transfer.getDespachadaPorNombre(), transfer.getDespachadaPor(), principal.tenantId()),
            transfer.getRecibidaEn(), actor(transfer.getRecibidaPorNombre(), transfer.getRecibidaPor(), principal.tenantId()),
            PurchaseOrderService.issuedByDisplayName(generatedBy), OffsetDateTime.now(zone), reportLines);
        return new ArchivoPdfDto("Transferencia_" + transfer.getNumero() + ".pdf", pdf.generar(report, zone));
    }

    private String actor(String snapshot, UUID id, UUID tenantId) {
        if (snapshot != null && !snapshot.isBlank()) return snapshot;
        return id == null ? null : users.findByIdAndTenantId(id, tenantId)
            .map(PurchaseOrderService::issuedByDisplayName).orElse(null);
    }
    private static ZoneId zone(String value) { try { return ZoneId.of(first(value, "America/Santo_Domingo")); } catch (DateTimeException ignored) { return ZoneId.of("America/Santo_Domingo"); } }
    private static String first(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String value(String value) { return value == null || value.isBlank() ? "—" : value.trim(); }
    private static boolean same(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }
}
