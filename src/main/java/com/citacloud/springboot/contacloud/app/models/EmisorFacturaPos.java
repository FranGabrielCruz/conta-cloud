package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;

/** Identity captured at confirmation; never refreshed when an invoice is reprinted. */
@Embeddable
public class EmisorFacturaPos {
    @Column(name="pos_issuer_name", length=200, updatable=false) private String nombre;
    @Column(name="pos_issuer_tax_id", length=60, updatable=false) private String identificacion;
    @Column(name="pos_issuer_address", length=500, updatable=false) private String direccion;
    @Column(name="pos_issuer_phone", length=60, updatable=false) private String telefono;
    @Column(name="pos_issuer_country", length=2, updatable=false) private String pais;
    @Column(name="pos_cash_register_name", length=100, updatable=false) private String caja;
    @Column(name="pos_issuer_zone", length=80, updatable=false) private String zona;
    protected EmisorFacturaPos() { }
    public EmisorFacturaPos(String nombre, String identificacion, String direccion, String telefono, String pais, String caja, String zona) {
        this.nombre=nombre;this.identificacion=identificacion;this.direccion=direccion;this.telefono=telefono;this.pais=pais;this.caja=caja;this.zona=zona;
    }
    public String getNombre(){return nombre;} public String getIdentificacion(){return identificacion;}
    public String getDireccion(){return direccion;} public String getTelefono(){return telefono;}
    public String getPais(){return pais;} public String getCaja(){return caja;} public String getZona(){return zona;}
}
