package com.citacloud.springboot.contacloud.app.models;

public enum TipoTransferenciaInventario {
    DIRECT("Directa"), IN_TRANSIT("Con tránsito");
    private final String etiqueta;
    TipoTransferenciaInventario(String etiqueta){this.etiqueta=etiqueta;}
    public String getEtiqueta(){return etiqueta;}
}
