package com.citacloud.springboot.contacloud.app.models;

public enum TipoCuentaBancaria {
    CHECKING("Corriente"),
    SAVINGS("Ahorros"),
    OTHER("Otra");

    private final String etiqueta;

    TipoCuentaBancaria(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
