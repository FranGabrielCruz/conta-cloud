package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_transfer")
public class TransferenciaInventario {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "empresa_id", nullable = false, updatable = false) private UUID empresaId;
    @Column(name = "transfer_number", nullable = false, updatable = false, length = 30) private String numero;
    @Column(name = "transfer_date", nullable = false) private LocalDate fecha;
    @Enumerated(EnumType.STRING) @Column(name = "transfer_type", nullable = false, length = 20) private TipoTransferenciaInventario tipo;
    @Column(name = "source_warehouse_id", nullable = false) private UUID almacenOrigenId;
    @Column(name = "destination_warehouse_id", nullable = false) private UUID almacenDestinoId;
    @Column(name = "transit_warehouse_id") private UUID almacenTransitoId;
    @Column(length = 100) private String reference;
    @Column(length = 1000) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EstadoTransferenciaInventario status;
    @Column(name = "dispatched_at") private OffsetDateTime despachadaEn;
    @Column(name = "dispatched_by") private UUID despachadaPor;
    @Column(name = "received_at") private OffsetDateTime recibidaEn;
    @Column(name = "received_by") private UUID recibidaPor;
    @Column(name = "cancelled_at") private OffsetDateTime canceladaEn;
    @Column(name = "cancelled_by") private UUID canceladaPor;
    @Column(name = "cancellation_reason", length = 500) private String motivoCancelacion;
    @Column(name = "reversal_of_id") private UUID reversoDeId;
    @Column(name = "source_warehouse_name_snapshot", length = 200) private String almacenOrigenHistorico;
    @Column(name = "source_branch_name_snapshot", length = 200) private String sucursalOrigenHistorica;
    @Column(name = "destination_warehouse_name_snapshot", length = 200) private String almacenDestinoHistorico;
    @Column(name = "destination_branch_name_snapshot", length = 200) private String sucursalDestinoHistorica;
    @Column(name = "dispatched_by_name_snapshot", length = 200) private String despachadaPorNombre;
    @Column(name = "received_by_name_snapshot", length = 200) private String recibidaPorNombre;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private OffsetDateTime creadaEn;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID creadaPor;
    @Column(name = "updated_at", nullable = false, insertable = false) private OffsetDateTime actualizadaEn;
    @Column(name = "updated_by", nullable = false) private UUID actualizadaPor;
    @Version @Column(nullable = false) private long version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_warehouse_id", insertable = false, updatable = false) private Almacen origen;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "destination_warehouse_id", insertable = false, updatable = false) private Almacen destino;

    protected TransferenciaInventario() {}

    public TransferenciaInventario(UUID tenant, UUID empresa, String numero, LocalDate fecha,
            TipoTransferenciaInventario tipo, UUID origen, UUID destino, String referencia, String notas,
            UUID usuario) {
        this.tenantId = tenant; this.empresaId = empresa; this.numero = numero; this.fecha = fecha;
        this.tipo = tipo; this.almacenOrigenId = origen; this.almacenDestinoId = destino;
        this.reference = referencia; this.notes = notas; this.status = EstadoTransferenciaInventario.DRAFT;
        this.creadaPor = usuario; this.actualizadaPor = usuario;
    }

    public void actualizar(LocalDate fecha, TipoTransferenciaInventario tipo, UUID origen, UUID destino,
            String referencia, String notas, UUID usuario) {
        exigirBorrador(); this.fecha = fecha; this.tipo = tipo; this.almacenOrigenId = origen;
        this.almacenDestinoId = destino; this.reference = referencia; this.notes = notas;
        this.actualizadaPor = usuario;
    }

    public void capturarContextoHistorico(Almacen origen, Almacen destino) {
        almacenOrigenHistorico = origen.getNombre(); sucursalOrigenHistorica = origen.getSucursal().getNombre();
        almacenDestinoHistorico = destino.getNombre(); sucursalDestinoHistorica = destino.getSucursal().getNombre();
    }

    public void confirmarDirecta(UUID usuario, String nombreUsuario) {
        exigirBorrador(); status = EstadoTransferenciaInventario.RECEIVED;
        despachadaEn = recibidaEn = OffsetDateTime.now(); despachadaPor = recibidaPor = usuario;
        despachadaPorNombre = recibidaPorNombre = nombreUsuario; actualizadaPor = usuario;
    }
    public void confirmarDirecta(UUID usuario) { confirmarDirecta(usuario, null); }

    public void despachar(UUID transito, UUID usuario, String nombreUsuario) {
        exigirBorrador(); almacenTransitoId = transito; status = EstadoTransferenciaInventario.IN_TRANSIT;
        despachadaEn = OffsetDateTime.now(); despachadaPor = usuario; despachadaPorNombre = nombreUsuario;
        actualizadaPor = usuario;
    }
    public void despachar(UUID transito, UUID usuario) { despachar(transito, usuario, null); }

    public void recibir(UUID usuario, String nombreUsuario) {
        if (status != EstadoTransferenciaInventario.IN_TRANSIT) throw new IllegalStateException("La transferencia no está en tránsito.");
        status = EstadoTransferenciaInventario.RECEIVED; recibidaEn = OffsetDateTime.now();
        recibidaPor = usuario; recibidaPorNombre = nombreUsuario; actualizadaPor = usuario;
    }
    public void recibir(UUID usuario) { recibir(usuario, null); }

    public void cancelar(String motivo, UUID usuario) {
        exigirBorrador(); status = EstadoTransferenciaInventario.CANCELLED; motivoCancelacion = motivo;
        canceladaEn = OffsetDateTime.now(); canceladaPor = usuario; actualizadaPor = usuario;
    }

    public void asignarAlmacenes(Almacen origen, Almacen destino) { this.origen = origen; this.destino = destino; }
    private void exigirBorrador() { if (status != EstadoTransferenciaInventario.DRAFT) throw new IllegalStateException("Solo se puede modificar una transferencia en borrador."); }
    @PreUpdate void marca() { actualizadaEn = OffsetDateTime.now(); }

    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNumero(){return numero;} public LocalDate getFecha(){return fecha;} public TipoTransferenciaInventario getTipo(){return tipo;}
    public UUID getAlmacenOrigenId(){return almacenOrigenId;} public UUID getAlmacenDestinoId(){return almacenDestinoId;} public UUID getAlmacenTransitoId(){return almacenTransitoId;}
    public String getReferencia(){return reference;} public String getNotas(){return notes;} public EstadoTransferenciaInventario getEstado(){return status;}
    public OffsetDateTime getDespachadaEn(){return despachadaEn;} public UUID getDespachadaPor(){return despachadaPor;}
    public OffsetDateTime getRecibidaEn(){return recibidaEn;} public UUID getRecibidaPor(){return recibidaPor;}
    public OffsetDateTime getCanceladaEn(){return canceladaEn;} public UUID getCanceladaPor(){return canceladaPor;}
    public String getMotivoCancelacion(){return motivoCancelacion;} public UUID getReversoDeId(){return reversoDeId;}
    public String getAlmacenOrigenHistorico(){return almacenOrigenHistorico;} public String getSucursalOrigenHistorica(){return sucursalOrigenHistorica;}
    public String getAlmacenDestinoHistorico(){return almacenDestinoHistorico;} public String getSucursalDestinoHistorica(){return sucursalDestinoHistorica;}
    public String getDespachadaPorNombre(){return despachadaPorNombre;} public String getRecibidaPorNombre(){return recibidaPorNombre;}
    public OffsetDateTime getCreadaEn(){return creadaEn;} public UUID getCreadaPor(){return creadaPor;}
    public OffsetDateTime getActualizadaEn(){return actualizadaEn;} public UUID getActualizadaPor(){return actualizadaPor;}
    public long getVersion(){return version;} public Almacen getOrigen(){return origen;} public Almacen getDestino(){return destino;}
}
