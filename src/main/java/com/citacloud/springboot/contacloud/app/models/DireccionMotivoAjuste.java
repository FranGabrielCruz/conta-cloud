package com.citacloud.springboot.contacloud.app.models;
public enum DireccionMotivoAjuste { IN("Entrada"),OUT("Salida"),BOTH("Ambos"); private final String etiqueta; DireccionMotivoAjuste(String e){etiqueta=e;} public String getEtiqueta(){return etiqueta;} public boolean permite(int signo){return this==BOTH||(this==IN&&signo>0)||(this==OUT&&signo<0);} }
