package com.citacloud.springboot.contacloud.app.models;
public enum EstadoAjusteInventario { DRAFT("Borrador"),CONFIRMED("Confirmado"),CANCELLED("Anulado"),REVERSED("Revertido"); private final String etiqueta; EstadoAjusteInventario(String e){etiqueta=e;} public String getEtiqueta(){return etiqueta;} }
