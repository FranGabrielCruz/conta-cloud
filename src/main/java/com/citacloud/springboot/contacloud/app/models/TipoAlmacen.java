package com.citacloud.springboot.contacloud.app.models;

public enum TipoAlmacen {
    GENERAL("General"), DEVOLUCIONES("Devoluciones"), DANADOS("Dañados"), TRANSITO("Tránsito");
    private final String etiqueta;
    TipoAlmacen(String etiqueta){this.etiqueta=etiqueta;}
    public String getEtiqueta(){return etiqueta;}
}
