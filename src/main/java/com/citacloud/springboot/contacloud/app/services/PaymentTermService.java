package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CondicionPagoDto;
import com.citacloud.springboot.contacloud.app.dto.CondicionPagoInput;
import com.citacloud.springboot.contacloud.app.mappers.CondicionPagoMapper;
import com.citacloud.springboot.contacloud.app.models.CondicionPago;
import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import com.citacloud.springboot.contacloud.app.repositories.CondicionPagoRepository;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentTermService {
    public static final int MAX_DAYS_TO_DUE = 3650;
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private final CondicionPagoRepository repository;
    private final CondicionPagoMapper mapper;
    private final AuditoriaService auditoria;

    public PaymentTermService(CondicionPagoRepository repository, CondicionPagoMapper mapper,
                              AuditoriaService auditoria) {
        this.repository = repository;
        this.mapper = mapper;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.ver','CONDICION_PAGO_VER')")
    public Page<CondicionPagoDto> buscar(String buscar, Boolean activo, int pagina, int tamano,
                                         String ordenarPor, boolean ascendente) {
        validarPaginacion(pagina, tamano);
        var principal = TenantContext.principalActual();
        return repository.buscar(principal.tenantId(), EmpresaContext.requerirEmpresaId(), limpiar(buscar), activo,
            PageRequest.of(pagina, tamano, orden(ordenarPor, ascendente))).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.ver','CONDICION_PAGO_VER')")
    public CondicionPagoDto obtener(UUID id) { return mapper.toDto(condicionSegura(id)); }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.ver','CONDICION_PAGO_VER')")
    public List<CondicionPagoDto> listarActivas() {
        var principal = TenantContext.principalActual();
        return repository.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(
            principal.tenantId(), EmpresaContext.requerirEmpresaId()).stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.crear','CONDICION_PAGO_CREAR')")
    public CondicionPagoDto crear(CondicionPagoInput input) {
        CondicionPagoInput validado = validar(input);
        var principal = TenantContext.principalActual();
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        validarNombreDuplicado(validado.nombre(), null, principal.tenantId(), empresaId);
        String codigo = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        CondicionPago entity = mapper.toEntity(validado, principal.tenantId(), empresaId, codigo,
            principal.usuarioId());
        try {
            entity = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaNegocioException("Ya existe una condición de pago con este nombre.", ex);
        }
        auditoria.registrar("PAYMENT_TERM_CREATED", "CondicionPago", entity.getId(), detalle(null, entity));
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.editar','CONDICION_PAGO_EDITAR')")
    public CondicionPagoDto actualizar(UUID id, CondicionPagoInput input) {
        CondicionPago entity = condicionSegura(id);
        CondicionPagoInput validado = validar(input);
        if (validado.activo() != entity.isActivo())
            throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado de la condición.");
        validarNombreDuplicado(validado.nombre(), id, entity.getTenantId(), entity.getEmpresaId());
        String anterior = detalle(entity, null);
        entity.setNombre(validado.nombre());
        entity.setTipo(validado.tipo());
        entity.setDias(validado.dias());
        entity.setDescripcion(validado.descripcion());
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaNegocioException("Ya existe una condición de pago con este nombre.", ex);
        }
        auditoria.registrar("PAYMENT_TERM_UPDATED", "CondicionPago", entity.getId(),
            combinarDetalle(anterior, entity));
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.desactivar','CONDICION_PAGO_DESACTIVAR')")
    public void desactivar(UUID id) { cambiarEstado(id, false, "PAYMENT_TERM_DISABLED"); }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.desactivar','CONDICION_PAGO_DESACTIVAR')")
    public void reactivar(UUID id) {
        CondicionPago entity = condicionSegura(id);
        validar(new CondicionPagoInput(entity.getNombre(), entity.getTipo(), entity.getDias(),
            entity.getDescripcion(), true));
        validarNombreDuplicado(entity.getNombre(), entity.getId(), entity.getTenantId(), entity.getEmpresaId());
        cambiarEstado(entity, true, "PAYMENT_TERM_ENABLED");
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CONDICIONES_PAGO') and hasAnyAuthority('condiciones_pago.ver','CONDICION_PAGO_VER')")
    public LocalDate calculateDueDate(LocalDate documentDate, UUID paymentTermId) {
        if (documentDate == null) throw new ReglaNegocioException("La fecha del documento es obligatoria.");
        CondicionPago condicion = condicionSegura(paymentTermId);
        if (!condicion.isActivo()) throw new ReglaNegocioException("La condición de pago seleccionada está inactiva.");
        return documentDate.plusDays(condicion.getDias());
    }

    public LocalDate calculateDueDate(LocalDate documentDate, CondicionPagoDto paymentTerm) {
        if (documentDate == null) throw new ReglaNegocioException("La fecha del documento es obligatoria.");
        if (paymentTerm == null) throw new ReglaNegocioException("La condición de pago es obligatoria.");
        validarReglaDias(paymentTerm.tipo(), paymentTerm.dias());
        return documentDate.plusDays(paymentTerm.dias());
    }

    private void cambiarEstado(UUID id, boolean activo, String accion) {
        cambiarEstado(condicionSegura(id), activo, accion);
    }

    private void cambiarEstado(CondicionPago entity, boolean activo, String accion) {
        entity.setActivo(activo);
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        repository.save(entity);
        auditoria.registrar(accion, "CondicionPago", entity.getId(), "{\"activo\":" + activo + "}");
    }

    private CondicionPagoInput validar(CondicionPagoInput input) {
        if (input == null) throw new ReglaNegocioException("Los datos de la condición de pago son obligatorios.");
        String nombre = limpiar(input.nombre());
        if (nombre.isEmpty()) throw new ReglaNegocioException("El nombre es obligatorio.");
        if (nombre.length() > 100) throw new ReglaNegocioException("El nombre excede 100 caracteres.");
        if (input.tipo() == null) throw new ReglaNegocioException("El tipo es obligatorio.");
        if (input.dias() == null) throw new ReglaNegocioException("Los días para vencimiento son obligatorios.");
        validarReglaDias(input.tipo(), input.dias());
        String descripcion = limpiarNulo(input.descripcion());
        if (descripcion != null && descripcion.length() > 500)
            throw new ReglaNegocioException("La descripción excede 500 caracteres.");
        return new CondicionPagoInput(nombre, input.tipo(), input.dias(), descripcion, input.activo());
    }

    private static void validarReglaDias(TipoCondicionPago tipo, int dias) {
        if (tipo == TipoCondicionPago.CASH && dias != 0)
            throw new ReglaNegocioException("Las condiciones de contado deben tener 0 días para vencimiento.");
        if (tipo == TipoCondicionPago.CREDIT && dias <= 0)
            throw new ReglaNegocioException("Los días para vencimiento deben ser mayores que cero.");
        if (dias < 0 || dias > MAX_DAYS_TO_DUE)
            throw new ReglaNegocioException("Los días para vencimiento deben estar entre 0 y 3650.");
    }

    private void validarNombreDuplicado(String nombre, UUID id, UUID tenantId, UUID empresaId) {
        boolean existe = id == null
            ? repository.existsByTenantIdAndEmpresaIdAndNombreIgnoreCase(tenantId, empresaId, nombre)
            : repository.existsByTenantIdAndEmpresaIdAndNombreIgnoreCaseAndIdNot(tenantId, empresaId, nombre, id);
        if (existe) throw new ReglaNegocioException("Ya existe una condición de pago con este nombre.");
    }

    private CondicionPago condicionSegura(UUID id) {
        if (id == null) throw new RecursoNoEncontradoException("Condición de pago no encontrada.");
        return repository.findByIdAndTenantIdAndEmpresaId(id, TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).orElseThrow(() ->
            new RecursoNoEncontradoException("Condición de pago no encontrada."));
    }

    private static Sort orden(String campo, boolean ascendente) {
        String propiedad = switch (campo == null ? "nombre" : campo) {
            case "tipo" -> "tipo";
            case "dias" -> "dias";
            case "estado" -> "activo";
            default -> "nombre";
        };
        return Sort.by(ascendente ? Sort.Direction.ASC : Sort.Direction.DESC, propiedad)
            .and(Sort.by("nombre"));
    }

    private static void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0 || !PAGE_SIZES.contains(tamano)) throw new ReglaNegocioException("Paginación inválida.");
    }

    private static String detalle(CondicionPago anterior, CondicionPago nueva) {
        CondicionPago valor = anterior != null ? anterior : nueva;
        return "{\"nombre\":\"" + escapar(valor.getNombre()) + "\",\"tipo\":\""
            + valor.getTipo() + "\",\"dias\":" + valor.getDias() + ",\"activo\":" + valor.isActivo() + "}";
    }

    private static String combinarDetalle(String anterior, CondicionPago nueva) {
        return "{\"anterior\":" + anterior + ",\"nuevo\":" + detalle(null, nueva) + "}";
    }

    private static String escapar(String valor) { return valor.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String limpiar(String valor) { return valor == null ? "" : valor.trim(); }
    private static String limpiarNulo(String valor) { String limpio = limpiar(valor); return limpio.isEmpty() ? null : limpio; }
}
