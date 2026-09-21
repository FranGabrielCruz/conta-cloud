package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.MonedaDto;
import com.citacloud.springboot.contacloud.app.dto.NuevaMonedaDto;
import com.citacloud.springboot.contacloud.app.mappers.MonedaMapper;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import com.citacloud.springboot.contacloud.app.repositories.CatalogoMonedaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class CompanyCurrencyService {
    private final MonedaRepository repository;
    private final CatalogoMonedaRepository catalog;
    private final EmpresaRepository empresas;
    private final MonedaMapper mapper;
    private final AuditoriaService auditoria;
    public CompanyCurrencyService(MonedaRepository repository, CatalogoMonedaRepository catalog,
                                  EmpresaRepository empresas, MonedaMapper mapper, AuditoriaService auditoria) {
        this.repository=repository; this.catalog=catalog; this.empresas=empresas; this.mapper=mapper; this.auditoria=auditoria;
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('MONEDA_VER')")
    public Page<MonedaDto> search(String query, Boolean active, Pageable pageable) {
        if (!java.util.Set.of(10,25,50,100).contains(pageable.getPageSize()) || pageable.getPageNumber() < 0)
            throw new ReglaNegocioException("Paginación no válida.");
        UUID empresaId = TenantContext.requerirEmpresaId();
        String search = query == null ? "" : query.trim();
        var result = active == null
            ? repository.buscar(empresaId, search, pageable)
            : repository.buscarPorEstado(empresaId, search, active, pageable);
        return result.map(mapper::toDto);
    }
    @Transactional(readOnly = true) @PreAuthorize("hasAnyAuthority('MONEDA_VER','EMPRESA_VER')")
    public List<MonedaDto> activeCurrencies() {
        return repository.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(TenantContext.requerirEmpresaId())
            .stream().map(mapper::toDto).toList();
    }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('MONEDA_VER')")
    public MonedaDto get(UUID id) { return mapper.toDto(findCurrent(id)); }

    @Transactional @PreAuthorize("hasAuthority('MONEDA_CREAR')")
    public MonedaDto addFromCatalog(String code) {
        UUID empresaId = TenantContext.requerirEmpresaId();
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (repository.existsByEmpresaIdAndCodigoIsoIgnoreCase(empresaId, normalized))
            throw new ReglaNegocioException("La moneda ya está asociada a la empresa.");
        var item = catalog.findById(normalized).filter(c -> c.isActivo())
            .orElseThrow(() -> new RecursoNoEncontradoException("Moneda de catálogo no encontrada"));
        Moneda currency = new Moneda(empresaId, item.getCodigoIso(), item.getNombre(), item.getSimbolo(), item.getDecimales());
        currency = repository.save(currency);
        auditoria.registrar("COMPANY_CURRENCY_ADDED", "Moneda", currency.getId(), "{}");
        return mapper.toDto(currency);
    }

    @Transactional @PreAuthorize("hasAuthority('MONEDA_CREAR')")
    public MonedaDto create(NuevaMonedaDto dto) {
        validateNew(dto);
        UUID empresaId = TenantContext.requerirEmpresaId();
        String code = dto.codigo().trim().toUpperCase();
        if (repository.existsByEmpresaIdAndCodigoIsoIgnoreCase(empresaId, code))
            throw new ReglaNegocioException("Ya existe una moneda con ese código en la empresa.");
        Moneda currency = new Moneda(empresaId, code, dto.nombre().trim(), dto.simbolo().trim(), dto.decimales());
        currency = repository.save(currency);
        auditoria.registrar("COMPANY_CURRENCY_CREATED", "Moneda", currency.getId(), "{}");
        return mapper.toDto(currency);
    }

    @Transactional @PreAuthorize("hasAuthority('MONEDA_EDITAR')")
    public MonedaDto update(UUID id, MonedaDto dto) {
        var currency = findCurrent(id);
        if (!currency.getCodigoIso().equalsIgnoreCase(dto.codigoIso()))
            throw new ReglaNegocioException("El código de la moneda no puede modificarse.");
        if (dto.nombre() == null || dto.nombre().trim().isEmpty() || dto.simbolo() == null || dto.simbolo().trim().isEmpty())
            throw new ReglaNegocioException("El nombre y el símbolo son obligatorios.");
        if (dto.decimales() < 0 || dto.decimales() > 6)
            throw new ReglaNegocioException("Los decimales deben estar entre 0 y 6.");
        mapper.update(currency, dto); repository.save(currency);
        auditoria.registrar("COMPANY_CURRENCY_UPDATED", "Moneda", currency.getId(), "{}");
        return mapper.toDto(currency);
    }

    @Transactional @PreAuthorize("hasAuthority('MONEDA_DESACTIVAR')")
    public void disable(UUID id) {
        empresas.findWithLockById(TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));
        var currency = findCurrent(id);
        if (currency.isMonedaBase()) throw new ReglaNegocioException("No puedes desactivar la moneda base de la empresa.");
        currency.setActivo(false); repository.save(currency);
        auditoria.registrar("COMPANY_CURRENCY_DISABLED", "Moneda", currency.getId(), "{}");
    }

    @Transactional
    public Moneda ensureDop(UUID empresaId) {
        return repository.findByEmpresaIdAndCodigoIsoIgnoreCase(empresaId, "DOP").orElseGet(() -> {
            if (repository.countByEmpresaId(empresaId) > 0)
                throw new ReglaNegocioException("La empresa requiere configurar una moneda base.");
            var dop = catalog.findById("DOP").orElseThrow(() -> new IllegalStateException("DOP no existe en el catálogo"));
            Moneda created = new Moneda(empresaId, "DOP", dop.getNombre(), dop.getSimbolo(), dop.getDecimales());
            created.setMonedaBase(true); return repository.save(created);
        });
    }

    private Moneda findCurrent(UUID id) {
        return repository.findByIdAndEmpresaId(id, TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Moneda no encontrada"));
    }

    private void validateNew(NuevaMonedaDto dto) {
        if (dto == null || dto.codigo() == null || dto.codigo().trim().isEmpty()
            || dto.nombre() == null || dto.nombre().trim().isEmpty()
            || dto.simbolo() == null || dto.simbolo().trim().isEmpty())
            throw new ReglaNegocioException("El código, el nombre y el símbolo son obligatorios.");
        if (!dto.codigo().trim().toUpperCase().matches("[A-Z]{3}"))
            throw new ReglaNegocioException("El código debe contener tres letras, por ej: DOP, USD o EUR.");
        if (dto.nombre().trim().length() > 80 || dto.simbolo().trim().length() > 10)
            throw new ReglaNegocioException("El nombre o el símbolo supera la longitud permitida.");
        if (dto.decimales() < 0 || dto.decimales() > 6)
            throw new ReglaNegocioException("Los decimales deben estar entre 0 y 6.");
    }

}
