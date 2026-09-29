package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.CuentaBancariaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class BankAccountService {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final String DUPLICATE_MESSAGE =
        "Esta cuenta bancaria ya está registrada para la empresa.";

    private final BankAccountRepository repository;
    private final MonedaRepository monedas;
    private final CuentaBancariaMapper mapper;
    private final BankAccountNumberService numbers;
    private final AuditoriaService auditoria;

    public BankAccountService(BankAccountRepository repository, MonedaRepository monedas,
                              CuentaBancariaMapper mapper, BankAccountNumberService numbers,
                              AuditoriaService auditoria) {
        this.repository = repository;
        this.monedas = monedas;
        this.mapper = mapper;
        this.numbers = numbers;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.ver')")
    public Page<CuentaBancariaDto> buscar(String buscar, UUID monedaId, Boolean activo,
                                          int pagina, int tamano, String ordenarPor,
                                          boolean ascendente) {
        validarPaginacion(pagina, tamano);
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        if (monedaId != null) validarMoneda(monedaId, empresaId);
        var principal = TenantContext.principalActual();
        Pageable pageable = PageRequest.of(pagina, tamano, orden(ordenarPor, ascendente));
        return repository.buscar(principal.tenantId(), empresaId, limpiar(buscar), monedaId,
            activo, pageable).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.ver')")
    public CuentaBancariaDto obtener(UUID id) { return mapper.toDto(cuentaSegura(id)); }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.editar')")
    public CuentaBancariaEdicionDto obtenerParaEditar(UUID id) {
        return mapper.toEditDto(cuentaSegura(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.ver')")
    public CuentaBancariaCatalogosDto catalogos() {
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        var opciones = monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresaId).stream()
            .map(m -> new CuentaBancariaCatalogosDto.MonedaOpcion(
                m.getId(), m.getCodigoIso(), m.getNombre())).toList();
        UUID baseId = monedas.findByEmpresaIdAndMonedaBaseTrue(empresaId).filter(Moneda::isActivo)
            .map(Moneda::getId).orElse(null);
        return new CuentaBancariaCatalogosDto(opciones, baseId);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.crear')")
    public CuentaBancariaDto crear(CuentaBancariaInput input) {
        CuentaBancariaInput validado = validar(input);
        var principal = TenantContext.principalActual();
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        Moneda moneda = validarMoneda(validado.monedaId(), empresaId);
        DatosNumero datos = proteger(validado.numeroCuenta());
        validarDuplicado(datos.fingerprint(), null, principal.tenantId(), empresaId);
        String codigo = "BAN-" + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 12).toUpperCase(Locale.ROOT);
        CuentaBancaria entity = mapper.toEntity(validado, principal.tenantId(), empresaId,
            codigo, datos.cifrado(), datos.last4(), datos.fingerprint(), principal.usuarioId());
        entity.asignarMoneda(moneda);
        guardar(entity);
        auditoria.registrar("BANK_ACCOUNT_CREATED", "CuentaBancaria", entity.getId(), detalle(entity));
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.editar')")
    public CuentaBancariaDto actualizar(UUID id, CuentaBancariaInput input) {
        CuentaBancaria entity = cuentaSegura(id);
        CuentaBancariaInput validado = validar(input);
        if (validado.activa() != entity.isActivo())
            throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado de la cuenta bancaria.");
        Moneda moneda = validarMoneda(validado.monedaId(), entity.getEmpresaId());
        DatosNumero datos = proteger(validado.numeroCuenta());
        validarDuplicado(datos.fingerprint(), entity.getId(), entity.getTenantId(), entity.getEmpresaId());
        String anterior = detalle(entity);
        entity.asignarMoneda(moneda);
        entity.setBancoNombre(validado.banco());
        entity.setNombreCuenta(validado.nombre());
        entity.setTipoCuenta(validado.tipo());
        entity.setNumeroCuenta(datos.cifrado(), datos.last4(), datos.fingerprint());
        entity.setDescripcion(validado.descripcion());
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        guardar(entity);
        auditoria.registrar("BANK_ACCOUNT_UPDATED", "CuentaBancaria", entity.getId(),
            "{\"anterior\":" + anterior + ",\"nuevo\":" + detalle(entity)
                + ",\"numeroCuentaActualizado\":true}");
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.desactivar')")
    public void desactivar(UUID id) { cambiarEstado(id, false, "BANK_ACCOUNT_DISABLED"); }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('cuentas_bancarias.desactivar')")
    public void reactivar(UUID id) { cambiarEstado(id, true, "BANK_ACCOUNT_ENABLED"); }

    private void cambiarEstado(UUID id, boolean activo, String evento) {
        CuentaBancaria entity = cuentaSegura(id);
        if (entity.isActivo() == activo) return;
        if (activo) {
            validarMoneda(entity.getMonedaId(), entity.getEmpresaId());
            validarDuplicado(entity.getNumeroCuentaFingerprint(), entity.getId(),
                entity.getTenantId(), entity.getEmpresaId());
        }
        entity.setActivo(activo);
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        repository.save(entity);
        auditoria.registrar(evento, "CuentaBancaria", entity.getId(), "{\"activo\":" + activo + "}");
    }

    private CuentaBancariaInput validar(CuentaBancariaInput input) {
        if (input == null) throw new ReglaNegocioException("Los datos de la cuenta bancaria son obligatorios.");
        String banco = requerido(input.banco(), "El banco es obligatorio.", 120);
        String nombre = requerido(input.nombre(), "El nombre de cuenta es obligatorio.", 120);
        if (input.tipo() == null) throw new ReglaNegocioException("El tipo de cuenta es obligatorio.");
        if (input.monedaId() == null) throw new ReglaNegocioException("La moneda es obligatoria.");
        String descripcion = limpiarNulo(input.descripcion());
        if (descripcion != null && descripcion.length() > 500)
            throw new ReglaNegocioException("La descripción excede 500 caracteres.");
        return new CuentaBancariaInput(banco, nombre, input.tipo(), input.monedaId(),
            input.numeroCuenta(), descripcion, input.activa());
    }

    private Moneda validarMoneda(UUID id, UUID empresaId) {
        return monedas.findByIdAndEmpresaId(id, empresaId).filter(Moneda::isActivo)
            .orElseThrow(() -> new ReglaNegocioException("La moneda seleccionada no es válida."));
    }

    private DatosNumero proteger(String raw) {
        String normalized = numbers.normalize(raw);
        return new DatosNumero(numbers.encrypt(normalized), numbers.last4(normalized),
            numbers.fingerprint(normalized));
    }

    private void validarDuplicado(String fingerprint, UUID id, UUID tenantId, UUID empresaId) {
        boolean existe = id == null
            ? repository.existsByTenantIdAndEmpresaIdAndNumeroCuentaFingerprint(
                tenantId, empresaId, fingerprint)
            : repository.existsByTenantIdAndEmpresaIdAndNumeroCuentaFingerprintAndIdNot(
                tenantId, empresaId, fingerprint, id);
        if (existe) throw new ReglaNegocioException(DUPLICATE_MESSAGE);
    }

    private CuentaBancaria cuentaSegura(UUID id) {
        if (id == null) throw new RecursoNoEncontradoException("Cuenta bancaria no encontrada.");
        return repository.findByIdAndTenantIdAndEmpresaId(id, TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).orElseThrow(
                () -> new RecursoNoEncontradoException("Cuenta bancaria no encontrada."));
    }

    private void guardar(CuentaBancaria entity) {
        try { repository.saveAndFlush(entity); }
        catch (DataIntegrityViolationException ex) {
            throw new ReglaNegocioException(DUPLICATE_MESSAGE, ex);
        }
    }

    private static Sort orden(String campo, boolean ascendente) {
        String propiedad = switch (campo == null ? "banco" : campo) {
            case "nombre" -> "nombreCuenta";
            case "tipo" -> "tipoCuenta";
            case "moneda" -> "moneda.codigoIso";
            case "estado" -> "activo";
            default -> "bancoNombre";
        };
        Sort.Direction direction = ascendente ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, propiedad).and(Sort.by(direction, "nombreCuenta"));
    }

    private static void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0 || !PAGE_SIZES.contains(tamano))
            throw new ReglaNegocioException("Paginación inválida.");
    }

    private static String requerido(String value, String message, int max) {
        String clean = limpiar(value);
        if (clean.isEmpty()) throw new ReglaNegocioException(message);
        if (clean.length() > max) throw new ReglaNegocioException("El valor excede " + max + " caracteres.");
        return clean;
    }

    private static String detalle(CuentaBancaria c) {
        return "{\"banco\":\"" + escapar(c.getBancoNombre()) + "\",\"nombre\":\""
            + escapar(c.getNombreCuenta()) + "\",\"tipo\":\"" + c.getTipoCuenta()
            + "\",\"monedaId\":\"" + c.getMonedaId() + "\",\"activo\":" + c.isActivo() + "}";
    }

    private static String escapar(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String limpiar(String value) { return value == null ? "" : value.trim(); }
    private static String limpiarNulo(String value) { String clean = limpiar(value); return clean.isEmpty() ? null : clean; }

    private record DatosNumero(String cifrado, String last4, String fingerprint) {}
}
