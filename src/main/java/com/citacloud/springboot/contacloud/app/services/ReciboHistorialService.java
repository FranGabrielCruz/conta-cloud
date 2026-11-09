package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
public class ReciboHistorialService {
    private static final Logger LOG=LoggerFactory.getLogger(ReciboHistorialService.class);
    private final CobroClienteService cobros;
    private final CobroClienteRepository recibos;
    private final PaymentReceiptSnapshotRepository snapshots;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datos;

    public ReciboHistorialService(CobroClienteService cobros,CobroClienteRepository recibos,
            PaymentReceiptSnapshotRepository snapshots,EmpresaRepository empresas,DatosEmpresaRepository datos) {
        this.cobros=cobros;this.recibos=recibos;this.snapshots=snapshots;this.empresas=empresas;this.datos=datos;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.ver')")
    public Page<ReciboHistorialDto.Resumen> buscar(String texto,LocalDate desde,LocalDate hasta,Boolean anulados,int pagina,int tamano) {
        if(pagina<0||tamano<1||tamano>100)throw new ReglaNegocioException("Paginación inválida.");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la final.");
        var p=TenantContext.principalActual();String busqueda=texto==null?"":texto.strip();
        if(busqueda.length()>150)throw new ReglaNegocioException("La búsqueda excede 150 caracteres.");
        String identificacion=busqueda.matches("[0-9 .-]+")?busqueda.replaceAll("[^0-9]",""):"";
        var empresa=EmpresaContext.requerirEmpresaId();
        return recibos.historial(p.tenantId(),empresa,
            p.accesoTodasSucursales()||p.permisos().contains("recibos.ver_otras_sucursales"),p.sucursalIds(),busqueda,identificacion,desde,hasta,anulados,pagina,tamano);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.ver')")
    public ReciboHistorialDto detalle(UUID id) {
        var p=TenantContext.principalActual();var empresa=EmpresaContext.requerirEmpresaId();
        var r=cobros.detalle(id);
        var almacenado=snapshots.obtener(id,p.tenantId(),empresa);
        if(almacenado==null)throw new RecursoNoEncontradoException("Recibo no encontrado.");
        var e=empresas.findByIdAndTenantId(empresa,p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Recibo no encontrado."));
        var zona=ZoneId.of(e.getZonaHoraria());
        var d=almacenado.json()==null?legado(r,e,zona):snapshots.documento(almacenado);
        var a=recibos.datosAnulacion(id,p.tenantId(),empresa);
        LOG.atDebug().addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",empresa)
            .addKeyValue("ReceiptId",id).addKeyValue("UserId",p.usuarioId()).addKeyValue("TraceId",TraceContext.currentOrCreate()).log("receipt_history_read");
        return new ReciboHistorialDto(r,d,a==null||a.fecha()==null?null:a.fecha().atZoneSameInstant(zona).toLocalDateTime(),
            a==null?null:a.usuario(),anulables(p,empresa,List.of(id)).contains(id));
    }

    private Set<UUID> anulables(TenantPrincipal principal, UUID empresa, List<UUID> ids) {
        if (!principal.permisos().contains("recibos.anular") || ids.isEmpty()) return Set.of();
        return recibos.recibosAnulables(principal.tenantId(),empresa,ids,
            principal.accesoTodasSucursales(),principal.sucursalIds());
    }

    // Receipts predating the POS snapshot retain their historical amounts and document labels.
    // Their issuer identity was not stored; use current company details only for that missing header.
    private PaymentReceiptPrintDocument legado(CobroClienteDto r,com.citacloud.springboot.contacloud.app.models.Empresa e,ZoneId zona) {
        var de=datos.findByEmpresaId(e.getId()).orElse(null);
        var company=new CompanySnapshot(de!=null&&de.getNombreComercial()!=null&&!de.getNombreComercial().isBlank()?de.getNombreComercial():e.getNombre(),
            e.getIdentificacionFiscal(),de==null?null:de.getDireccion(),de==null?null:de.getTelefono(),e.getPaisCodigo()==null?"XX":e.getPaisCodigo(),e.getLogoObjectKey());
        return new PaymentReceiptPrintDocument(company,r.numero(),r.confirmadoEn().atZoneSameInstant(zona).toLocalDateTime(),
            new CustomerSnapshot(r.clienteId()==null?"Consumidor Final":r.cliente(),r.identificacion()),r.sucursal(),null,r.usuario(),null,r.moneda(),
            r.aplicaciones().stream().map(a->new PaidDocument(a.factura(),a.monto(),a.fechaFactura(),a.totalDocumento(),
                a.notaDebitoId()==null?"INVOICE":"DEBIT_NOTE",a.notaDebitoId()==null?a.facturaId():a.notaDebitoId())).toList(),
            r.aplicado(),r.medios().stream().map(m->new PaymentMethod(m.medio(),m.monto(),m.referencia())).toList(),r.recibido(),null,null,null,List.of());
    }
}
