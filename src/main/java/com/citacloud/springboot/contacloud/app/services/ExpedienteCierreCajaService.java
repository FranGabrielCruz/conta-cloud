package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Consulta histórica del turno asociado a un cierre, sin recalcular el snapshot del cierre. */
@Service
public class ExpedienteCierreCajaService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,20,50);
    private final CierreCajaRepository cierres;
    private final CashRegisterSessionRepository sesiones;
    private final FacturaVentaRepository facturas;
    private final CobroClienteRepository cobros;
    private final FinancialMovementRepository movimientos;
    private final RecuentoCajaRepository recuentos;

    public ExpedienteCierreCajaService(CierreCajaRepository cierres,CashRegisterSessionRepository sesiones,
            FacturaVentaRepository facturas,CobroClienteRepository cobros,
            FinancialMovementRepository movimientos,RecuentoCajaRepository recuentos){
        this.cierres=cierres;this.sesiones=sesiones;this.facturas=facturas;this.cobros=cobros;
        this.movimientos=movimientos;this.recuentos=recuentos;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial') and hasAuthority('facturas.ver')")
    public Page<FacturaVentaResumenDto> ventas(UUID cierreId,String buscar,EstadoFacturaVenta estado,int pagina,int tamano){
        validarPagina(pagina,tamano);Contexto c=contexto(cierreId);String texto=limpiar(buscar).toLowerCase(Locale.ROOT);
        String identificacion=texto.replaceAll("\\D","");
        return facturas.buscarPorSesion(c.tenant(),c.empresa(),c.sesion(),texto,identificacion,estado,
                PageRequest.of(pagina,tamano,Sort.by(Sort.Order.desc("confirmadaEn"),Sort.Order.desc("id"))))
            .map(f->new FacturaVentaResumenDto(f.getId(),f.getNumero(),f.getConfirmadaEn(),f.getClienteNombre(),
                f.getTotal(),f.getMonedaCodigo(),f.getStatus()));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial') and hasAuthority('facturas.ver')")
    public BigDecimal totalVentas(UUID cierreId){Contexto c=contexto(cierreId);
        BigDecimal total=facturas.totalConfirmadoPorSesion(c.tenant(),c.empresa(),c.sesion());
        return total==null?BigDecimal.ZERO:total;}

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial') and hasAuthority('recibos.ver')")
    public Page<CobroClienteDto> cobros(UUID cierreId,String buscar,int pagina,int tamano){
        validarPagina(pagina,tamano);Contexto c=contexto(cierreId);String texto=limpiar(buscar).toLowerCase(Locale.ROOT);
        return cobros.buscarPorSesion(c.tenant(),c.empresa(),c.sesion(),texto,texto.replaceAll("\\D",""),pagina,tamano);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial') and hasAuthority('movimientos_caja.ver')")
    public Page<MovimientoCajaDto> movimientos(UUID cierreId,String buscar,int pagina,int tamano){
        validarPagina(pagina,tamano);Contexto c=contexto(cierreId);
        return movimientos.buscarMovimientosCaja(c.tenant(),c.empresa(),c.sesion(),limpiar(buscar),"ALL","ALL",
            PageRequest.of(pagina,tamano)).map(ExpedienteCierreCajaService::movimiento);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial') and hasAuthority('movimientos_caja.ver')")
    public MovimientoCajaDto movimiento(UUID cierreId,UUID movimientoId){Contexto c=contexto(cierreId);
        return movimientos.detalleMovimientoCaja(c.tenant(),c.empresa(),c.sesion(),movimientoId)
            .map(ExpedienteCierreCajaService::movimiento)
            .orElseThrow(()->new RecursoNoEncontradoException("Movimiento de caja no encontrado."));}

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial') and hasAuthority('recuentos_caja.ver')")
    public Page<RecuentoCajaDto> recuentos(UUID cierreId,int pagina,int tamano){
        validarPagina(pagina,tamano);Contexto c=contexto(cierreId);
        return recuentos.historial(c.tenant(),c.empresa(),c.sesion(),PageRequest.of(pagina,tamano,
            Sort.by(Sort.Order.desc("contadoEn"),Sort.Order.desc("id")))).map(ExpedienteCierreCajaService::recuento);
    }

    private Contexto contexto(UUID cierreId){
        if(cierreId==null)throw new RecursoNoEncontradoException("Cierre de caja no encontrado.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        CierreCaja cierre=cierres.findByIdAndTenantIdAndEmpresaId(cierreId,p.tenantId(),empresa)
            .orElseThrow(()->new RecursoNoEncontradoException("Cierre de caja no encontrado."));
        SesionCaja sesion=sesiones.findByIdAndTenantIdAndEmpresaId(cierre.getSesionId(),p.tenantId(),empresa)
            .orElseThrow(()->new RecursoNoEncontradoException("Turno de caja no encontrado."));
        if(!cierre.getCajaId().equals(sesion.getCajaId())||!cierre.getSucursalId().equals(sesion.getSucursalId())
                ||!EmpresaContext.permiteSucursal(cierre.getSucursalId()))
            throw new RecursoNoEncontradoException("Cierre de caja no encontrado.");
        return new Contexto(p.tenantId(),empresa,cierre.getSesionId());
    }

    private static MovimientoCajaDto movimiento(MovimientoCajaProjection x){
        OffsetDateTime fecha=x.getFechaHora()==null?null:x.getFechaHora().atOffset(ZoneOffset.UTC);
        return new MovimientoCajaDto(x.getId(),x.getNumero(),fecha,x.getCaja(),x.getSucursal(),x.getUsuario(),
            TipoMovimientoFinanciero.valueOf(x.getTipo()),TipoOrigenMovimiento.valueOf(x.getOrigen()),x.getConcepto(),
            x.getReferencia(),x.getImporte(),x.getSaldo(),x.getMonedaCodigo(),x.getObservacion(),
            EstadoMovimientoFinanciero.valueOf(x.getEstado()),x.getReversionDeId(),false);
    }
    private static RecuentoCajaDto recuento(RecuentoCaja r){return new RecuentoCajaDto(r.getId(),r.getNumero(),
        r.getSesionId(),r.getSesion()==null?null:r.getSesion().getCodigoVisible(),r.getCaja().getNombre(),
        r.getSucursal().getNombre(),r.getMoneda().getCodigoIso(),r.getMoneda().getSimbolo(),r.getContadoEn(),
        r.getUsuario().getUsuario(),r.getEsperado(),r.getContado(),r.getDiferencia(),r.getResult(),r.getObservation(),List.of());}
    private static String limpiar(String valor){if(valor==null)return "";String v=valor.trim();return v.length()>180?v.substring(0,180):v;}
    private static void validarPagina(int pagina,int tamano){if(pagina<0||!PAGE_SIZES.contains(tamano))
        throw new ReglaNegocioException("Paginación inválida.");}
    private record Contexto(UUID tenant,UUID empresa,UUID sesion){}
}
