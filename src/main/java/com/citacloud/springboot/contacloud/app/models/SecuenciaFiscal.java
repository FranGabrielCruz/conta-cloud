package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="secuencias")
public class SecuenciaFiscal {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false) private UUID empresaId;
    @Column(name="tipo_comprobante_id",nullable=false) private UUID comprobanteId;
    @Column(nullable=false,length=40) private String codigo;
    @Column(name="numero_inicial",nullable=false) private long numeroInicial;
    @Column(name="valor_actual",nullable=false) private long numeroActual;
    @Column(name="numero_final") private Long numeroFinal;
    @Column(nullable=false) private short longitud=8;
    @Column(nullable=false) private boolean activo=true;
    @Column(name="creado_en",nullable=false,updatable=false) private OffsetDateTime creadoEn=OffsetDateTime.now();
    @Column(name="actualizado_en",nullable=false) private OffsetDateTime actualizadoEn=OffsetDateTime.now();
    protected SecuenciaFiscal(){}
    public SecuenciaFiscal(UUID tenantId,UUID empresaId,UUID comprobanteId,String codigo,long inicial,Long fin){this.tenantId=tenantId;this.empresaId=empresaId;this.comprobanteId=comprobanteId;this.codigo=codigo;this.numeroInicial=inicial;this.numeroActual=inicial-1;this.numeroFinal=fin;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public UUID getComprobanteId(){return comprobanteId;} public void setComprobanteId(UUID v){comprobanteId=v;} public String getCodigo(){return codigo;} public long getNumeroInicial(){return numeroInicial;} public void setNumeroInicial(long v){numeroInicial=v;} public long getNumeroActual(){return numeroActual;} public void setNumeroActual(long v){numeroActual=v;} public Long getNumeroFinal(){return numeroFinal;} public void setNumeroFinal(Long v){numeroFinal=v;} public short getLongitud(){return longitud;} public boolean isActivo(){return activo;} public void setActivo(boolean v){activo=v;} public boolean agotada(){return numeroFinal!=null&&numeroActual>=numeroFinal;} public boolean utilizada(){return numeroActual>=numeroInicial;} public long siguiente(){return Math.addExact(numeroActual,1);}
    @PreUpdate void actualizarFecha(){actualizadoEn=OffsetDateTime.now();}
}
