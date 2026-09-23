package com.citacloud.springboot.contacloud.app.services;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CodigoEmpresaValidator {
    private static final Pattern FORMATO = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{2,29}$");

    private CodigoEmpresaValidator() {}

    public static String validarYNormalizar(String valor) {
        String codigo = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (codigo.isEmpty()) {
            throw new ReglaNegocioException("El código de acceso de la empresa es obligatorio.");
        }
        if (!FORMATO.matcher(codigo).matches()) {
            throw new ReglaNegocioException(
                "El código de acceso debe tener entre 3 y 30 caracteres y solo puede incluir letras, números, punto, guion o guion bajo.");
        }
        return codigo;
    }
}
