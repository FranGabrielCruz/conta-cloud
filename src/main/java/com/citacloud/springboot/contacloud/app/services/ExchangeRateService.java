package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.TasaCambioMapper;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import com.citacloud.springboot.contacloud.app.models.TasaCambio;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.repositories.TasaCambioRepository;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExchangeRateService {
    public static final int RATE_SCALE = 8;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TasaCambioRepository repository;
    private final MonedaRepository monedas;
    private final TasaCambioMapper mapper;
    private final AuditoriaService auditoria;

    public ExchangeRateService(TasaCambioRepository repository, MonedaRepository monedas,
                               TasaCambioMapper mapper, AuditoriaService auditoria) {
        this.repository = repository;
        this.monedas = monedas;
        this.mapper = mapper;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.ver','TASA_CAMBIO_VER')")
    public Page<TasaCambioDto> buscar(String buscar, UUID monedaId, LocalDate fecha, int pagina, int tamano) {
        return buscar(buscar, monedaId, fecha, pagina, tamano, "fecha", false);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.ver','TASA_CAMBIO_VER')")
    public Page<TasaCambioDto> buscar(String buscar, UUID monedaId, LocalDate fecha, int pagina, int tamano,
                                      String ordenarPor, boolean ascendente) {
        validarPaginacion(pagina, tamano);
        var principal = TenantContext.principalActual();
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        if (monedaId != null) monedaSegura(monedaId, empresaId, false);
        Sort sort = orden(ordenarPor, ascendente);
        return repository.buscar(principal.tenantId(), empresaId, limpiar(buscar), monedaId, fecha,
            PageRequest.of(pagina, tamano, sort)).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.ver','TASA_CAMBIO_VER')")
    public TasaCambioDto obtener(UUID id) { return mapper.toDto(tasaSegura(id)); }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.ver','TASA_CAMBIO_VER','tasas_cambio.crear','TASA_CAMBIO_CREAR')")
    public List<MonedaResumenDto> listarMonedas(boolean soloActivas) {
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        List<Moneda> lista = soloActivas
            ? monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresaId)
            : monedas.findAllByEmpresaIdOrderByCodigoIso(empresaId);
        return lista.stream().map(m -> new MonedaResumenDto(m.getId(), m.getCodigoIso(), m.getNombre(),
            m.isActivo(), m.isMonedaBase())).toList();
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.crear','TASA_CAMBIO_CREAR')")
    public TasaCambioDto crear(TasaCambioInput input) {
        var principal = TenantContext.principalActual();
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        ValidacionMonedas validacion = validar(input, null, empresaId, true);
        TasaCambio entity = mapper.toEntity(normalizar(input), principal.tenantId(), empresaId, principal.usuarioId());
        try {
            entity = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw duplicado(validacion.origen(), validacion.destino(), input.fecha(), ex);
        }
        auditoria.registrar("EXCHANGE_RATE_CREATED", "TasaCambio", entity.getId(),
            detalle(validacion.origen(), validacion.destino(), null, entity.getTasa(), entity.getFecha()));
        return mapper.toDto(entity, validacion.origen(), validacion.destino());
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.editar','TASA_CAMBIO_EDITAR')")
    public TasaCambioDto actualizar(UUID id, TasaCambioInput input) {
        TasaCambio entity = tasaSegura(id);
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        ValidacionMonedas validacion = validar(input, id, empresaId, true);
        BigDecimal anterior = entity.getTasa();
        if (input.activo() != entity.isActivo())
            throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado de la tasa.");
        entity.setMonedaOrigenId(input.monedaOrigenId());
        entity.setMonedaDestinoId(input.monedaDestinoId());
        entity.setTasa(normalizarTasa(input.tasa()));
        entity.setFecha(input.fecha());
        entity.setActivo(input.activo());
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw duplicado(validacion.origen(), validacion.destino(), input.fecha(), ex);
        }
        auditoria.registrar("EXCHANGE_RATE_UPDATED", "TasaCambio", entity.getId(),
            detalle(validacion.origen(), validacion.destino(), anterior, entity.getTasa(), entity.getFecha()));
        return mapper.toDto(entity, validacion.origen(), validacion.destino());
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.desactivar','TASA_CAMBIO_DESACTIVAR')")
    public void desactivar(UUID id) { cambiarEstado(id, false, "EXCHANGE_RATE_DISABLED"); }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.desactivar','TASA_CAMBIO_DESACTIVAR')")
    public void reactivar(UUID id) { cambiarEstado(id, true, "EXCHANGE_RATE_ENABLED"); }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.ver','TASA_CAMBIO_VER')")
    public TasaResueltaDto findExactRate(UUID monedaOrigenId, UUID monedaDestinoId, LocalDate fecha) {
        return resolverExacta(monedaOrigenId, monedaDestinoId, fecha, false);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('TASAS_CAMBIO') and hasAnyAuthority('tasas_cambio.ver','TASA_CAMBIO_VER')")
    public TasaResueltaDto resolverExacta(UUID monedaOrigenId, UUID monedaDestinoId, LocalDate fecha,
                                          boolean permitirInversa) {
        if (fecha == null) throw new ReglaNegocioException("La fecha es obligatoria.");
        UUID empresaId = EmpresaContext.requerirEmpresaId();
        Moneda origen = monedaSegura(monedaOrigenId, empresaId, false);
        Moneda destino = monedaSegura(monedaDestinoId, empresaId, false);
        if (origen.getId().equals(destino.getId()))
            throw new ReglaNegocioException("La moneda de origen y la moneda de destino deben ser diferentes.");
        UUID tenantId = TenantContext.requerirTenantId();
        var directa = repository.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId, empresaId, origen.getId(), destino.getId(), fecha);
        if (directa.isPresent()) {
            TasaCambio rate = directa.get();
            return new TasaResueltaDto(rate.getId(), rate.getTasa(), rate.getFecha(), false);
        }
        if (permitirInversa) {
            var inversa = repository.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
                tenantId, empresaId, destino.getId(), origen.getId(), fecha);
            if (inversa.isPresent()) {
                TasaCambio rate = inversa.get();
                return new TasaResueltaDto(rate.getId(), BigDecimal.ONE.divide(rate.getTasa(), RATE_SCALE, ROUNDING_MODE),
                    rate.getFecha(), true);
            }
        }
        throw new ReglaNegocioException("No existe una tasa de cambio disponible para la fecha seleccionada.");
    }

    public BigDecimal convertir(BigDecimal monto, BigDecimal tasa, int decimalesResultado) {
        if (monto == null || tasa == null) throw new ReglaNegocioException("El monto y la tasa son obligatorios.");
        if (tasa.signum() <= 0) throw new ReglaNegocioException("La tasa de cambio debe ser mayor que cero.");
        if (decimalesResultado < 0 || decimalesResultado > 8)
            throw new ReglaNegocioException("La cantidad de decimales no es válida.");
        return monto.multiply(tasa).setScale(decimalesResultado, ROUNDING_MODE);
    }

    private void cambiarEstado(UUID id, boolean activo, String accion) {
        TasaCambio entity = tasaSegura(id);
        if (activo) {
            monedaSegura(entity.getMonedaOrigenId(), entity.getEmpresaId(), true);
            monedaSegura(entity.getMonedaDestinoId(), entity.getEmpresaId(), true);
        }
        entity.setActivo(activo);
        entity.setActualizadoPor(TenantContext.principalActual().usuarioId());
        repository.save(entity);
        auditoria.registrar(accion, "TasaCambio", entity.getId(), "{\"activo\":" + activo + "}");
    }

    private ValidacionMonedas validar(TasaCambioInput input, UUID id, UUID empresaId, boolean exigirActivas) {
        if (input == null) throw new ReglaNegocioException("Los datos de la tasa de cambio son obligatorios.");
        if (input.monedaOrigenId() == null) throw new ReglaNegocioException("La moneda de origen es obligatoria.");
        if (input.monedaDestinoId() == null) throw new ReglaNegocioException("La moneda de destino es obligatoria.");
        if (input.monedaOrigenId().equals(input.monedaDestinoId()))
            throw new ReglaNegocioException("La moneda de origen y la moneda de destino deben ser diferentes.");
        Moneda origen = monedaSegura(input.monedaOrigenId(), empresaId, exigirActivas);
        Moneda destino = monedaSegura(input.monedaDestinoId(), empresaId, exigirActivas);
        normalizarTasa(input.tasa());
        if (input.fecha() == null) throw new ReglaNegocioException("La fecha es obligatoria.");
        UUID tenantId = TenantContext.requerirTenantId();
        boolean existe = id == null
            ? repository.existsByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFecha(
                tenantId, empresaId, origen.getId(), destino.getId(), input.fecha())
            : repository.existsByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndIdNot(
                tenantId, empresaId, origen.getId(), destino.getId(), input.fecha(), id);
        if (existe) throw duplicado(origen, destino, input.fecha(), null);
        return new ValidacionMonedas(origen, destino);
    }

    private Moneda monedaSegura(UUID id, UUID empresaId, boolean exigirActiva) {
        Moneda moneda = id == null ? null : monedas.findByIdAndEmpresaId(id, empresaId).orElse(null);
        if (moneda == null) throw new ReglaNegocioException("Seleccione una moneda válida de la empresa actual.");
        if (exigirActiva && !moneda.isActivo())
            throw new ReglaNegocioException("Las monedas seleccionadas deben estar activas.");
        return moneda;
    }

    private TasaCambio tasaSegura(UUID id) {
        return repository.findByIdAndTenantIdAndEmpresaId(id, TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).orElseThrow(() -> new RecursoNoEncontradoException(
                "Tasa de cambio no encontrada."));
    }

    private static TasaCambioInput normalizar(TasaCambioInput input) {
        return new TasaCambioInput(input.monedaOrigenId(), input.monedaDestinoId(),
            normalizarTasa(input.tasa()), input.fecha(), input.activo());
    }

    private static BigDecimal normalizarTasa(BigDecimal tasa) {
        if (tasa == null) throw new ReglaNegocioException("La tasa de cambio es obligatoria.");
        if (tasa.signum() <= 0) throw new ReglaNegocioException("La tasa de cambio debe ser mayor que cero.");
        if (tasa.scale() > RATE_SCALE || tasa.precision() - tasa.scale() > 11)
            throw new ReglaNegocioException("La tasa admite hasta 11 enteros y 8 decimales.");
        return tasa.setScale(RATE_SCALE);
    }

    private static ReglaNegocioException duplicado(Moneda origen, Moneda destino, LocalDate fecha, Throwable cause) {
        String mensaje = "Ya existe una tasa de cambio para " + origen.getCodigoIso() + " → "
            + destino.getCodigoIso() + " en la fecha " + DATE.format(fecha) + ".";
        return cause == null ? new ReglaNegocioException(mensaje) : new ReglaNegocioException(mensaje, cause);
    }

    private static String detalle(Moneda origen, Moneda destino, BigDecimal anterior,
                                  BigDecimal nueva, LocalDate fecha) {
        return "{\"monedaOrigen\":\"" + origen.getCodigoIso() + "\",\"monedaDestino\":\""
            + destino.getCodigoIso() + "\",\"fecha\":\"" + fecha + "\",\"tasaAnterior\":"
            + (anterior == null ? "null" : anterior.toPlainString()) + ",\"tasaNueva\":"
            + nueva.toPlainString() + "}";
    }

    private static String limpiar(String texto) { return texto == null ? "" : texto.trim(); }

    private static void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0 || !PAGE_SIZES.contains(tamano)) throw new ReglaNegocioException("Paginación inválida.");
    }

    private static Sort orden(String campo, boolean ascendente) {
        String propiedad = switch (campo == null ? "fecha" : campo) {
            case "origen" -> "monedaOrigen.codigoIso";
            case "destino" -> "monedaDestino.codigoIso";
            case "tasa" -> "tasa";
            default -> "fecha";
        };
        Sort.Direction direccion = ascendente ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort principal = Sort.by(direccion, propiedad);
        return "fecha".equals(propiedad)
            ? principal.and(Sort.by("monedaOrigen.codigoIso", "monedaDestino.codigoIso"))
            : principal.and(Sort.by(Sort.Direction.DESC, "fecha"));
    }

    private record ValidacionMonedas(Moneda origen, Moneda destino) {}
}
