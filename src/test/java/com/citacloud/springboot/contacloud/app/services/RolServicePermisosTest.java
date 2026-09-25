package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.RolInputDto;
import com.citacloud.springboot.contacloud.app.mappers.RolMapper;
import com.citacloud.springboot.contacloud.app.models.Permiso;
import com.citacloud.springboot.contacloud.app.repositories.PermisoRepository;
import com.citacloud.springboot.contacloud.app.repositories.RolPermisoRepository;
import com.citacloud.springboot.contacloud.app.repositories.RolRepository;
import com.citacloud.springboot.contacloud.app.repositories.UsuarioEmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolServicePermisosTest {
    @Mock RolRepository roles;
    @Mock PermisoRepository permisos;
    @Mock UsuarioEmpresaRepository accesos;
    @Mock RolPermisoRepository rolPermisos;
    @Mock EntityManager entityManager;
    @Mock AuditoriaService auditoria;
    @Mock EmpresaModuloService modulos;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void catalogoSoloIncluyePermisosDeModulosHabilitados() {
        Permiso usuarios = permiso("usuarios.crear", "CORE", "usuarios");
        Permiso impuestos = permiso("IMPUESTO_CREAR", "CORE", null);
        Permiso empresas = permiso("empresas.ver", "CORE", "empresas");
        when(modulos.habilitadosActuales()).thenReturn(Set.of("USUARIOS"));
        when(permisos.findAllByOrderByModuloAscRecursoAscCodigoAsc())
            .thenReturn(List.of(usuarios, impuestos, empresas));

        var resultado = service().catalogoPermisos();

        assertThat(resultado).extracting(p -> p.codigo()).containsExactly("usuarios.crear");
    }

    @Test
    void backendRechazaPermisoDeModuloDeshabilitado() {
        UUID empresaId = UUID.randomUUID();
        UUID permisoId = UUID.randomUUID();
        var principal = new TenantPrincipal(UUID.randomUUID(), empresaId, "EMPRESA", "Admin",
            "admin", "", true, Set.of("roles.crear"));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        Permiso impuesto = permiso("IMPUESTO_CREAR", "CORE", null);
        when(roles.existsByEmpresaIdAndNombreIgnoreCase(empresaId, "Operador")).thenReturn(false);
        when(permisos.findAllByIdIn(Set.of(permisoId))).thenReturn(List.of(impuesto));
        when(modulos.habilitadosActuales()).thenReturn(Set.of("ROLES"));

        assertThatThrownBy(() -> service().crear(
            new RolInputDto("Operador", "", true, Set.of(permisoId))))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("módulos no habilitados");

        verify(roles, never()).saveAndFlush(any());
    }

    private RolService service() {
        return new RolService(roles, permisos, accesos, new RolMapper(), rolPermisos,
            entityManager, auditoria, modulos);
    }

    private static Permiso permiso(String codigo, String modulo, String recurso) {
        Permiso permiso = org.mockito.Mockito.mock(Permiso.class);
        org.mockito.Mockito.lenient().when(permiso.getCodigo()).thenReturn(codigo);
        org.mockito.Mockito.lenient().when(permiso.getModulo()).thenReturn(modulo);
        org.mockito.Mockito.lenient().when(permiso.getRecurso()).thenReturn(recurso);
        org.mockito.Mockito.lenient().when(permiso.getNombre()).thenReturn(codigo);
        return permiso;
    }
}
