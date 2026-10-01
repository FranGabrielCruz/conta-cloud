package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.TransferenciaFinancieraMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinancialTransferService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private static final int RATE_SCALE=8;
    private static final RoundingMode ROUNDING=RoundingMode.HALF_UP;
    private final FinancialTransferRepository repository;
    private final TasaCambioRepository tasas;
    private final CuentaDineroResolver cuentas;
    private final FinancialMovementService movimientos;
    private final TransferenciaFinancieraMapper mapper;
    private final AuditoriaService auditoria;

    public FinancialTransferService(FinancialTransferRepository repository,TasaCambioRepository tasas,
            CuentaDineroResolver cuentas,FinancialMovementService movimientos,
            TransferenciaFinancieraMapper mapper,AuditoriaService auditoria){
        this.repository=repository;this.tasas=tasas;this.cuentas=cuentas;this.movimientos=movimientos;
        this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('transferencias.ver')")
    public Page<TransferenciaFinancieraDto> buscar(String texto,LocalDate desde,LocalDate hasta,
            EstadoMovimientoFinanciero estado,int pagina,int tamano,String orden,boolean asc){
        if(pagina<0||!PAGE_SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))
            throw new ReglaNegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        var p=TenantContext.principalActual();Set<UUID> sucursales=p.sucursalIds().isEmpty()
            ?Set.of(new UUID(0L,0L)):p.sucursalIds();
        return repository.buscar(p.tenantId(),EmpresaContext.requerirEmpresaId(),limpiar(texto),desde,hasta,estado,
            p.accesoTodasSucursales(),sucursales,PageRequest.of(pagina,tamano,orden(orden,asc))).map(mapper::toDto);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('transferencias.ver')")
    public TransferenciaFinancieraDto obtener(UUID id){return mapper.toDto(segura(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('transferencias.crear')")
    public MovimientoCatalogosDto catalogos(){return new MovimientoCatalogosDto(cuentas.opciones());}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('transferencias.crear')")
    public Optional<TasaTransferenciaDto> resolverTasa(UUID monedaOrigenId,UUID monedaDestinoId,LocalDate fecha){
        if(fecha==null||monedaOrigenId==null||monedaDestinoId==null||monedaOrigenId.equals(monedaDestinoId))
            return Optional.empty();
        return tasaConfigurada(monedaOrigenId,monedaDestinoId,fecha)
            .map(t->new TasaTransferenciaDto(t.tasa,descripcionTasa(t.tasa,t.codigoOrigen,t.codigoDestino)));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('transferencias.crear')")
    public TransferenciaFinancieraDto crear(TransferenciaFinancieraInput input){
        Validado v=validar(input);var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();
        CuentaDineroResolver.CuentaResuelta origen=cuentas.resolver(v.tipoOrigen,v.origenId);
        CuentaDineroResolver.CuentaResuelta destino=cuentas.resolver(v.tipoDestino,v.destinoId);
        if(origen.tipo()==destino.tipo()&&origen.id().equals(destino.id()))
            throw new ReglaNegocioException("El origen y el destino deben ser diferentes.");
        Conversion conversion=conversion(v,origen,destino);
        TransferenciaFinanciera entity=new TransferenciaFinanciera(p.tenantId(),empresaId,v.fecha,
            origen.tipo(),origen.id(),destino.tipo(),destino.id(),origen.monedaId(),destino.monedaId(),
            v.montoOrigen,conversion.montoDestino,conversion.tasa,conversion.descripcionTasa,v.referencia,v.descripcion,p.usuarioId());
        entity.asignarRelaciones(origen.caja(),origen.cuentaBancaria(),destino.caja(),destino.cuentaBancaria(),
            origen.moneda(),destino.moneda());
        repository.saveAndFlush(entity);
        movimientos.registrarTransferencia(entity.getId(),TipoMovimientoFinanciero.EXPENSE,v.fecha,origen,
            v.montoOrigen,v.referencia,v.descripcion);
        movimientos.registrarTransferencia(entity.getId(),TipoMovimientoFinanciero.INCOME,v.fecha,destino,
            conversion.montoDestino,v.referencia,v.descripcion);
        auditoria.registrar("FINANCIAL_TRANSFER_CREATED","TransferenciaFinanciera",entity.getId(),detalle(entity));
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('transferencias.anular')")
    public TransferenciaFinancieraDto anular(UUID id,String motivo){
        if(id==null)throw new RecursoNoEncontradoException("Transferencia no encontrada.");
        TransferenciaFinanciera entity=repository.buscarParaAnular(id,TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Transferencia no encontrada."));
        validarAccesoSucursales(entity);
        if(entity.getEstado()==EstadoMovimientoFinanciero.VOIDED)
            throw new ReglaNegocioException("La transferencia ya está anulada.");
        String limpio=limpiarNulo(motivo);
        if(limpio==null)throw new ReglaNegocioException("El motivo de anulación es obligatorio.");
        if(limpio.length()>500)throw new ReglaNegocioException("El motivo de anulación excede 500 caracteres.");
        movimientos.anularTransferencia(entity.getId(),limpio);
        entity.anular(limpio,TenantContext.principalActual().usuarioId());repository.saveAndFlush(entity);
        auditoria.registrar("FINANCIAL_TRANSFER_VOIDED","TransferenciaFinanciera",entity.getId(),detalle(entity));
        return mapper.toDto(entity);
    }

    private Conversion conversion(Validado v,CuentaDineroResolver.CuentaResuelta origen,
            CuentaDineroResolver.CuentaResuelta destino){
        if(origen.monedaId().equals(destino.monedaId()))
            return new Conversion(v.montoOrigen,null,null);
        BigDecimal tasa=v.tasaCambio==null?tasaConfigurada(origen.monedaId(),destino.monedaId(),v.fecha)
            .map(TasaAplicable::tasa).orElseThrow(()->new ReglaNegocioException(
                "No existe una tasa de cambio válida para las monedas seleccionadas.")):normalizarTasa(v.tasaCambio);
        int decimales=Math.max(0,Math.min(4,destino.moneda().getDecimales()));
        BigDecimal montoDestino=v.montoOrigen.divide(tasa,decimales,ROUNDING);
        if(montoDestino.signum()<=0)throw new ReglaNegocioException("El monto destino debe ser mayor que cero.");
        return new Conversion(montoDestino,tasa,descripcionTasa(tasa,origen.moneda().getCodigoIso(),destino.moneda().getCodigoIso()));
    }

    private Optional<TasaAplicable> tasaConfigurada(UUID monedaOrigenId,UUID monedaDestinoId,LocalDate fecha){
        UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        Optional<TasaCambio> cotizacion=tasas.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId,empresaId,monedaDestinoId,monedaOrigenId,fecha);
        if(cotizacion.isPresent()){
            TasaCambio t=cotizacion.get();
            return Optional.of(new TasaAplicable(t.getTasa(),t.getMonedaDestino().getCodigoIso(),t.getMonedaOrigen().getCodigoIso()));
        }
        Optional<TasaCambio> inversa=tasas.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId,empresaId,monedaOrigenId,monedaDestinoId,fecha);
        if(inversa.isEmpty())return Optional.empty();
        TasaCambio t=inversa.get();BigDecimal tasa=BigDecimal.ONE.divide(t.getTasa(),RATE_SCALE,ROUNDING);
        return Optional.of(new TasaAplicable(tasa,t.getMonedaOrigen().getCodigoIso(),t.getMonedaDestino().getCodigoIso()));
    }

    private TransferenciaFinanciera segura(UUID id){
        if(id==null)throw new RecursoNoEncontradoException("Transferencia no encontrada.");
        TransferenciaFinanciera t=repository.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Transferencia no encontrada."));
        validarAccesoSucursales(t);return t;
    }
    private static void validarAccesoSucursales(TransferenciaFinanciera t){
        if(t.getTipoCuentaOrigen()==TipoCuentaDinero.CASH_REGISTER&&!EmpresaContext.permiteSucursal(t.getCajaOrigen().getSucursalId())
            ||t.getTipoCuentaDestino()==TipoCuentaDinero.CASH_REGISTER&&!EmpresaContext.permiteSucursal(t.getCajaDestino().getSucursalId()))
            throw new RecursoNoEncontradoException("Transferencia no encontrada.");
    }
    private static Validado validar(TransferenciaFinancieraInput i){
        if(i==null)throw new ReglaNegocioException("Los datos de la transferencia son obligatorios.");
        if(i.fecha()==null)throw new ReglaNegocioException("Selecciona una fecha.");
        if(i.tipoOrigen()==null||i.origenId()==null)throw new ReglaNegocioException("Selecciona un origen.");
        if(i.tipoDestino()==null||i.destinoId()==null)throw new ReglaNegocioException("Selecciona un destino.");
        if(i.tipoOrigen()==i.tipoDestino()&&i.origenId().equals(i.destinoId()))
            throw new ReglaNegocioException("El origen y el destino deben ser diferentes.");
        if(i.montoOrigen()==null||i.montoOrigen().signum()<=0)
            throw new ReglaNegocioException("Ingresa un monto mayor que cero.");
        if(i.montoOrigen().scale()>4||i.montoOrigen().precision()-i.montoOrigen().scale()>15)
            throw new ReglaNegocioException("El monto excede la precisión permitida.");
        String ref=limpiarNulo(i.referencia()),desc=limpiarNulo(i.descripcion());
        if(ref!=null&&ref.length()>100)throw new ReglaNegocioException("La referencia excede 100 caracteres.");
        if(desc!=null&&desc.length()>1000)throw new ReglaNegocioException("La descripción excede 1000 caracteres.");
        return new Validado(i.fecha(),i.tipoOrigen(),i.origenId(),i.tipoDestino(),i.destinoId(),i.montoOrigen(),i.tasaCambio(),ref,desc);
    }
    private static BigDecimal normalizarTasa(BigDecimal tasa){
        if(tasa==null||tasa.signum()<=0)throw new ReglaNegocioException("Ingresa una tasa de cambio mayor que cero.");
        if(tasa.scale()>RATE_SCALE||tasa.precision()-tasa.scale()>11)
            throw new ReglaNegocioException("La tasa admite hasta 11 enteros y 8 decimales.");
        return tasa.setScale(RATE_SCALE);
    }
    private static Sort orden(String campo,boolean asc){
        String propiedad=switch(campo==null?"fecha":campo){case"origen"->"tipoCuentaOrigen";case"destino"->"tipoCuentaDestino";
            case"moneda"->"monedaOrigen.codigoIso";case"monto"->"montoOrigen";case"estado"->"estado";default->"fecha";};
        return Sort.by(asc?Sort.Direction.ASC:Sort.Direction.DESC,propiedad).and(Sort.by(Sort.Direction.DESC,"creadoEn"));
    }
    private static String descripcionTasa(BigDecimal tasa,String origen,String destino){
        return "1 "+destino+" = "+tasa.stripTrailingZeros().toPlainString()+" "+origen;
    }
    private static String detalle(TransferenciaFinanciera t){return "{\"fecha\":\""+t.getFecha()+
        "\",\"origenId\":\""+(t.getCajaOrigenId()!=null?t.getCajaOrigenId():t.getCuentaBancariaOrigenId())+
        "\",\"destinoId\":\""+(t.getCajaDestinoId()!=null?t.getCajaDestinoId():t.getCuentaBancariaDestinoId())+
        "\",\"montoOrigen\":"+t.getMontoOrigen()+",\"montoDestino\":"+t.getMontoDestino()+
        ",\"estado\":\""+t.getEstado()+"\"}";}
    private static String limpiar(String v){return v==null?"":v.trim();}
    private static String limpiarNulo(String v){String x=limpiar(v);return x.isEmpty()?null:x;}
    private record Validado(LocalDate fecha,TipoCuentaDinero tipoOrigen,UUID origenId,TipoCuentaDinero tipoDestino,
        UUID destinoId,BigDecimal montoOrigen,BigDecimal tasaCambio,String referencia,String descripcion){}
    private record Conversion(BigDecimal montoDestino,BigDecimal tasa,String descripcionTasa){}
    private record TasaAplicable(BigDecimal tasa,String codigoOrigen,String codigoDestino){}
}
