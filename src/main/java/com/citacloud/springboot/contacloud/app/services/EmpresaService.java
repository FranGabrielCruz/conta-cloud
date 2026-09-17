package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.mappers.EmpresaMapper;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmpresaService {
    private final EmpresaRepository repository;
    private final EmpresaMapper mapper;
    private final AuditoriaService auditoria;
    public EmpresaService(EmpresaRepository repository, EmpresaMapper mapper, AuditoriaService auditoria) {
        this.repository = repository; this.mapper = mapper; this.auditoria = auditoria;
    }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('EMPRESA_VER')")
    public EmpresaDto obtenerActual() { return mapper.toDto(buscarActual()); }
    @Transactional @PreAuthorize("hasAuthority('EMPRESA_EDITAR')")
    public EmpresaDto actualizarActual(EmpresaDto dto) {
        Empresa empresa = buscarActual(); mapper.actualizar(empresa, dto);
        Empresa guardada = repository.save(empresa);
        auditoria.registrar("EDITAR", "Empresa", guardada.getId(), "{}");
        return mapper.toDto(guardada);
    }
    private Empresa buscarActual() {
        return repository.findById(TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));
    }
}
