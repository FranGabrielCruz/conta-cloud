package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.NuevaEmpresaDto;
import com.citacloud.springboot.contacloud.app.dto.UsuarioInicialDto;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.models.Permiso;
import com.citacloud.springboot.contacloud.app.models.Rol;
import com.citacloud.springboot.contacloud.app.models.RolPermiso;
import com.citacloud.springboot.contacloud.app.models.Usuario;
import com.citacloud.springboot.contacloud.app.models.UsuarioEmpresa;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaAdministracionTenantLocalTest {
    @Mock EmpresaRepository empresas;
    @Mock DatosEmpresaRepository datos;
    @Mock UsuarioEmpresaRepository accesos;
    @Mock EmpresaModuloRepository empresaModulos;
    @Mock EmpresaModuloService moduloService;
    @Mock AuditoriaService auditoria;
    @Mock SucursalRepository sucursales;
    @Mock MonedaRepository monedas;
    @Mock RolRepository roles;
    @Mock PermisoRepository permisos;
    @Mock RolPermisoRepository rolPermisos;
    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder passwordEncoder;

    @AfterEach void limpiarContexto() { SecurityContextHolder.clearContext(); }

    @Test void creaTenantAisladoConUnicoUsuarioYSinPermisosDeEmpresas() {
        UUID actorTenant = UUID.randomUUID();
        UUID actorEmpresa = UUID.randomUUID();
        UUID actorUsuario = UUID.randomUUID();
        var principal = new TenantPrincipal(actorUsuario, actorTenant, actorEmpresa, null,
            "MATRIZ", "Administrador", "admin", "", true, true, Set.of(),
            Set.of("empresas.crear", "empresas.configurar_modulos"));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        UUID empresaId = UUID.randomUUID();
        Empresa empresaGuardada = mock(Empresa.class);
        when(empresaGuardada.getId()).thenReturn(empresaId);
        when(empresaGuardada.getNombre()).thenReturn("Cliente Nuevo");
        when(empresas.saveAndFlush(any(Empresa.class))).thenReturn(empresaGuardada);

        UUID rolId = UUID.randomUUID();
        Rol rolGuardado = mock(Rol.class);
        when(rolGuardado.getId()).thenReturn(rolId);
        when(roles.saveAndFlush(any(Rol.class))).thenReturn(rolGuardado);

        UUID permisoId = UUID.randomUUID();
        Permiso permitido = permiso("CORE", "usuarios");
        when(permitido.getId()).thenReturn(permisoId);
        Permiso empresasPermiso = permiso("CORE", "empresas");
        Permiso infraestructura = permiso("INFRAESTRUCTURA", "bases_datos");
        when(permisos.findAllByOrderByModuloAscRecursoAscCodigoAsc())
            .thenReturn(List.of(permitido, empresasPermiso, infraestructura));

        UUID usuarioId = UUID.randomUUID();
        Usuario usuarioGuardado = mock(Usuario.class);
        when(usuarioGuardado.getId()).thenReturn(usuarioId);
        when(usuarios.saveAndFlush(any(Usuario.class))).thenReturn(usuarioGuardado);
        when(passwordEncoder.encode("ClaveSegura1")).thenReturn("hash");

        var service = new EmpresaAdministracionService(empresas, datos, accesos, empresaModulos,
            moduloService, auditoria, sucursales, monedas, roles, permisos, rolPermisos, usuarios,
            passwordEncoder);
        var result = service.crearTenantLocal(
            new NuevaEmpresaDto("CLIENTE01", "Cliente Nuevo", "Cliente Nuevo SRL", "101850585",
                "8095550101", "cliente@example.com", "Santo Domingo", true, 5, Set.of()),
            new UsuarioInicialDto("cliente.admin", "Ana", "Pérez", "ana@example.com", "8095550102"),
            "ClaveSegura1", "ClaveSegura1");

        assertThat(result.tenantId()).isNotEqualTo(actorTenant);
        assertThat(result.empresaId()).isEqualTo(empresaId);
        assertThat(result.databaseNodeCode()).isEqualTo("conta_cloud");
        verify(usuarios, times(1)).saveAndFlush(any(Usuario.class));
        verify(accesos, times(1)).save(any(UsuarioEmpresa.class));

        ArgumentCaptor<Empresa> empresaCaptor = ArgumentCaptor.forClass(Empresa.class);
        verify(empresas).saveAndFlush(empresaCaptor.capture());
        assertThat(empresaCaptor.getValue().isLimiteUsuariosHabilitado()).isTrue();
        assertThat(empresaCaptor.getValue().getLimiteUsuarios()).isEqualTo(5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RolPermiso>> captor = ArgumentCaptor.forClass(List.class);
        verify(rolPermisos).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getId().getPermisoId()).isEqualTo(permisoId);
        verify(auditoria).registrarPara(empresaId, usuarioId, "TENANT_CREATED", "Tenant",
            result.tenantId(), "{}");
    }

    @Test void listadoDeAdministracionConsultaTodasLasEmpresasSinFiltrarTenant() {
        Empresa empresa = mock(Empresa.class);
        UUID empresaId = UUID.randomUUID();
        when(empresa.getId()).thenReturn(empresaId);
        when(empresa.getCodigo()).thenReturn("CLIENTE01");
        when(empresa.getNombre()).thenReturn("Cliente");
        when(empresa.isActivo()).thenReturn(true);
        when(empresas.buscarTodas(eq("cliente"), any())).thenReturn(new PageImpl<>(List.of(empresa)));
        when(empresaModulos.findAllByEmpresaIdInAndEnabledTrue(List.of(empresaId))).thenReturn(List.of());

        var result = service().buscar("cliente", null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(empresas).buscarTodas(eq("cliente"), any());
        verify(empresas, never()).buscarAutorizadas(any(), any(), any(), any());
    }

    @Test void empresaInactivaPuedeVolverAActivarse() {
        UUID empresaId = UUID.randomUUID();
        Empresa empresa = mock(Empresa.class);
        when(empresa.getId()).thenReturn(empresaId);
        when(empresa.isActivo()).thenReturn(false);
        when(empresas.findById(empresaId)).thenReturn(java.util.Optional.of(empresa));

        service().activar(empresaId);

        verify(empresa).setActivo(true);
        verify(empresas).save(empresa);
        verify(auditoria).registrar("COMPANY_ENABLED", "Empresa", empresaId, "{}");
    }

    @Test void resetDeContrasenaActualizaSoloElHashSinModificarUsuarioNiTenant() {
        UUID empresaId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Empresa empresa = mock(Empresa.class);
        when(empresa.getTenantId()).thenReturn(tenantId);
        when(empresas.findById(empresaId)).thenReturn(java.util.Optional.of(empresa));
        Usuario usuario = mock(Usuario.class);
        when(usuario.getTenantId()).thenReturn(tenantId);
        UsuarioEmpresa acceso = mock(UsuarioEmpresa.class);
        when(acceso.getUsuario()).thenReturn(usuario);
        when(accesos.findByUsuarioIdAndEmpresaId(usuarioId, empresaId))
            .thenReturn(java.util.Optional.of(acceso));
        when(passwordEncoder.encode("NuevaClave1")).thenReturn("nuevo-hash");
        when(usuarios.actualizarPasswordHash(usuarioId, tenantId, "nuevo-hash")).thenReturn(1);

        service().resetearContrasena(empresaId, usuarioId, "NuevaClave1", "NuevaClave1");

        verify(usuarios).actualizarPasswordHash(usuarioId, tenantId, "nuevo-hash");
        verify(usuarios, never()).save(any(Usuario.class));
        verify(usuario, never()).setUsuario(any());
        verify(usuario, never()).setNombre(any());
        verify(usuario, never()).setApellido(any());
        verify(usuario, never()).setCorreo(any());
        verify(usuario, never()).setTelefono(any());
        verify(auditoria).registrar("CONTRASENA_USUARIO_RESETEADA", "Usuario", usuarioId, "{}");
    }

    private EmpresaAdministracionService service() {
        return new EmpresaAdministracionService(empresas, datos, accesos, empresaModulos,
            moduloService, auditoria, sucursales, monedas, roles, permisos, rolPermisos, usuarios,
            passwordEncoder);
    }

    private static Permiso permiso(String modulo, String recurso) {
        Permiso permiso = mock(Permiso.class);
        when(permiso.getModulo()).thenReturn(modulo);
        when(permiso.getRecurso()).thenReturn(recurso);
        return permiso;
    }
}
