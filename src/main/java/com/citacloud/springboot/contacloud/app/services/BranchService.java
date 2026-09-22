package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.SucursalDto;
import com.citacloud.springboot.contacloud.app.mappers.SucursalMapper;
import com.citacloud.springboot.contacloud.app.repositories.SucursalRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class BranchService {
    private final SucursalRepository repository;
    private final SucursalMapper mapper;
    private final AuditoriaService auditoria;
    public BranchService(SucursalRepository repository, SucursalMapper mapper, AuditoriaService auditoria) {
        this.repository = repository; this.mapper = mapper; this.auditoria = auditoria;
    }

    @Transactional(readOnly = true) @PreAuthorize("@moduleAuthorization.enabled('SUCURSALES') and hasAuthority('SUCURSAL_VER')")
    public Page<SucursalDto> search(String query, Boolean active, Pageable pageable) {
        validatePage(pageable);
        UUID empresaId = TenantContext.requerirEmpresaId();
        String search = normalizeSearch(query);
        var result = active == null
            ? repository.buscar(empresaId, search, pageable)
            : repository.buscarPorEstado(empresaId, search, active, pageable);
        return result.map(mapper::toDto);
    }

    @Transactional(readOnly = true) @PreAuthorize("@moduleAuthorization.enabled('SUCURSALES') and hasAuthority('SUCURSAL_VER')")
    public SucursalDto get(UUID id) { return mapper.toDto(findCurrent(id)); }

    @Transactional @PreAuthorize("@moduleAuthorization.enabled('SUCURSALES') and hasAuthority('SUCURSAL_CREAR')")
    public SucursalDto create(SucursalDto dto) {
        validate(dto);
        UUID empresaId = TenantContext.requerirEmpresaId();
        var branch = mapper.toEntity(empresaId, generateCode(), normalized(dto));
        if (!repository.existsByEmpresaIdAndPrincipalTrue(empresaId)) branch.setPrincipal(true);
        branch = repository.save(branch);
        auditoria.registrar("BRANCH_CREATED", "Sucursal", branch.getId(), "{}");
        return mapper.toDto(branch);
    }

    @Transactional @PreAuthorize("@moduleAuthorization.enabled('SUCURSALES') and hasAuthority('SUCURSAL_EDITAR')")
    public SucursalDto update(UUID id, SucursalDto dto) {
        validate(dto);
        var branch = findCurrent(id); mapper.update(branch, normalized(dto)); repository.save(branch);
        auditoria.registrar("BRANCH_UPDATED", "Sucursal", branch.getId(), "{}");
        return mapper.toDto(branch);
    }

    @Transactional @PreAuthorize("@moduleAuthorization.enabled('SUCURSALES') and hasAuthority('SUCURSAL_DESACTIVAR')")
    public void disable(UUID id) {
        var branch = findCurrent(id);
        if (branch.isPrincipal()) throw new ReglaNegocioException("No puedes desactivar la sucursal principal.");
        branch.setActivo(false); repository.save(branch);
        auditoria.registrar("BRANCH_DISABLED", "Sucursal", branch.getId(), "{}");
    }

    private com.citacloud.springboot.contacloud.app.models.Sucursal findCurrent(UUID id) {
        return repository.findByIdAndEmpresaId(id, TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Sucursal no encontrada"));
    }
    private void validate(SucursalDto dto) {
        if (dto == null || dto.nombre() == null || dto.nombre().trim().isEmpty())
            throw new ReglaNegocioException("El nombre de la sucursal es obligatorio.");
        if (dto.nombre().trim().length() > 150) throw new ReglaNegocioException("El nombre de la sucursal es demasiado largo.");
    }
    private SucursalDto normalized(SucursalDto dto) {
        return new SucursalDto(dto.id(), dto.nombre(), dto.direccion(),
            CompanyConfigurationService.normalizePhone(dto.telefono()), dto.principal(), dto.activo());
    }
    private String generateCode() {
        return "SUC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }
    private String normalizeSearch(String value) { return value == null ? "" : value.trim(); }
    private void validatePage(Pageable pageable) {
        if (!java.util.Set.of(10,25,50,100).contains(pageable.getPageSize()) || pageable.getPageNumber() < 0)
            throw new ReglaNegocioException("Paginación no válida.");
    }
}
