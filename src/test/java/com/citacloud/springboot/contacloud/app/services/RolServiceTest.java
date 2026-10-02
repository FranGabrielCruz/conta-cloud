package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.RolInputDto;
import com.citacloud.springboot.contacloud.app.mappers.RolMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RolServiceTest {
    private final RolRepository roles=mock(RolRepository.class);
    private final PermisoRepository permisos=mock(PermisoRepository.class);
    private final UsuarioEmpresaRepository accesos=mock(UsuarioEmpresaRepository.class);
    private final RolPermisoRepository rolPermisos=mock(RolPermisoRepository.class);
    private final EntityManager entityManager=mock(EntityManager.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final EmpresaModuloService modulos=mock(EmpresaModuloService.class);
    private final RolService service=new RolService(roles,permisos,accesos,new RolMapper(),rolPermisos,
        entityManager,auditoria,modulos);
    private final UUID tenantId=UUID.randomUUID(),empresaId=UUID.randomUUID(),usuarioId=UUID.randomUUID();

    @BeforeEach void preparar(){
        var principal=new TenantPrincipal(usuarioId,tenantId,empresaId,null,"DEMO","Administrador","admin","",
            true,true,Set.of(),Set.of("roles.editar"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,null,principal.getAuthorities()));
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void actualizarFuerzaPersistenciaDePermisosAntesDeLimpiarElContexto(){
        UUID rolId=UUID.randomUUID(),permisoId=UUID.randomUUID();
        Rol rol=new Rol(empresaId,"OPERADOR","Operador");ReflectionTestUtils.setField(rol,"id",rolId);
        Permiso permiso=mock(Permiso.class);when(permiso.getId()).thenReturn(permisoId);
        when(permiso.getModulo()).thenReturn("ROLES");when(permiso.getRecurso()).thenReturn("roles");
        when(permiso.getCodigo()).thenReturn("roles.ver");
        when(roles.findByIdAndEmpresaId(rolId,empresaId)).thenReturn(Optional.of(rol));
        when(roles.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(empresaId,"Operador",rolId)).thenReturn(false);
        when(permisos.findAllByIdIn(Set.of(permisoId))).thenReturn(List.of(permiso));
        when(modulos.habilitadosActuales()).thenReturn(Set.of("ROLES"));
        when(accesos.countByEmpresaIdAndRolIdAndActivoTrue(empresaId,rolId)).thenReturn(0L);

        service.actualizar(rolId,new RolInputDto("Operador","Acceso operativo",true,Set.of(permisoId)));

        verify(rolPermisos).deleteAllByRolId(rolId);
        var captor=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rolPermisos).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        RolPermiso relacion=(RolPermiso)captor.getValue().getFirst();
        assertThat(relacion.getId().getRolId()).isEqualTo(rolId);
        assertThat(relacion.getId().getPermisoId()).isEqualTo(permisoId);
        var orden=inOrder(rolPermisos,entityManager);
        orden.verify(rolPermisos).saveAllAndFlush(anyList());
        orden.verify(entityManager).clear();
    }
}
