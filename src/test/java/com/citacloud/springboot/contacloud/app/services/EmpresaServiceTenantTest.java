package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.mappers.EmpresaMapper;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTenantTest {
    @Mock EmpresaRepository repository;
    @Mock AuditoriaService auditoria;
    private final UUID empresaA = UUID.randomUUID();
    private final UUID empresaB = UUID.randomUUID();

    @AfterEach void limpiarContexto() { SecurityContextHolder.clearContext(); }

    @Test void usuarioDeEmpresaANuncaConsultaEmpresaB() {
        var principal = new TenantPrincipal(UUID.randomUUID(), empresaA, "A", "Usuario A", "admin", "", true, Set.of("EMPRESA_VER"));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(repository.findById(empresaA)).thenReturn(Optional.of(new Empresa("A", "Empresa A")));

        new EmpresaService(repository, new EmpresaMapper(), auditoria).obtenerActual();

        verify(repository).findById(empresaA);
        verify(repository, never()).findById(empresaB);
    }
}
