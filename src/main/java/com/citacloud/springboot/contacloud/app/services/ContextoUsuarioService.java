package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContextoUsuarioService {
    private final EmpresaRepository empresas;
    public ContextoUsuarioService(EmpresaRepository empresas) { this.empresas = empresas; }

    @Transactional(readOnly = true)
    public Contexto obtener() {
        var principal = TenantContext.principalActual();
        var empresa = empresas.findById(principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));
        String inicial = empresa.getNombre().isBlank() ? "C" : empresa.getNombre().substring(0, 1).toUpperCase();
        return new Contexto(principal.nombre(), empresa.getNombre(), empresa.getCodigo(), inicial);
    }
    public record Contexto(String usuario, String empresa, String empresaCodigo, String inicialEmpresa) {}
}
