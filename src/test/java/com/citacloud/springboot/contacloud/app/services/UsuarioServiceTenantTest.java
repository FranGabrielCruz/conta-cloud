package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.mappers.UsuarioMapper;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTenantTest {
    @Mock UsuarioRepository usuarios;@Mock UsuarioEmpresaRepository accesos;@Mock UsuarioSucursalRepository usuarioSucursales;
    @Mock RolRepository roles;@Mock SucursalRepository sucursales;@Mock EmpresaRepository empresas;@Mock PasswordEncoder encoder;@Mock AuditoriaService auditoria;
    UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID();
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    @Test void listadoNuncaConsultaUsuariosDeOtraEmpresa(){
        var principal=new TenantPrincipal(UUID.randomUUID(),empresaA,"A","Admin","admin","",true,Set.of("USUARIO_VER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        when(accesos.buscar(eq(empresaA),eq(""),any(Pageable.class))).thenReturn(Page.empty());
        when(usuarioSucursales.findAllByUsuarioEmpresaIdIn(any())).thenReturn(List.of());
        when(sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresaA)).thenReturn(List.of());
        var service=new UsuarioService(usuarios,accesos,usuarioSucursales,roles,sucursales,empresas,encoder,new UsuarioMapper(),auditoria);
        service.buscar("",null,0,10);
        verify(accesos).buscar(eq(empresaA),eq(""),any(Pageable.class));
        verify(accesos,never()).buscar(eq(empresaB),anyString(),any(Pageable.class));
    }
}
