package com.citacloud.springboot.contacloud.app.services;

public final class PermisoTenantPolicy {
    private PermisoTenantPolicy() {}

    public static boolean disponibleParaAdministradorInicial(String modulo, String recurso) {
        return !"INFRAESTRUCTURA".equalsIgnoreCase(limpiar(modulo))
            && !"empresas".equalsIgnoreCase(limpiar(recurso));
    }

    private static String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
