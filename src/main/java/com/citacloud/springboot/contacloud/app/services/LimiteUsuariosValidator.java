package com.citacloud.springboot.contacloud.app.services;

public final class LimiteUsuariosValidator {
    private LimiteUsuariosValidator() {}

    public static Integer normalizar(boolean habilitado, Integer limite) {
        if (!habilitado) return null;
        if (limite == null || limite < 1)
            throw new ReglaNegocioException("Indique un límite de usuarios mayor o igual a 1.");
        if (limite > 100000)
            throw new ReglaNegocioException("El límite de usuarios no puede exceder 100,000.");
        return limite;
    }
}
