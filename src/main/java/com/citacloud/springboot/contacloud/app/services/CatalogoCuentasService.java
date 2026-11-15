package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.CuentaContableRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class CatalogoCuentasService {
    public static final String RECURSO = "catalogo_cuentas.";
    private static final String VER = "@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('catalogo_cuentas.ver')";
    private static final String CREAR = VER + " and hasAuthority('catalogo_cuentas.crear')";
    private static final String EDITAR = VER + " and hasAuthority('catalogo_cuentas.editar')";
    private static final Filtro SIN_FILTRO = new Filtro(null, null, null, "");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final CuentaContableRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;
    private final AuditoriaService auditoria;

    public CatalogoCuentasService(CuentaContableRepository repo, EmpresaRepository empresas,
                                  DatosEmpresaRepository datosEmpresa, AuditoriaService auditoria) {
        this.repo = repo;
        this.empresas = empresas;
        this.datosEmpresa = datosEmpresa;
        this.auditoria = auditoria;
    }

    @PreAuthorize(VER)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<Cuenta> raices(Filtro f, int pagina, int tamano) {
        if (pagina < 0 || !Set.of(10, 25, 50, 100).contains(tamano))
            throw new ReglaNegocioException("Paginación inválida.");
        return repo.raices(principal(), filtro(f), PageRequest.of(pagina, tamano));
    }

    @PreAuthorize(VER)
    public List<Cuenta> hijos(UUID id, Filtro f) {
        var p = principal();
        cuenta(p, id);
        return repo.hijos(p, id, filtro(f));
    }

    @PreAuthorize("(" + CREAR + ") or (" + EDITAR + ")")
    public List<Cuenta> padres(UUID excluir, String buscar, int offset, int limite) {
        if (offset < 0 || limite < 1 || limite > 100) throw new ReglaNegocioException("Paginación inválida.");
        return repo.padres(principal(), excluir, Objects.toString(texto(buscar, 160), ""), offset, limite);
    }

    @PreAuthorize(VER)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detalle detalle(UUID id) {
        var p = principal();
        var c = cuenta(p, id);
        return new Detalle(c, c.padre() == null ? null : cuenta(p, c.padre()), repo.nivel(p, id),
            repo.hijos(p, id, SIN_FILTRO), repo.usos(p, id));
    }

    @PreAuthorize(CREAR)
    @Transactional
    public UUID crear(Entrada entrada, UUID solicitud) {
        if (solicitud == null) throw new ReglaNegocioException("Solicitud de creación inválida.");
        var e = normalizar(entrada);
        var p = principal();
        repo.bloquearEmpresa(p);
        var existente = repo.solicitud(p, solicitud);
        if (existente.isPresent()) return existente.get().id();
        var padre = validarPadre(p, null, e.padre());
        var id = UUID.randomUUID();
        repo.crear(p, id, solicitud, e, siguienteCodigo(p, padre), padre.clasificacion());
        auditar(p, "CREAR", null, cuenta(p, id));
        return id;
    }

    @PreAuthorize(EDITAR)
    @Transactional
    public void editar(UUID id, long version, Entrada entrada) {
        var p = principal();
        var e = normalizar(entrada);
        repo.bloquearEmpresa(p);
        var c = cuenta(p, id);
        version(c, version);
        boolean cambiaPadre = !Objects.equals(c.padre(), e.padre());
        boolean cambiaEstructura = cambiaPadre || c.tipo() != e.tipo() || c.naturaleza() != e.naturaleza();
        if (cambiaEstructura) {
            if (c.estructural()) throw new ReglaNegocioException("La estructura de esta cuenta base está protegida.");
            if (repo.usos(p, id).stream().anyMatch(u -> cambiaPadre || u.historico() || u.activo()))
                throw new ReglaNegocioException("La cuenta tiene movimientos o dependencias que protegen su estructura.");
            if (c.hijos() && (cambiaPadre || c.tipo() != e.tipo()))
                throw new ReglaNegocioException("La cuenta tiene hijas. Su padre y tipo están protegidos.");
        }
        var codigo = c.codigo();
        var clasificacion = c.clasificacion();
        if (cambiaPadre) {
            var padre = validarPadre(p, id, e.padre());
            codigo = siguienteCodigo(p, padre);
            clasificacion = padre.clasificacion();
            repo.reservarCodigo(p, c.codigo());
        }
        repo.editar(p, id, e, codigo, clasificacion);
        auditar(p, "EDITAR", c, cuenta(p, id));
    }

    @PreAuthorize(VER + " and hasAuthority('catalogo_cuentas.desactivar')")
    @Transactional
    public void desactivar(UUID id, long version) { cambiarEstado(id, version, false); }

    @PreAuthorize(VER + " and hasAuthority('catalogo_cuentas.activar')")
    @Transactional
    public void activar(UUID id, long version) { cambiarEstado(id, version, true); }

    private void cambiarEstado(UUID id, long version, boolean activo) {
        var p = principal();
        repo.bloquearEmpresa(p);
        var c = cuenta(p, id);
        if (c.activo() == activo) return;
        version(c, version);
        if (!activo && c.estructural())
            throw new ReglaNegocioException("Las cuentas estructurales del catálogo base no se pueden desactivar.");
        if (activo && c.padre() != null && !cuenta(p, c.padre()).activo())
            throw new ReglaNegocioException("Activa primero la cuenta padre.");
        if (!activo && repo.hijosActivos(p, id))
            throw new ReglaNegocioException("La cuenta no puede desactivarse mientras tenga cuentas hijas activas.");
        if (!activo && repo.usos(p, id).stream().anyMatch(u -> !u.historico() && u.activo()))
            throw new ReglaNegocioException("La cuenta no puede desactivarse porque una configuración activa la utiliza.");
        repo.estado(p, id, activo);
        auditar(p, activo ? "ACTIVAR" : "DESACTIVAR", c, cuenta(p, id));
    }

    // Los servicios que contabilizan o configuran una integración llaman dentro de su transacción autorizada.
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarUso(UUID cuenta, String modulo, UUID referencia, String concepto, boolean historico,
                             Clasificacion clasificacion, Naturaleza naturaleza) {
        if (referencia == null) throw new ReglaNegocioException("Indica la referencia de la integración.");
        var p = principal();
        repo.bloquearEmpresa(p);
        var c = cuenta(p, cuenta);
        if (!c.activo() || !c.permiteMovimientos() || clasificacion != null && c.clasificacion() != clasificacion
                || naturaleza != null && c.naturaleza() != naturaleza)
            throw new ReglaNegocioException("Selecciona una cuenta de movimiento activa y compatible.");
        repo.registrarUso(p, c.id(), requerido(modulo, 80, "Indica el módulo."), referencia,
            requerido(concepto, 160, "Indica el concepto."), historico);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void desvincular(UUID cuenta, String modulo, UUID referencia) {
        var p = principal();
        repo.bloquearEmpresa(p);
        repo.desvincular(p, cuenta(p, cuenta).id(), modulo, referencia);
    }

    @PreAuthorize(VER + " and hasAuthority('catalogo_cuentas.imprimir')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Reporte reporte(Filtro entrada) {
        var p = principal();
        var f = filtro(entrada);
        var empresa = empresas.findByIdAndTenantId(p.empresaId(), p.tenantId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        var datos = datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        var comercial = datos == null ? null : datos.getNombreComercial();
        var emisor = new Empresa(comercial == null || comercial.isBlank() ? empresa.getNombre() : comercial,
            empresa.getIdentificacionFiscal(), datos == null ? null : datos.getDireccion(),
            datos == null ? null : datos.getTelefono(), empresa.getPaisCodigo(), empresa.getLogoObjectKey());
        return new Reporte(emisor, ZonedDateTime.now(ZoneId.of(empresa.getZonaHoraria())), f, repo.reporte(p, f));
    }

    private Cuenta validarPadre(TenantPrincipal p, UUID id, UUID padreId) {
        if (padreId == null) throw new ReglaNegocioException("Selecciona una cuenta padre.");
        var padre = cuenta(p, padreId);
        if (id != null && repo.esAncestro(p, id, padre.id()))
            throw new ReglaNegocioException("La cuenta padre no puede ser la propia cuenta ni una descendiente.");
        if (padre.tipo() != Tipo.AGRUPADORA || !padre.activo())
            throw new ReglaNegocioException("Selecciona una cuenta padre agrupadora activa.");
        return padre;
    }

    private String siguienteCodigo(TenantPrincipal p, Cuenta padre) {
        var prefijo = padre.codigo() + (padre.codigo().contains("-") ? "-" : ".");
        int ancho = repo.anchoSegmento(p, repo.nivel(p, padre.id()) + 1);
        return componerCodigo(prefijo, repo.siguienteSegmento(p, padre.id(), prefijo).toPlainString(), ancho);
    }

    static String componerCodigo(String prefijo, String segmento, int ancho) {
        var codigo = prefijo + "0".repeat(Math.max(0, ancho - segmento.length())) + segmento;
        if (codigo.length() > 40) throw new ReglaNegocioException("La rama alcanzó el límite de longitud de los códigos contables.");
        return codigo;
    }

    static Entrada normalizar(Entrada e) {
        if (e == null || e.tipo() == null || e.naturaleza() == null)
            throw new ReglaNegocioException("Completa el tipo y la naturaleza de la cuenta.");
        return new Entrada(e.padre(), requerido(e.nombre(), 160, "Indica el nombre de la cuenta."),
            e.tipo(), e.naturaleza(), texto(e.descripcion(), 2000));
    }

    private static Filtro filtro(Filtro f) {
        return f == null ? SIN_FILTRO : new Filtro(f.tipo(), f.naturaleza(), f.activo(), Objects.toString(texto(f.buscar(), 160), ""));
    }

    private static String requerido(String valor, int limite, String mensaje) {
        var limpio = texto(valor, limite);
        if (limpio == null) throw new ReglaNegocioException(mensaje);
        return limpio;
    }

    private static String texto(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        var limpio = valor.strip();
        if (limpio.length() > limite) throw new ReglaNegocioException("Un campo supera " + limite + " caracteres.");
        return limpio;
    }

    private static void version(Cuenta cuenta, long esperada) {
        if (cuenta.version() != esperada) throw new ReglaNegocioException("La cuenta cambió. Vuelve a abrirla.");
    }

    private Cuenta cuenta(TenantPrincipal p, UUID id) {
        return repo.cuenta(p, id).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta contable no encontrada."));
    }

    private void auditar(TenantPrincipal p, String accion, Cuenta anterior, Cuenta actual) {
        var trace = TraceContext.currentOrCreate();
        var cambios = new LinkedHashMap<String, Object>();
        cambios.put("traceId", trace);
        cambios.put("anterior", anterior);
        cambios.put("actual", actual);
        auditoria.registrar(accion, "CuentaContable", actual.id(), JSON.writeValueAsString(cambios));
        LoggerFactory.getLogger(CatalogoCuentasService.class).atInfo().addKeyValue("TraceId", trace)
            .addKeyValue("CompanyId", p.empresaId()).addKeyValue("AccountId", actual.id())
            .addKeyValue("Operacion", accion).log("cuenta_contable");
    }

    private static TenantPrincipal principal() { return TenantContext.principalActual(); }
}
