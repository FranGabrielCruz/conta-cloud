package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.PeriodoFiscalDto;
import com.citacloud.springboot.contacloud.app.dto.PeriodoFiscalInput;
import com.citacloud.springboot.contacloud.app.mappers.PeriodoFiscalMapper;
import com.citacloud.springboot.contacloud.app.models.PeriodoFiscal;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.PeriodoFiscalRepository;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountingPeriodService {
    private static final Set<Integer> TAMANOS_VALIDOS = Set.of(10, 25, 50, 100);
    private final PeriodoFiscalRepository repository;
    private final EmpresaRepository empresas;
    private final PeriodoFiscalMapper mapper;
    private final AuditoriaService auditoria;

    public AccountingPeriodService(PeriodoFiscalRepository repository, EmpresaRepository empresas,
                                   PeriodoFiscalMapper mapper, AuditoriaService auditoria) {
        this.repository = repository;
        this.empresas = empresas;
        this.mapper = mapper;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('periodos_fiscales.ver','PERIODO_VER')")
    public Page<PeriodoFiscalDto> buscar(String buscar, Integer anio, String estado, int pagina, int tamano) {
        if (pagina < 0 || !TAMANOS_VALIDOS.contains(tamano)) throw new IllegalArgumentException("Paginación inválida.");
        var principal = TenantContext.principalActual();
        return repository.buscar(principal.tenantId(), EmpresaContext.requerirEmpresaId(), limpiar(buscar),
            anio, estado(estado), PageRequest.of(pagina, tamano)).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('periodos_fiscales.ver','PERIODO_VER')")
    public List<Integer> listarAnios() {
        var principal = TenantContext.principalActual();
        return repository.listarAnios(principal.tenantId(), EmpresaContext.requerirEmpresaId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('periodos_fiscales.ver','PERIODO_VER')")
    public PeriodoFiscalDto obtener(UUID id) { return mapper.toDto(seguro(id)); }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('periodos_fiscales.crear','PERIODO_CREAR')")
    public PeriodoFiscalDto crear(PeriodoFiscalInput input) {
        validar(input);
        bloquearEmpresa();
        validarSolapamiento(null, input.fechaInicial(), input.fechaFinal());
        var principal = TenantContext.principalActual();
        var entity = mapper.toEntity(input, principal.tenantId(), principal.empresaId());
        entity = repository.save(entity);
        auditoria.registrar("FISCAL_PERIOD_CREATED", "PeriodoFiscal", entity.getId(), "{}");
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('periodos_fiscales.editar','PERIODO_EDITAR')")
    public PeriodoFiscalDto actualizar(UUID id, PeriodoFiscalInput input) {
        validar(input);
        bloquearEmpresa();
        var entity = seguro(id);
        if (entity.getEstado() != PeriodoFiscal.Estado.ABIERTO) {
            throw new ReglaNegocioException("Solo se pueden editar períodos abiertos.");
        }
        validarSolapamiento(id, input.fechaInicial(), input.fechaFinal());
        mapper.updateEntity(input, entity);
        auditoria.registrar("FISCAL_PERIOD_UPDATED", "PeriodoFiscal", id, "{}");
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('periodos_fiscales.cerrar','PERIODO_CERRAR')")
    public PeriodoFiscalDto cerrar(UUID id) {
        bloquearEmpresa();
        var entity = seguro(id);
        if (entity.getEstado() != PeriodoFiscal.Estado.ABIERTO) {
            throw new ReglaNegocioException("El período fiscal no está abierto.");
        }
        entity.cerrar(TenantContext.principalActual().usuarioId());
        auditoria.registrar("FISCAL_PERIOD_CLOSED", "PeriodoFiscal", id,
            "{\"estadoAnterior\":\"ABIERTO\",\"estadoNuevo\":\"CERRADO\"}");
        return mapper.toDto(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAuthority('periodos_fiscales.reabrir')")
    public PeriodoFiscalDto reabrir(UUID id) {
        bloquearEmpresa();
        var entity = seguro(id);
        if (entity.getEstado() != PeriodoFiscal.Estado.CERRADO) {
            throw new ReglaNegocioException("Solo se pueden reabrir períodos cerrados.");
        }
        entity.reabrir(TenantContext.principalActual().usuarioId());
        auditoria.registrar("FISCAL_PERIOD_REOPENED", "PeriodoFiscal", id,
            "{\"estadoAnterior\":\"CERRADO\",\"estadoNuevo\":\"ABIERTO\"}");
        return mapper.toDto(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE')")
    public PeriodoFiscalDto resolverPorFecha(LocalDate fecha) {
        if (fecha == null) throw new ReglaNegocioException("La fecha de operación es obligatoria.");
        var principal = TenantContext.principalActual();
        return repository.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(
                principal.tenantId(), principal.empresaId(), fecha, fecha)
            .map(mapper::toDto)
            .orElseThrow(() -> new ReglaNegocioException("No existe un período fiscal para la fecha indicada."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE')")
    public PeriodoFiscalDto validarPeriodoAbierto(LocalDate fecha) {
        var periodo = resolverPorFecha(fecha);
        if (!PeriodoFiscal.Estado.ABIERTO.name().equals(periodo.estado())) {
            throw new ReglaNegocioException(
                "No se puede contabilizar la operación porque el período correspondiente está cerrado.");
        }
        return periodo;
    }

    private PeriodoFiscal seguro(UUID id) {
        var principal = TenantContext.principalActual();
        return repository.findByIdAndTenantIdAndEmpresaId(id, principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Período fiscal no encontrado."));
    }

    private void bloquearEmpresa() {
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        empresas.findWithLockById(empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private void validarSolapamiento(UUID excluirId, LocalDate inicio, LocalDate fin) {
        var principal = TenantContext.principalActual();
        if (repository.existeSolapamiento(principal.tenantId(), principal.empresaId(), excluirId, inicio, fin)) {
            throw new ReglaNegocioException("Las fechas se solapan con otro período fiscal existente.");
        }
    }

    static void validar(PeriodoFiscalInput input) {
        if (input == null || limpiar(input.nombre()).isEmpty()) {
            throw new ReglaNegocioException("El nombre del período es obligatorio.");
        }
        if (limpiar(input.nombre()).length() > 120) {
            throw new ReglaNegocioException("El nombre del período excede 120 caracteres.");
        }
        if (input.fechaInicial() == null) throw new ReglaNegocioException("La fecha inicial es obligatoria.");
        if (input.fechaFinal() == null) throw new ReglaNegocioException("La fecha final es obligatoria.");
        if (input.fechaInicial().isAfter(input.fechaFinal())) {
            throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private static PeriodoFiscal.Estado estado(String value) {
        if (value == null || value.isBlank() || "TODOS".equalsIgnoreCase(value)) return null;
        try { return PeriodoFiscal.Estado.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new ReglaNegocioException("El estado seleccionado no es válido."); }
    }

    private static String limpiar(String value) { return value == null ? "" : value.trim(); }
}
