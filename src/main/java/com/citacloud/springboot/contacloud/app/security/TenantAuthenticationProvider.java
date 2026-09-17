package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.models.Usuario;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.UsuarioRepository;
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
    private final PasswordEncoder passwordEncoder;
    public TenantAuthenticationProvider(EmpresaRepository empresas, UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.empresas = empresas; this.usuarios = usuarios; this.passwordEncoder = passwordEncoder;
    }
    @Override @Transactional
    public Authentication authenticate(Authentication authentication) {
        String[] credencial = authentication.getName().trim().split("\\|", 2);
        if (credencial.length != 2 || credencial[0].isBlank() || credencial[1].isBlank()) {
            throw new BadCredentialsException("Credenciales invalidas");
        }
        Empresa empresa = empresas.findByCodigoIgnoreCaseAndActivoTrue(credencial[0])
            .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));
        Usuario usuario = usuarios.findByEmpresaIdAndUsuarioIgnoreCaseAndActivoTrue(empresa.getId(), credencial[1])
            .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));
        if (!passwordEncoder.matches(authentication.getCredentials().toString(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }
        var permisos = new LinkedHashSet<String>();
        usuario.getRoles().stream().filter(r -> r.isActivo() && r.getEmpresaId().equals(empresa.getId())).forEach(rol -> {
            permisos.add("ROLE_" + rol.getCodigo());
            rol.getPermisos().forEach(p -> permisos.add(p.getCodigo()));
        });
        usuario.registrarAcceso();
        var principal = new TenantPrincipal(usuario.getId(), empresa.getId(), empresa.getCodigo(), usuario.getNombre(),
            usuario.getUsuario(), usuario.getPasswordHash(), true, permisos);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    @Override public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
