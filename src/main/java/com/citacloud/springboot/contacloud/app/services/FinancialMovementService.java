package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.MovimientoFinancieroMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinancialMovementService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final FinancialMovementRepository repository;
    private final CuentaDineroResolver cuentaResolver;
    private final MovimientoFinancieroMapper mapper;
    private final AuditoriaService auditoria;

    public FinancialMovementService(FinancialMovementRepository repository,CuentaDineroResolver cuentaResolver,
            MovimientoFinancieroMapper mapper,AuditoriaService auditoria) {
        this.repository=repository;this.cuentaResolver=cuentaResolver;this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('ingresos.ver')")
    public Page<MovimientoFinancieroDto> buscarIngresos(String q,LocalDate desde,LocalDate hasta,
            TipoCuentaDinero tipoCuenta,UUID cuentaId,EstadoMovimientoFinanciero estado,
            int pagina,int tamano,String orden,boolean asc){
        return buscar(TipoMovimientoFinanciero.INCOME,q,desde,hasta,tipoCuenta,cuentaId,estado,pagina,tamano,orden,asc);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('egresos.ver')")
    public Page<MovimientoFinancieroDto> buscarEgresos(String q,LocalDate desde,LocalDate hasta,
            TipoCuentaDinero tipoCuenta,UUID cuentaId,EstadoMovimientoFinanciero estado,
            int pagina,int tamano,String orden,boolean asc){
        return buscar(TipoMovimientoFinanciero.EXPENSE,q,desde,hasta,tipoCuenta,cuentaId,estado,pagina,tamano,orden,asc);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('ingresos.ver')")
    public MovimientoFinancieroDto obtenerIngreso(UUID id){return mapper.toDto(seguro(id,TipoMovimientoFinanciero.INCOME));}
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('egresos.ver')")
    public MovimientoFinancieroDto obtenerEgreso(UUID id){return mapper.toDto(seguro(id,TipoMovimientoFinanciero.EXPENSE));}
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('ingresos.ver')")
    public MovimientoCatalogosDto catalogosIngresos(){return catalogos();}
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('egresos.ver')")
    public MovimientoCatalogosDto catalogosEgresos(){return catalogos();}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('ingresos.crear')")
    public MovimientoFinancieroDto crearIngreso(MovimientoFinancieroInput input){return crear(TipoMovimientoFinanciero.INCOME,input);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('egresos.crear')")
    public MovimientoFinancieroDto crearEgreso(MovimientoFinancieroInput input){return crear(TipoMovimientoFinanciero.EXPENSE,input);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('ingresos.editar')")
    public MovimientoFinancieroDto actualizarIngreso(UUID id,MovimientoFinancieroInput input){return actualizar(id,TipoMovimientoFinanciero.INCOME,input);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('egresos.editar')")
    public MovimientoFinancieroDto actualizarEgreso(UUID id,MovimientoFinancieroInput input){return actualizar(id,TipoMovimientoFinanciero.EXPENSE,input);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('ingresos.anular')")
    public MovimientoFinancieroDto anularIngreso(UUID id,String motivo){return anular(id,TipoMovimientoFinanciero.INCOME,motivo);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('egresos.anular')")
    public MovimientoFinancieroDto anularEgreso(UUID id,String motivo){return anular(id,TipoMovimientoFinanciero.EXPENSE,motivo);}

    private Page<MovimientoFinancieroDto> buscar(TipoMovimientoFinanciero tipo,String q,LocalDate desde,
            LocalDate hasta,TipoCuentaDinero tipoCuenta,UUID cuentaId,EstadoMovimientoFinanciero estado,
            int pagina,int tamano,String orden,boolean asc) {
        if(pagina<0||!PAGE_SIZES.contains(tamano)) throw new ReglaNegocioException("Paginación inválida.");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))
            throw new ReglaNegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        if(cuentaId!=null&&tipoCuenta==null) throw new ReglaNegocioException("El tipo de cuenta es obligatorio.");
        var p=TenantContext.principalActual();
        Set<UUID> sucursales=p.sucursalIds().isEmpty()?Set.of(new UUID(0L,0L)):p.sucursalIds();
        return repository.buscar(p.tenantId(),EmpresaContext.requerirEmpresaId(),tipo,limpiar(q),desde,hasta,
            tipoCuenta,cuentaId,estado,p.accesoTodasSucursales(),sucursales,
            PageRequest.of(pagina,tamano,orden(orden,asc))).map(mapper::toDto);
    }

    private MovimientoCatalogosDto catalogos() {
        return new MovimientoCatalogosDto(cuentaResolver.opciones());
    }

    private MovimientoFinancieroDto crear(TipoMovimientoFinanciero tipo,MovimientoFinancieroInput input) {
        Validado v=validar(input); var p=TenantContext.principalActual(); UUID empresaId=EmpresaContext.requerirEmpresaId();
        CuentaDineroResolver.CuentaResuelta cuenta=cuentaResolver.resolver(v.tipoCuenta,v.cuentaId);
        MovimientoFinanciero entity=new MovimientoFinanciero(p.tenantId(),empresaId,tipo,v.fecha,v.tipoCuenta,
            v.cuentaId,cuenta.monedaId(),v.monto,v.concepto,v.referencia,v.descripcion,p.usuarioId());
        entity.asignarRelacionesCuenta(cuenta.caja(),cuenta.cuentaBancaria(),cuenta.moneda());
        repository.saveAndFlush(entity);
        auditoria.registrar(evento(tipo,"CREATED"),"MovimientoFinanciero",entity.getId(),detalle(entity));
        return mapper.toDto(seguro(entity.getId(),tipo));
    }

    private MovimientoFinancieroDto actualizar(UUID id,TipoMovimientoFinanciero tipo,MovimientoFinancieroInput input) {
        MovimientoFinanciero entity=seguroParaActualizar(id,tipo);
        if(entity.getEstado()!=EstadoMovimientoFinanciero.REGISTERED)
            throw new ReglaNegocioException("No se puede editar un movimiento anulado.");
        if(entity.getTipoOrigen()!=TipoOrigenMovimiento.MANUAL)
            throw new ReglaNegocioException("Este movimiento fue generado automáticamente y no puede editarse manualmente.");
        Validado v=validar(input); CuentaDineroResolver.CuentaResuelta cuenta=cuentaResolver.resolver(v.tipoCuenta,v.cuentaId);
        String anterior=detalle(entity);
        if(entity.getMonto().compareTo(v.monto)!=0)
            throw new ReglaNegocioException("El monto de un movimiento registrado no puede modificarse.");
        entity.actualizar(v.fecha,v.tipoCuenta,v.cuentaId,cuenta.monedaId(),v.monto,v.concepto,v.referencia,
            v.descripcion,TenantContext.principalActual().usuarioId());
        entity.asignarRelacionesCuenta(cuenta.caja(),cuenta.cuentaBancaria(),cuenta.moneda());
        repository.saveAndFlush(entity);
        auditoria.registrar(evento(tipo,"UPDATED"),"MovimientoFinanciero",entity.getId(),
            "{\"anterior\":"+anterior+",\"nuevo\":"+detalle(entity)+"}");
        return mapper.toDto(seguro(entity.getId(),tipo));
    }

    private MovimientoFinancieroDto anular(UUID id,TipoMovimientoFinanciero tipo,String motivo) {
        MovimientoFinanciero entity=seguroParaActualizar(id,tipo);
        if(entity.getEstado()==EstadoMovimientoFinanciero.VOIDED)
            throw new ReglaNegocioException("El movimiento ya está anulado.");
        String limpio=limpiarNulo(motivo);
        if(limpio==null) throw new ReglaNegocioException("El motivo de anulación es obligatorio.");
        if(limpio.length()>500) throw new ReglaNegocioException("El motivo de anulación excede 500 caracteres.");
        entity.anular(limpio,TenantContext.principalActual().usuarioId()); repository.saveAndFlush(entity);
        auditoria.registrar(evento(tipo,"VOIDED"),"MovimientoFinanciero",entity.getId(),detalle(entity));
        return mapper.toDto(seguro(entity.getId(),tipo));
    }

    private MovimientoFinanciero seguro(UUID id,TipoMovimientoFinanciero tipo) {
        if(id==null) throw new RecursoNoEncontradoException("Movimiento no encontrado.");
        MovimientoFinanciero m=repository.findByIdAndTenantIdAndEmpresaIdAndTipoMovimiento(id,
            TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId(),tipo)
            .orElseThrow(()->new RecursoNoEncontradoException("Movimiento no encontrado."));
        if(m.getTipoCuenta()==TipoCuentaDinero.CASH_REGISTER&&!EmpresaContext.permiteSucursal(m.getCaja().getSucursalId()))
            throw new RecursoNoEncontradoException("Movimiento no encontrado.");
        return m;
    }

    private MovimientoFinanciero seguroParaActualizar(UUID id,TipoMovimientoFinanciero tipo) {
        if(id==null) throw new RecursoNoEncontradoException("Movimiento no encontrado.");
        MovimientoFinanciero m=repository.buscarParaActualizar(id,TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId(),tipo)
            .orElseThrow(()->new RecursoNoEncontradoException("Movimiento no encontrado."));
        if(m.getTipoCuenta()==TipoCuentaDinero.CASH_REGISTER&&!EmpresaContext.permiteSucursal(m.getCaja().getSucursalId()))
            throw new RecursoNoEncontradoException("Movimiento no encontrado.");
        return m;
    }

    MovimientoFinanciero registrarTransferencia(UUID transferenciaId,TipoMovimientoFinanciero direccion,
            LocalDate fecha,CuentaDineroResolver.CuentaResuelta cuenta,BigDecimal monto,String referencia,String descripcion){
        var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();
        String concepto=direccion==TipoMovimientoFinanciero.EXPENSE?"Transferencia enviada":"Transferencia recibida";
        MovimientoFinanciero entity=MovimientoFinanciero.transferencia(p.tenantId(),empresaId,direccion,fecha,
            cuenta.tipo(),cuenta.id(),cuenta.monedaId(),monto,concepto,referencia,descripcion,transferenciaId,p.usuarioId());
        entity.asignarRelacionesCuenta(cuenta.caja(),cuenta.cuentaBancaria(),cuenta.moneda());
        return repository.saveAndFlush(entity);
    }

    void anularTransferencia(UUID transferenciaId,String motivo){
        var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();
        List<MovimientoFinanciero> movimientos=repository.buscarTransferenciaParaAnular(p.tenantId(),empresaId,transferenciaId);
        if(movimientos.size()!=2||movimientos.stream().map(MovimientoFinanciero::getTipoMovimiento).distinct().count()!=2)
            throw new ReglaNegocioException("Los movimientos de la transferencia no están completos.");
        if(movimientos.stream().anyMatch(m->m.getEstado()!=EstadoMovimientoFinanciero.REGISTERED))
            throw new ReglaNegocioException("Los movimientos de la transferencia ya fueron anulados.");
        movimientos.forEach(m->m.anular(motivo,p.usuarioId()));
        repository.saveAll(movimientos);repository.flush();
    }

    private static Validado validar(MovimientoFinancieroInput input) {
        if(input==null) throw new ReglaNegocioException("Los datos del movimiento son obligatorios.");
        if(input.fecha()==null) throw new ReglaNegocioException("La fecha es obligatoria.");
        if(input.tipoCuenta()==null||input.cuentaId()==null) throw new ReglaNegocioException("La cuenta es obligatoria.");
        String concepto=limpiar(input.concepto());
        if(concepto.isEmpty()) throw new ReglaNegocioException("El concepto es obligatorio.");
        if(concepto.length()>180) throw new ReglaNegocioException("El concepto excede 180 caracteres.");
        BigDecimal monto=input.monto();
        if(monto==null||monto.signum()<=0) throw new ReglaNegocioException("El monto debe ser mayor que cero.");
        if(monto.scale()>4||monto.precision()-monto.scale()>15)
            throw new ReglaNegocioException("El monto excede la precisión permitida.");
        String referencia=limpiarNulo(input.referencia()),descripcion=limpiarNulo(input.descripcion());
        if(referencia!=null&&referencia.length()>100) throw new ReglaNegocioException("La referencia excede 100 caracteres.");
        if(descripcion!=null&&descripcion.length()>1000) throw new ReglaNegocioException("La descripción excede 1000 caracteres.");
        return new Validado(input.fecha(),input.tipoCuenta(),input.cuentaId(),concepto,monto,referencia,descripcion);
    }
    private static Sort orden(String campo,boolean asc) {
        String propiedad=switch(campo==null?"fecha":campo){case "concepto"->"concepto";case "cuenta"->"tipoCuenta";
            case "moneda"->"moneda.codigoIso";case "monto"->"monto";case "estado"->"estado";default->"fecha";};
        return Sort.by(asc?Sort.Direction.ASC:Sort.Direction.DESC,propiedad)
            .and(Sort.by(Sort.Direction.DESC,"creadoEn"));
    }
    private static String evento(TipoMovimientoFinanciero tipo,String accion){
        return "FINANCIAL_"+(tipo==TipoMovimientoFinanciero.INCOME?"INCOME_":"EXPENSE_")+accion;
    }
    private static String detalle(MovimientoFinanciero m){return "{\"tipo\":\""+m.getTipoMovimiento()+
        "\",\"fecha\":\""+m.getFecha()+"\",\"tipoCuenta\":\""+m.getTipoCuenta()+
        "\",\"cuentaId\":\""+(m.getCajaId()!=null?m.getCajaId():m.getCuentaBancariaId())+
        "\",\"monedaId\":\""+m.getMonedaId()+"\",\"monto\":"+m.getMonto()+
        ",\"concepto\":\""+escapar(m.getConcepto())+"\",\"estado\":\""+m.getEstado()+"\"}";}
    private static String escapar(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private static String limpiar(String s){return s==null?"":s.trim();}
    private static String limpiarNulo(String s){String v=limpiar(s);return v.isEmpty()?null:v;}
    private record Validado(LocalDate fecha,TipoCuentaDinero tipoCuenta,UUID cuentaId,String concepto,
                            BigDecimal monto,String referencia,String descripcion){}
}
