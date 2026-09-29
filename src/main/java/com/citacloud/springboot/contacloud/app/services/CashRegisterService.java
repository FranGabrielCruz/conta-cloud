package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.CajaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CashRegisterService {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final String DUPLICATE_MESSAGE =
        "Ya existe una caja con este nombre en la sucursal seleccionada.";

    private final CashRegisterRepository repository;
    private final SucursalRepository sucursales;
    private final MonedaRepository monedas;
    private final CajaMapper mapper;
    private final AuditoriaService auditoria;

    public CashRegisterService(CashRegisterRepository repository, SucursalRepository sucursales,
                               MonedaRepository monedas, CajaMapper mapper, AuditoriaService auditoria) {
        this.repository = repository;
        this.sucursales = sucursales;
        this.monedas = monedas;
        this.mapper = mapper;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.ver')")
    public Page<CajaDto> buscar(String buscar, UUID sucursalId, Boolean activo, int pagina, int tamano,
                                String ordenarPor, boolean ascendente) {
        validarPaginacion(pagina, tamano);
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        if (sucursalId != null) validarSucursal(sucursalId, empresaId);
        var principal = TenantContext.principalActual();
        return repository.buscar(principal.tenantId(), empresaId, limpiar(buscar), sucursalId, activo,
            PageRequest.of(pagina, tamano, orden(ordenarPor, ascendente))).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.ver')")
    public CajaDto obtener(UUID id) { return mapper.toDto(cajaSegura(id)); }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.ver')")
    public CajaCatalogosDto catalogos() {
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        List<CajaCatalogosDto.SucursalOpcion> branches = sucursales
            .findAllByEmpresaIdAndActivoTrueOrderByNombre(empresaId).stream()
            .filter(s -> EmpresaContext.permiteSucursal(s.getId()))
            .map(s -> new CajaCatalogosDto.SucursalOpcion(s.getId(), s.getNombre())).toList();
        List<CajaCatalogosDto.MonedaOpcion> currencies = monedas
            .findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresaId).stream()
            .map(m -> new CajaCatalogosDto.MonedaOpcion(m.getId(), m.getCodigoIso(), m.getNombre())).toList();
        UUID baseId = monedas.findByEmpresaIdAndMonedaBaseTrue(empresaId).filter(Moneda::isActivo)
            .map(Moneda::getId).orElse(null);
        return new CajaCatalogosDto(branches, currencies, baseId);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.crear')")
    public CajaDto crear(CajaInput input) {
        CajaInput validado = validar(input);
        var principal = TenantContext.principalActual();
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        Sucursal sucursal = validarSucursal(validado.sucursalId(), empresaId);
        Moneda moneda = validarMoneda(validado.monedaId(), empresaId);
        validarDuplicado(validado.nombre(), validado.sucursalId(), null, principal.tenantId(), empresaId);
        String codigo = "CAJ-" + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 12).toUpperCase(Locale.ROOT);
        Caja entity = mapper.toEntity(validado, principal.tenantId(), empresaId, codigo, principal.usuarioId());
        entity.asignarSucursal(sucursal);
        entity.asignarMoneda(moneda);
        guardar(entity);
        auditoria.registrar("CASH_REGISTER_CREATED", "Caja", entity.getId(), detalle(entity));
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.editar')")
    public CajaDto actualizar(UUID id, CajaInput input) {
        Caja entity = cajaSegura(id);
        CajaInput validado = validar(input);
        if (validado.activa() != entity.isActivo())
            throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado de la caja.");
        Sucursal sucursal = validarSucursal(validado.sucursalId(), entity.getEmpresaId());
        Moneda moneda = validarMoneda(validado.monedaId(), entity.getEmpresaId());
        validarDuplicado(validado.nombre(), validado.sucursalId(), entity.getId(), entity.getTenantId(),
            entity.getEmpresaId());
        String anterior = detalle(entity);
        entity.asignarSucursal(sucursal);
        entity.asignarMoneda(moneda);
        entity.setNombre(validado.nombre());
        entity.setDescripcion(validado.descripcion());
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        guardar(entity);
        auditoria.registrar("CASH_REGISTER_UPDATED", "Caja", entity.getId(),
            "{\"anterior\":" + anterior + ",\"nuevo\":" + detalle(entity) + "}");
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.desactivar')")
    public void desactivar(UUID id) { cambiarEstado(id, false, "CASH_REGISTER_DISABLED"); }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cajas.desactivar')")
    public void reactivar(UUID id) { cambiarEstado(id, true, "CASH_REGISTER_ENABLED"); }

    private void cambiarEstado(UUID id, boolean activo, String evento) {
        Caja entity = cajaSegura(id);
        if (entity.isActivo() == activo) return;
        if (activo) {
            validarSucursal(entity.getSucursalId(), entity.getEmpresaId());
            validarMoneda(entity.getMonedaId(), entity.getEmpresaId());
            validarDuplicado(entity.getNombre(), entity.getSucursalId(), entity.getId(), entity.getTenantId(),
                entity.getEmpresaId());
        }
        entity.setActivo(activo);
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        repository.save(entity);
        auditoria.registrar(evento, "Caja", entity.getId(), "{\"activo\":" + activo + "}");
    }

    private CajaInput validar(CajaInput input) {
        if (input == null) throw new ReglaNegocioException("Los datos de la caja son obligatorios.");
        String nombre = limpiar(input.nombre());
        if (nombre.isEmpty()) throw new ReglaNegocioException("El nombre es obligatorio.");
        if (nombre.length() > 120) throw new ReglaNegocioException("El nombre excede 120 caracteres.");
        if (input.sucursalId() == null) throw new ReglaNegocioException("La sucursal es obligatoria.");
        if (input.monedaId() == null) throw new ReglaNegocioException("La moneda es obligatoria.");
        String descripcion = limpiarNulo(input.descripcion());
        if (descripcion != null && descripcion.length() > 500)
            throw new ReglaNegocioException("La descripción excede 500 caracteres.");
        return new CajaInput(nombre, input.sucursalId(), input.monedaId(), descripcion, input.activa());
    }

    private Sucursal validarSucursal(UUID id, UUID empresaId) {
        Sucursal sucursal = sucursales.findByIdAndEmpresaId(id, empresaId)
            .filter(Sucursal::isActivo)
            .orElseThrow(() -> new ReglaNegocioException("La sucursal seleccionada no es válida."));
        if (!EmpresaContext.permiteSucursal(sucursal.getId()))
            throw new ReglaNegocioException("La sucursal seleccionada no está autorizada para el usuario.");
        return sucursal;
    }

    private Moneda validarMoneda(UUID id, UUID empresaId) {
        return monedas.findByIdAndEmpresaId(id, empresaId).filter(Moneda::isActivo)
            .orElseThrow(() -> new ReglaNegocioException("La moneda seleccionada no es válida."));
    }

    private void validarDuplicado(String nombre, UUID sucursalId, UUID id, UUID tenantId, UUID empresaId) {
        boolean existe = id == null
            ? repository.existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCase(
                tenantId, empresaId, sucursalId, nombre)
            : repository.existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCaseAndIdNot(
                tenantId, empresaId, sucursalId, nombre, id);
        if (existe) throw new ReglaNegocioException(DUPLICATE_MESSAGE);
    }

    private Caja cajaSegura(UUID id) {
        if (id == null) throw new RecursoNoEncontradoException("Caja no encontrada.");
        return repository.findByIdAndTenantIdAndEmpresaId(id, TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).orElseThrow(() -> new RecursoNoEncontradoException("Caja no encontrada."));
    }

    private void guardar(Caja entity) {
        try { repository.saveAndFlush(entity); }
        catch (DataIntegrityViolationException ex) { throw new ReglaNegocioException(DUPLICATE_MESSAGE, ex); }
    }

    private static Sort orden(String campo, boolean ascendente) {
        String propiedad = switch (campo == null ? "nombre" : campo) {
            case "sucursal" -> "sucursal.nombre";
            case "moneda" -> "moneda.codigoIso";
            case "estado" -> "activo";
            default -> "nombre";
        };
        return Sort.by(ascendente ? Sort.Direction.ASC : Sort.Direction.DESC, propiedad)
            .and(Sort.by("nombre"));
    }

    private static void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0 || !PAGE_SIZES.contains(tamano)) throw new ReglaNegocioException("Paginación inválida.");
    }

    private static String detalle(Caja c) {
        return "{\"nombre\":\"" + escapar(c.getNombre()) + "\",\"sucursalId\":\"" + c.getSucursalId()
            + "\",\"monedaId\":\"" + c.getMonedaId() + "\",\"activo\":" + c.isActivo() + "}";
    }

    private static String escapar(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String limpiar(String value) { return value == null ? "" : value.trim(); }
    private static String limpiarNulo(String value) { String clean = limpiar(value); return clean.isEmpty() ? null : clean; }
}
