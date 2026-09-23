package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.UsuarioInicialDto;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UsuarioInicialValidator {
    private static final Pattern USUARIO = Pattern.compile("^[a-zA-Z0-9._-]{3,60}$");
    private UsuarioInicialValidator() {}

    public static UsuarioInicialDto validar(UsuarioInicialDto input, String clave, String confirmarClave) {
        if (input == null) throw new ReglaNegocioException("Los datos del usuario administrador son obligatorios.");
        String usuario = requerido(input.usuario(), "Usuario", 60).toLowerCase(Locale.ROOT);
        if (!USUARIO.matcher(usuario).matches())
            throw new ReglaNegocioException("El usuario debe tener entre 3 y 60 caracteres y solo puede incluir letras, números, punto, guion o guion bajo.");
        String nombre = requerido(input.nombre(), "Nombre", 150);
        String apellido = requerido(input.apellido(), "Apellido", 100);
        String correo = CompanyConfigurationService.normalizeEmail(input.correo());
        String telefono = CompanyConfigurationService.normalizePhone(input.telefono());
        String password = clave == null ? "" : clave;
        if (password.length() < 8 || password.length() > 72)
            throw new ReglaNegocioException("La contraseña debe tener entre 8 y 72 caracteres.");
        if (!password.equals(confirmarClave == null ? "" : confirmarClave))
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        return new UsuarioInicialDto(usuario, nombre, apellido, correo, telefono);
    }

    private static String requerido(String valor, String campo, int maximo) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) throw new ReglaNegocioException(campo + " es obligatorio.");
        if (limpio.length() > maximo) throw new ReglaNegocioException(campo + " excede " + maximo + " caracteres.");
        return limpio;
    }
}
