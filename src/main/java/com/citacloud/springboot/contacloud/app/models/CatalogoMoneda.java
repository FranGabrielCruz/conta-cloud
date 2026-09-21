package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;

@Entity @Table(name = "catalogo_monedas")
public class CatalogoMoneda {
    @Id @Column(name = "codigo_iso", length = 3) private String codigoIso;
    @Column(nullable = false, length = 80) private String nombre;
    @Column(nullable = false, length = 10) private String simbolo;
    @Column(nullable = false) private short decimales;
    @Column(nullable = false) private boolean activo;
    protected CatalogoMoneda() {}
    public String getCodigoIso() { return codigoIso; }
    public String getNombre() { return nombre; }
    public String getSimbolo() { return simbolo; }
    public short getDecimales() { return decimales; }
    public boolean isActivo() { return activo; }
}
