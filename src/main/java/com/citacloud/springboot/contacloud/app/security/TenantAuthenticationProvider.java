package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.UsuarioRepository;
import com.citacloud.springboot.contacloud.app.repositories.UsuarioEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.UsuarioSucursalRepository;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;

@Component
public class TenantAuthenticationProvider implements AuthenticationProvider {
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final UsuarioEmpresaRepository accesos;
    private final UsuarioSucursalRepository sucursales;
    private final PasswordEncoder passwordEncoder;
    public TenantAuthenticationProvider(EmpresaRepository empresas, UsuarioRepository usuarios,
                                        UsuarioEmpresaRepository accesos, UsuarioSucursalRepository sucursales,
                                        PasswordEncoder passwordEncoder) {
        this.empresas = empresas; this.usuarios = usuarios; this.accesos=accesos; this.sucursales=sucursales;
        this.passwordEncoder = passwordEncoder;
    }
    @Override @Transactional
    public Authentication authenticate(Authentication authentication) {
        String[] credencial = authentication.getName().trim().split("\\|", 2);
        if (credencial.length != 2 || credencial[0].isBlank() || credencial[1].isBlank()) {
            throw new BadCredentialsException("Credenciales invalidas");
        }
        Empresa empresa = empresas.findByCodigoIgnoreCaseAndActivoTrue(credencial[0])
            .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));
        var acceso = accesos.findByEmpresaIdAndUsuarioUsuarioIgnoreCaseAndActivoTrueAndUsuarioActivoTrue(empresa.getId(), credencial[1])
            .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));
        var usuario = acceso.getUsuario();
        if (!usuario.getTenantId().equals(empresa.getTenantId()) || !acceso.getRol().isActivo())
            throw new BadCredentialsException("Credenciales invalidas");
        if (!passwordEncoder.matches(authentication.getCredentials().toString(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }
        var permisos = new LinkedHashSet<String>();
        var rol=acceso.getRol(); permisos.add("ROLE_" + rol.getCodigo());
        rol.getPermisos().forEach(p -> permisos.add(p.getCodigo()));
        var sucursalIds = acceso.isAccesoTodasSucursales() ? java.util.Set.<java.util.UUID>of()
            : sucursales.findAllByUsuarioEmpresaId(acceso.getId()).stream().map(s -> s.getSucursalId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        usuario.registrarAcceso();
        var principal = new TenantPrincipal(usuario.getId(), empresa.getTenantId(), empresa.getId(), acceso.getId(),
            empresa.getCodigo(), usuario.getNombre(), usuario.getUsuario(), usuario.getPasswordHash(), true,
            acceso.isAccesoTodasSucursales(), sucursalIds, permisos);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    @Override public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
