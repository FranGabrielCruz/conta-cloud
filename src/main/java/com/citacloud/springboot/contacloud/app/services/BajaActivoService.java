package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.BajaActivoDto.*;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.BajaActivoRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class BajaActivoService {
    private static final String VER = "@empresaModuloService.habilitado('ACTIVOS_FIJOS') and hasAuthority('bajas_activos.ver')";
    private static final String CREAR = VER + " and hasAuthority('bajas_activos.crear')";
    private static final String EDITAR = VER + " and hasAuthority('bajas_activos.editar')";
    private static final String CONFIRMAR = VER + " and hasAuthority('bajas_activos.confirmar')";
    private static final String CAPTURAR = "(" + CREAR + ") or (" + EDITAR + ")";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BajaActivoRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;
    private final AuditoriaService auditoria;

    public BajaActivoService(BajaActivoRepository repo, EmpresaRepository empresas,
                            DatosEmpresaRepository datosEmpresa, AuditoriaService auditoria) {
        this.repo = repo;
        this.empresas = empresas;
        this.datosEmpresa = datosEmpresa;
        this.auditoria = auditoria;
    }

    @PreAuthorize(VER)
    public LocalDate hoy() { return hoy(principal()); }

    @PreAuthorize(VER)
    public List<Tipo> tipos() { return repo.tipos(); }

    @PreAuthorize(VER)
    public Page<Fila> listar(Filtro filtro, int pagina, int tamano) {
        if (pagina < 0 || !Set.of(10, 25, 50, 100).contains(tamano)
                || filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta()))
            throw new ReglaNegocioException("Revisa el rango de fechas.");
        return repo.listar(principal(), new Filtro(filtro.desde(), filtro.hasta(), filtro.tipo(), filtro.estado(),
            Objects.toString(texto(filtro.buscar(), 150), "")), PageRequest.of(pagina, tamano));
    }

    @PreAuthorize(VER)
    public List<Total> totales() {
        var p = principal();
        var zona = ZoneId.of(empresa(p).getZonaHoraria());
        var mes = LocalDate.now(zona).withDayOfMonth(1);
        return repo.totales(p, mes.atStartOfDay(zona).toOffsetDateTime(),
            mes.plusMonths(1).atStartOfDay(zona).toOffsetDateTime());
    }

    @PreAuthorize(VER)
    public Detalle detalle(UUID id) { return detalle(principal(), id, false); }

    @PreAuthorize(VER)
    public Optional<Detalle> confirmadaDeActivo(UUID id) { return repo.confirmada(principal(), id); }

    @PreAuthorize(CAPTURAR)
    public List<Activo> buscarActivos(String buscar, int offset, int limite) {
        return repo.buscarActivos(principal(), Objects.toString(texto(buscar, 150), ""), offset, limite, hoy(principal()));
    }

    @PreAuthorize(CAPTURAR)
    public Activo situacion(UUID id, LocalDate fecha) {
        return activo(principal(), id, fecha == null ? hoy(principal()) : fecha, false);
    }

    @PreAuthorize(CAPTURAR)
    public List<Factura> facturas(UUID activo, String buscar, int offset, int limite, UUID seleccionada) {
        var p = principal();
        var a = activo(p, activo, hoy(p), false);
        return repo.facturas(p, a.monedaId(), Objects.toString(texto(buscar, 150), ""), offset, limite, seleccionada, false);
    }

    @PreAuthorize(CREAR)
    @Transactional
    public UUID crear(Entrada entrada) {
        var p = principal();
        var datos = normalizar(entrada);
        var a = validarBorrador(p, datos);
        var id = UUID.randomUUID();
        repo.crear(p, id, repo.siguiente(p), datos, a);
        auditar(p, detalle(p, id, false), "CREAR", a);
        return id;
    }

    @PreAuthorize(EDITAR)
    @Transactional
    public void editar(UUID id, long version, Entrada entrada) {
        var p = principal();
        var d = detalle(p, id, true);
        exigirBorrador(d, version);
        var datos = normalizar(entrada);
        var a = validarBorrador(p, datos);
        repo.editar(p, id, datos, a);
        auditar(p, detalle(p, id, false), "EDITAR", a);
    }

    @PreAuthorize(CONFIRMAR)
    public Revision revisar(UUID id) {
        var p = principal();
        var d = detalle(p, id, false);
        exigirBorrador(d, d.version());
        var a = activo(p, d.datos().activo(), d.datos().fecha(), false);
        validarConfirmacion(p, d, a);
        return new Revision(d, a, resultado(a.libros(), d.datos().obtenido()));
    }

    @PreAuthorize(CONFIRMAR)
    @Transactional
    public void confirmar(Revision revision, UUID solicitud) {
        if (revision == null || solicitud == null) throw new ReglaNegocioException("Solicitud de confirmación inválida.");
        var p = principal();
        var d = detalle(p, revision.detalle().id(), true);
        if (d.estado() == Estado.CONFIRMADA && solicitud.equals(d.solicitud()) && p.usuarioId().equals(d.confirmadoPor())) return;
        exigirBorrador(d, revision.detalle().version());
        if (repo.periodoCerrado(p, d.datos().fecha())) throw new ReglaNegocioException("El período fiscal de la baja está cerrado.");
        var a = activo(p, d.datos().activo(), d.datos().fecha(), true);
        validarConfirmacion(p, d, a);
        if (!a.equals(revision.activo())) throw new ReglaNegocioException("El activo cambió. Revisa los valores y vuelve a confirmar.");
        var factura = factura(p, d.datos(), a, true);
        repo.confirmar(p, d, new Snapshot(a, d.tipo(), factura, emisor(p), p.nombre()), solicitud);
        auditar(p, d, "CONFIRMAR", a);
    }

    @PreAuthorize(VER + " and hasAuthority('bajas_activos.imprimir')")
    public Detalle documento(UUID id) {
        var d = detalle(principal(), id, false);
        if (d.estado() != Estado.CONFIRMADA) throw new ReglaNegocioException("Solo se imprimen bajas confirmadas.");
        return d;
    }

    public static Resultado resultado(BigDecimal libros, BigDecimal obtenido) {
        var diferencia = obtenido.subtract(libros);
        return new Resultado(diferencia.max(BigDecimal.ZERO), diferencia.negate().max(BigDecimal.ZERO));
    }

    private Activo validarBorrador(TenantPrincipal p, Entrada e) {
        if (repo.tipos().stream().noneMatch(t -> t.codigo().equals(e.tipo())))
            throw new ReglaNegocioException("Selecciona un tipo de baja válido.");
        var a = activo(p, e.activo(), e.fecha(), true);
        validarFecha(e, a);
        if (a.retirado()) throw new ReglaNegocioException("El activo ya está dado de baja.");
        factura(p, e, a, false);
        return a;
    }

    private void validarConfirmacion(TenantPrincipal p, Detalle d, Activo a) {
        validarFecha(d.datos(), a);
        if (d.datos().fecha().isAfter(hoy(p))) throw new ReglaNegocioException("La fecha efectiva no puede ser futura.");
        if (a.retirado()) throw new ReglaNegocioException("El activo ya está dado de baja.");
        if (a.movimientosPosteriores()) throw new ReglaNegocioException("El activo tiene movimientos posteriores a la fecha efectiva.");
        if (a.mantenimientoPendiente()) throw new ReglaNegocioException("Resuelve los mantenimientos pendientes del activo antes de confirmar.");
        if (a.libros().signum() < 0) throw new ReglaNegocioException("Revisa el valor en libros del activo.");
    }

    private static void validarFecha(Entrada e, Activo a) {
        if (e.fecha().isBefore(a.adquisicion()))
            throw new ReglaNegocioException("La fecha efectiva debe ser igual o posterior a la adquisición.");
    }

    private String factura(TenantPrincipal p, Entrada e, Activo a, boolean bloquear) {
        if (e.factura() == null) return null;
        if (!"VENTA".equals(e.tipo())) throw new ReglaNegocioException("Solo las bajas por venta admiten una factura vinculada.");
        return repo.facturas(p, a.monedaId(), "", 0, 1, e.factura(), bloquear).stream().findFirst().map(Factura::codigo)
            .orElseThrow(() -> new ReglaNegocioException("Selecciona una factura de venta confirmada en la moneda del activo."));
    }

    private Activo activo(TenantPrincipal p, UUID id, LocalDate fecha, boolean bloquear) {
        return repo.activo(p, id, fecha, bloquear)
            .orElseThrow(() -> new ReglaNegocioException("El activo seleccionado no está disponible."));
    }

    private Detalle detalle(TenantPrincipal p, UUID id, boolean bloquear) {
        return repo.detalle(p, id, bloquear).orElseThrow(() -> new RecursoNoEncontradoException("Baja no encontrada."));
    }

    private static void exigirBorrador(Detalle d, long version) {
        if (d.estado() != Estado.BORRADOR || d.version() != version)
            throw new ReglaNegocioException("La baja cambió. Vuelve a abrirla.");
    }

    private static Entrada normalizar(Entrada e) {
        if (e == null || e.activo() == null || e.fecha() == null || e.tipo() == null || texto(e.motivo(), 2000) == null)
            throw new ReglaNegocioException("Completa el activo, tipo, fecha efectiva y motivo.");
        var valor = e.obtenido() == null ? BigDecimal.ZERO : e.obtenido();
        try {
            valor = valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ReglaNegocioException("El valor obtenido admite hasta dos decimales.");
        }
        if (valor.signum() < 0 || valor.precision() > 19) throw new ReglaNegocioException("Indica un valor obtenido válido.");
        return new Entrada(e.activo(), e.tipo(), e.fecha(), texto(e.motivo(), 2000), valor, texto(e.referencia(), 150),
            e.factura(), texto(e.observacion(), 2000));
    }

    private static String texto(String texto, int maximo) {
        if (texto == null || texto.isBlank()) return null;
        var limpio = texto.strip();
        if (limpio.length() > maximo) throw new ReglaNegocioException("Un campo supera " + maximo + " caracteres.");
        return limpio;
    }

    private void auditar(TenantPrincipal p, Detalle d, String accion, Activo a) {
        var traceId = TraceContext.currentOrCreate();
        auditoria.registrar(accion, "BajaActivo", d.id(), JSON.writeValueAsString(Map.of(
            "codigo", d.codigo(), "activoId", a.id(), "sucursalId", a.sucursal(),
            "fecha", d.datos().fecha().toString(), "tipo", d.datos().tipo(), "moneda", a.moneda(),
            "valorLibros", a.libros(), "obtenido", d.datos().obtenido(), "traceId", traceId)));
        LoggerFactory.getLogger(BajaActivoService.class).atInfo().addKeyValue("TraceId", traceId)
            .addKeyValue("CompanyId", p.empresaId()).addKeyValue("AssetId", a.id())
            .addKeyValue("DisposalId", d.id()).addKeyValue("Operacion", accion).log("activo_baja");
    }

    private LocalDate hoy(TenantPrincipal p) { return LocalDate.now(ZoneId.of(empresa(p).getZonaHoraria())); }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(), p.tenantId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private Empresa emisor(TenantPrincipal p) {
        var empresa = empresa(p);
        var datos = datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        var comercial = datos == null ? null : datos.getNombreComercial();
        return new Empresa(comercial == null || comercial.isBlank() ? empresa.getNombre() : comercial,
            empresa.getIdentificacionFiscal(), datos == null ? null : datos.getDireccion(),
            datos == null ? null : datos.getTelefono(), empresa.getPaisCodigo(), empresa.getLogoObjectKey());
    }

    private static TenantPrincipal principal() { return TenantContext.principalActual(); }
}
