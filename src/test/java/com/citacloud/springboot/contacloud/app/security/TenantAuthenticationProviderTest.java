package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAuthenticationProviderTest {
    @Mock EmpresaRepository empresas; @Mock UsuarioRepository usuarios;
    @Mock UsuarioEmpresaRepository accesos; @Mock UsuarioSucursalRepository sucursales;
    @Mock PasswordEncoder encoder;

    @Test void autenticaSiempreConEmpresaYUsuarioCombinados() {
        UUID empresaId=UUID.randomUUID(),tenantId=UUID.randomUUID();
        Empresa empresa=mock(Empresa.class);when(empresa.getId()).thenReturn(empresaId);when(empresa.getTenantId()).thenReturn(tenantId);when(empresa.getCodigo()).thenReturn("EMPRESA01");
        Usuario usuario=new Usuario(tenantId,empresaId,"admin","Administrador","","hash");
        Rol rol=new Rol(empresaId,"ADMINISTRADOR","Administrador");
        UsuarioEmpresa acceso=new UsuarioEmpresa(usuario,empresaId,rol);
        when(empresas.findByCodigoIgnoreCaseAndActivoTrue("EMPRESA01")).thenReturn(Optional.of(empresa));
        when(accesos.findForAuthentication(empresaId,"admin")).thenReturn(Optional.of(acceso));
        when(encoder.matches("secreto","hash")).thenReturn(true);
        var provider=new TenantAuthenticationProvider(empresas,usuarios,accesos,sucursales,encoder);

        Authentication resultado=provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("EMPRESA01|admin","secreto"));

        assertTrue(resultado.isAuthenticated());
        assertEquals(empresaId,((TenantPrincipal)resultado.getPrincipal()).empresaId());
        verify(accesos).findForAuthentication(empresaId,"admin");
    }

    @Test void rechazaUsuarioQueNoTieneAccesoEnLaEmpresaIndicada() {
        UUID empresaId=UUID.randomUUID(); Empresa empresa=mock(Empresa.class);when(empresa.getId()).thenReturn(empresaId);
        when(empresas.findByCodigoIgnoreCaseAndActivoTrue("EMPRESA-B")).thenReturn(Optional.of(empresa));
        when(accesos.findForAuthentication(empresaId,"admin")).thenReturn(Optional.empty());
        var provider=new TenantAuthenticationProvider(empresas,usuarios,accesos,sucursales,encoder);
        assertThrows(BadCredentialsException.class,()->provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("EMPRESA-B|admin","secreto")));
        verifyNoInteractions(encoder);
    }
}
