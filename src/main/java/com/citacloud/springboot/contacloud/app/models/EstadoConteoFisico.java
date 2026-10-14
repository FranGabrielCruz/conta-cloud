package com.citacloud.springboot.contacloud.app.models;
public enum EstadoConteoFisico{DRAFT("Borrador"),IN_PROGRESS("En conteo"),PENDING_CONFIRMATION("Pendiente de confirmación"),CONFIRMED("Confirmado"),CANCELLED("Anulado");private final String etiqueta;EstadoConteoFisico(String e){etiqueta=e;}public String getEtiqueta(){return etiqueta;}}
