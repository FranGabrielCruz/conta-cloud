package com.citacloud.springboot.contacloud.app.models;
public enum TipoAjusteInventario { ENTRY("Entrada"),EXIT("Salida"),MIXED("Mixto"); private final String etiqueta; TipoAjusteInventario(String e){etiqueta=e;} public String getEtiqueta(){return etiqueta;} }
