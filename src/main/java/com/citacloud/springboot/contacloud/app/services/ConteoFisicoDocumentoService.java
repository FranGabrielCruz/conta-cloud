package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.ConteoFisicoReporteData;
import com.citacloud.springboot.contacloud.app.models.ConteoFisico;
import com.citacloud.springboot.contacloud.app.models.DatosEmpresa;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.models.EstadoConteoFisico;
import com.citacloud.springboot.contacloud.app.models.ModoConteoFisico;
import com.citacloud.springboot.contacloud.app.repositories.ConteoFisicoRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.LineaConteoFisicoRepository;
import com.citacloud.springboot.contacloud.app.repositories.UsuarioRepository;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ConteoFisicoDocumentoService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ConteoFisicoRepository conteos;
    private final LineaConteoFisicoRepository lineas;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;
    private final UsuarioRepository usuarios;
    private final LocalFileStorageService storage;
    private final ConteoFisicoPdfService pdf;

    public ConteoFisicoDocumentoService(
            ConteoFisicoRepository conteos,
            LineaConteoFisicoRepository lineas,
            EmpresaRepository empresas,
            DatosEmpresaRepository datosEmpresa,
            UsuarioRepository usuarios,
            LocalFileStorageService storage,
            ConteoFisicoPdfService pdf) {
        this.conteos = conteos;
        this.lineas = lineas;
        this.empresas = empresas;
        this.datosEmpresa = datosEmpresa;
        this.usuarios = usuarios;
        this.storage = storage;
        this.pdf = pdf;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('conteo_fisico.hoja_pdf')")
    public ArchivoPdfDto hoja(UUID id) {
        return generar(id, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('conteo_fisico.resultado_pdf')")
    public ArchivoPdfDto resultado(UUID id) {
        return generar(id, true);
    }

    private ArchivoPdfDto generar(UUID id, boolean esResultado) {
        var principal = TenantContext.principalActual();
        ConteoFisico conteo = conteos.findByIdAndTenantIdAndEmpresaId(
                        id, principal.tenantId(), principal.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Conteo físico no encontrado."));
        if (!EmpresaContext.permiteSucursal(conteo.getAlmacen().getSucursalId())) {
            throw new ReglaNegocioException("No tienes acceso al almacén de este conteo.");
        }
        if (esResultado && conteo.getEstado() != EstadoConteoFisico.CONFIRMED) {
            throw new ReglaNegocioException("El resultado PDF solo está disponible para conteos confirmados.");
        }
        if (!esResultado && conteo.getEstado() != EstadoConteoFisico.IN_PROGRESS
                && conteo.getEstado() != EstadoConteoFisico.PENDING_CONFIRMATION) {
            throw new ReglaNegocioException("La hoja de conteo solo está disponible durante la captura o revisión.");
        }

        Empresa empresa = empresas.findByIdAndTenantId(principal.empresaId(), principal.tenantId())
                .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar la empresa del documento."));
        DatosEmpresa datos = datosEmpresa.findByEmpresaId(principal.empresaId()).orElse(null);
        var usuario = usuarios.findByIdAndTenantId(principal.usuarioId(), principal.tenantId())
                .orElseThrow(() -> new ReglaNegocioException("No fue posible identificar al usuario."));
        ZoneId zone = zone(empresa.getZonaHoraria());
        byte[] logo = empresa.getLogoObjectKey() == null
                ? null
                : storage.load(empresa.getLogoObjectKey(), 5L * 1024 * 1024).orElse(null);

        boolean mostrarTeorico = esResultado || conteo.getModo() == ModoConteoFisico.VISIBLE;
        int[] orden = {0};
        var detalle = lineas.findAllByConteoIdOrderByOrden(id).stream()
                .map(linea -> new ConteoFisicoReporteData.Linea(
                        ++orden[0], linea.getCodigo(), linea.getProducto(), linea.getUnidad(),
                        mostrarTeorico ? numero(linea.getCantidadInicial()) : "",
                        numero(linea.getCantidadContada()),
                        esResultado ? numero(linea.getCantidadTeorica()) : "",
                        esResultado ? firmado(linea.getDiferencia()) : ""))
                .toList();

        ConteoFisicoReporteData reporte = new ConteoFisicoReporteData(
                datos == null ? empresa.getNombre() : primero(datos.getNombreComercial(), empresa.getNombre()),
                datos == null ? empresa.getNombre() : primero(datos.getRazonSocial(), empresa.getNombre()),
                empresa.getIdentificacionFiscal(),
                datos == null ? null : datos.getDireccion(),
                datos == null ? null : datos.getTelefono(),
                datos == null ? null : datos.getCorreo(),
                logo,
                conteo.getNumero(), DATE.format(conteo.getFecha()),
                primero(conteo.getAlmacenHistorico(), conteo.getAlmacen().getNombre()),
                primero(conteo.getSucursalHistorica(), conteo.getAlmacen().getSucursal().getNombre()),
                conteo.getTipo().getEtiqueta(), conteo.getModo().getEtiqueta(), conteo.getEstado().getEtiqueta(),
                conteo.getReferencia(), conteo.getObservacion(), mostrarTeorico,
                conteo.getConfirmadoPorNombre(), fechaHora(conteo.getConfirmadoEn(), zone),
                PurchaseOrderService.issuedByDisplayName(usuario), DATE_TIME.format(OffsetDateTime.now(zone)), detalle);
        return new ArchivoPdfDto((esResultado ? "Resultado_" : "Hoja_") + conteo.getNumero() + ".pdf",
                pdf.generar(reporte, esResultado));
    }

    private static String numero(java.math.BigDecimal value) {
        return value == null ? "" : MovimientoInventarioReporteService.number(value);
    }

    private static String firmado(java.math.BigDecimal value) {
        return value == null ? "" : (value.signum() > 0 ? "+" : "")
                + MovimientoInventarioReporteService.number(value);
    }

    private static String fechaHora(OffsetDateTime value, ZoneId zone) {
        return value == null ? "—" : DATE_TIME.format(value.atZoneSameInstant(zone));
    }

    private static ZoneId zone(String value) {
        try {
            return ZoneId.of(primero(value, "America/Santo_Domingo"));
        } catch (DateTimeException exception) {
            return ZoneId.of("America/Santo_Domingo");
        }
    }

    private static String primero(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
