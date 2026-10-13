package com.citacloud.springboot.contacloud.app.models;

public enum EstadoTransferenciaInventario {
    DRAFT("Borrador"), IN_TRANSIT("En tránsito"), PARTIALLY_RECEIVED("Recibida parcialmente"),
    RECEIVED("Recibida"), CANCELLED("Cancelada");
    private final String etiqueta;
    EstadoTransferenciaInventario(String etiqueta){this.etiqueta=etiqueta;}
    public String getEtiqueta(){return etiqueta;}
}
