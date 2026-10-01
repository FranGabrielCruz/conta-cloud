package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ConciliacionBancariaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class BankReconciliationService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final BankReconciliationRepository reconciliaciones;
    private final BankStatementMovementRepository bancos;
    private final BankReconciliationMatchRepository asociaciones;
    private final FinancialMovementRepository financieros;
    private final BankAccountRepository cuentas;
    private final ConciliacionBancariaMapper mapper;
    private final AuditoriaService auditoria;

    public BankReconciliationService(BankReconciliationRepository reconciliaciones,
            BankStatementMovementRepository bancos,BankReconciliationMatchRepository asociaciones,
            FinancialMovementRepository financieros,BankAccountRepository cuentas,
            ConciliacionBancariaMapper mapper,AuditoriaService auditoria){
        this.reconciliaciones=reconciliaciones;this.bancos=bancos;this.asociaciones=asociaciones;
        this.financieros=financieros;this.cuentas=cuentas;this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public List<CuentaBancariaConciliacionDto> cuentasDisponibles(){
        var p=TenantContext.principalActual();return cuentas
            .findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByBancoNombreAscNombreCuentaAsc(p.tenantId(),p.empresaId())
            .stream().map(c->new CuentaBancariaConciliacionDto(c.getId(),ConciliacionBancariaMapper.etiqueta(c),
                c.getMonedaId(),c.getMoneda().getCodigoIso(),c.getMoneda().getDecimales())).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public Page<ConciliacionBancariaDto> buscar(UUID cuentaId,LocalDate desde,LocalDate hasta,
            EstadoConciliacionBancaria estado,int pagina,int tamano,String ordenarPor,boolean ascendente){
        paginacion(pagina,tamano);fechasFiltro(desde,hasta);var p=TenantContext.principalActual();
        if(cuentaId!=null)cuentaSegura(cuentaId,false);
        return reconciliaciones.buscar(p.tenantId(),p.empresaId(),cuentaId,desde,hasta,estado,
            PageRequest.of(pagina,tamano,orden(ordenarPor,ascendente))).map(mapper::toDto);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public ConciliacionBancariaDto obtener(UUID id){return mapper.toDto(segura(id));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.crear')")
    public ConciliacionBancariaDto crear(ConciliacionBancariaInput input){
        validarInput(input);var p=TenantContext.principalActual();CuentaBancaria cuenta=cuentas
            .bloquearParaConciliacion(input.cuentaBancariaId(),p.tenantId(),p.empresaId())
            .orElseThrow(()->new ReglaNegocioException("La cuenta bancaria seleccionada no es válida."));
        if(!cuenta.isActivo())throw new ReglaNegocioException("La cuenta bancaria seleccionada está inactiva.");
        if(reconciliaciones.existeSolapamiento(p.tenantId(),p.empresaId(),cuenta.getId(),input.fechaInicial(),input.fechaFinal()))
            throw new ReglaNegocioException("Ya existe una conciliación que se superpone con este período.");
        ConciliacionBancaria e=mapper.toEntity(input,p.tenantId(),p.empresaId(),cuenta,p.usuarioId());
        try{reconciliaciones.saveAndFlush(e);}catch(DataIntegrityViolationException ex){
            throw new ReglaNegocioException("Ya existe una conciliación que se superpone con este período.",ex);}
        auditoria.registrar("BANK_RECONCILIATION_CREATED","ConciliacionBancaria",e.getId(),detalle(e));
        return mapper.toDto(e);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public Page<MovimientoConciliableDto> movimientosContaCloud(UUID conciliacionId,int pagina,int tamano){
        paginacion(pagina,tamano);ConciliacionBancaria r=segura(conciliacionId);
        return financieros.candidatosConciliacion(r.getTenantId(),r.getEmpresaId(),r.getCuentaBancariaId(),
            r.getMonedaId(),r.getFechaFinal(),PageRequest.of(pagina,tamano,Sort.by("fecha").ascending().and(Sort.by("creadoEn"))))
            .map(m->movimientoDto(m,r.getFechaInicial()));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public Page<MovimientoEstadoBancarioDto> movimientosBanco(UUID conciliacionId,int pagina,int tamano){
        paginacion(pagina,tamano);ConciliacionBancaria r=segura(conciliacionId);
        return bancos.pendientes(r.getTenantId(),r.getEmpresaId(),r.getId(),PageRequest.of(pagina,tamano,
            Sort.by("fecha").ascending().and(Sort.by("creadoEn")))).map(mapper::toDto);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.conciliar')")
    public MovimientoEstadoBancarioDto agregarMovimientoBanco(UUID conciliacionId,MovimientoEstadoBancarioInput input){
        ConciliacionBancaria r=bloqueada(conciliacionId);enProceso(r);validarMovimientoBanco(input,r);
        var p=TenantContext.principalActual();MovimientoEstadoBancario b=new MovimientoEstadoBancario(p.tenantId(),
            p.empresaId(),r.getId(),r.getCuentaBancariaId(),input.fecha(),input.direccion(),input.descripcion().trim(),
            limpiarNulo(input.referencia()),normalizar(input.monto(),r),p.usuarioId());bancos.saveAndFlush(b);
        auditoria.registrar("BANK_RECONCILIATION_BANK_MOVEMENT_CREATED","MovimientoEstadoBancario",b.getId(),
            "{\"conciliacionId\":\""+r.getId()+"\"}");return mapper.toDto(b);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.conciliar')")
    public void conciliar(UUID conciliacionId,UUID movimientoFinancieroId,UUID movimientoBancoId){
        ConciliacionBancaria r=bloqueada(conciliacionId);enProceso(r);var p=TenantContext.principalActual();
        MovimientoFinanciero f=financieros.findByIdAndTenantIdAndEmpresaIdAndTipoCuentaAndCuentaBancariaId(
            movimientoFinancieroId,p.tenantId(),p.empresaId(),TipoCuentaDinero.BANK_ACCOUNT,r.getCuentaBancariaId())
            .orElseThrow(()->new ReglaNegocioException("El movimiento de ContaCloud no es válido para esta conciliación."));
        MovimientoEstadoBancario b=bancos.findByIdAndTenantIdAndEmpresaIdAndConciliacionId(movimientoBancoId,
            p.tenantId(),p.empresaId(),r.getId()).orElseThrow(()->new ReglaNegocioException("El movimiento bancario no es válido."));
        if(f.getEstado()!=EstadoMovimientoFinanciero.REGISTERED)throw new ReglaNegocioException("El movimiento de ContaCloud está anulado.");
        if(!f.getMonedaId().equals(r.getMonedaId()))throw new ReglaNegocioException("Las monedas de los movimientos no coinciden.");
        if(direccion(f)!=b.getDirection())throw new ReglaNegocioException("Las direcciones de los movimientos no son compatibles.");
        if(normalizar(f.getMonto(),r).compareTo(normalizar(b.getAmount(),r))!=0)
            throw new ReglaNegocioException("Los montos de los movimientos no coinciden.");
        if(asociaciones.existsByMovimientoFinancieroId(f.getId())||asociaciones.existsByMovimientoBancarioId(b.getId()))
            throw new ReglaNegocioException("El movimiento ya fue conciliado.");
        AsociacionConciliacionBancaria a=new AsociacionConciliacionBancaria(p.tenantId(),p.empresaId(),r.getId(),
            f.getId(),b.getId(),p.usuarioId());
        try{asociaciones.saveAndFlush(a);}catch(DataIntegrityViolationException ex){
            throw new ReglaNegocioException("El movimiento ya fue conciliado.",ex);}
        auditoria.registrar("BANK_RECONCILIATION_MATCH_CREATED","AsociacionConciliacionBancaria",a.getId(),
            detalleAsociacion(a));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.conciliar')")
    public void desconciliar(UUID conciliacionId,UUID asociacionId){
        ConciliacionBancaria r=bloqueada(conciliacionId);enProceso(r);var p=TenantContext.principalActual();
        AsociacionConciliacionBancaria a=asociaciones.findByIdAndTenantIdAndEmpresaIdAndConciliacionId(asociacionId,
            p.tenantId(),p.empresaId(),r.getId()).orElseThrow(()->new RecursoNoEncontradoException("Asociación no encontrada."));
        String detalle=detalleAsociacion(a);asociaciones.delete(a);asociaciones.flush();
        auditoria.registrar("BANK_RECONCILIATION_MATCH_REMOVED","ConciliacionBancaria",r.getId(),detalle);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public Page<AsociacionConciliacionDto> conciliados(UUID conciliacionId,int pagina,int tamano){
        paginacion(pagina,tamano);ConciliacionBancaria r=segura(conciliacionId);Page<AsociacionConciliacionBancaria> page=
            asociaciones.findAllByTenantIdAndEmpresaIdAndConciliacionId(r.getTenantId(),r.getEmpresaId(),r.getId(),
                PageRequest.of(pagina,tamano,Sort.by("conciliadoEn").descending()));
        return mapearAsociaciones(page,r);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.ver')")
    public ResumenConciliacionBancariaDto resumen(UUID conciliacionId){return calcular(segura(conciliacionId));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.finalizar')")
    public void finalizar(UUID conciliacionId){
        ConciliacionBancaria r=bloqueada(conciliacionId);enProceso(r);cuentaSegura(r.getCuentaBancariaId(),true);
        ResumenConciliacionBancariaDto resumen=calcular(r);
        if(!resumen.cuadrada()||resumen.pendientesBanco()>0)
            throw new ReglaNegocioException("La conciliación presenta diferencias y no puede finalizarse.");
        r.finalizar(TenantContext.principalActual().usuarioId());reconciliaciones.saveAndFlush(r);
        auditoria.registrar("BANK_RECONCILIATION_FINALIZED","ConciliacionBancaria",r.getId(),detalle(r));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.anular')")
    public void anular(UUID conciliacionId,String motivo){
        String limpio=requerido(motivo,"Ingresa el motivo de anulación.",500);ConciliacionBancaria r=bloqueada(conciliacionId);
        if(r.getStatus()==EstadoConciliacionBancaria.VOIDED)throw new ReglaNegocioException("La conciliación ya fue anulada.");
        var p=TenantContext.principalActual();asociaciones.deleteAllByTenantIdAndEmpresaIdAndConciliacionId(
            p.tenantId(),p.empresaId(),r.getId());r.anular(limpio,p.usuarioId());reconciliaciones.saveAndFlush(r);
        auditoria.registrar("BANK_RECONCILIATION_VOIDED","ConciliacionBancaria",r.getId(),detalle(r));
    }

    private ResumenConciliacionBancariaDto calcular(ConciliacionBancaria r){
        List<MovimientoEstadoBancario> todos=bancos.findAllByTenantIdAndEmpresaIdAndConciliacionId(r.getTenantId(),r.getEmpresaId(),r.getId());
        List<AsociacionConciliacionBancaria> matches=asociaciones.findAllByTenantIdAndEmpresaIdAndConciliacionId(r.getTenantId(),r.getEmpresaId(),r.getId());
        Map<UUID,MovimientoEstadoBancario> porId=new HashMap<>();todos.forEach(b->porId.put(b.getId(),b));
        BigDecimal creditos=sumar(todos,DireccionMovimientoBancario.INFLOW);BigDecimal debitos=sumar(todos,DireccionMovimientoBancario.OUTFLOW);
        BigDecimal neto=creditos.subtract(debitos);BigDecimal netoConciliado=BigDecimal.ZERO;
        for(var a:matches){MovimientoEstadoBancario b=porId.get(a.getMovimientoBancarioId());if(b!=null)
            netoConciliado=netoConciliado.add(b.getDirection()==DireccionMovimientoBancario.INFLOW?b.getAmount():b.getAmount().negate());}
        BigDecimal saldoConciliado=r.getSaldoInicialBanco().add(netoConciliado);BigDecimal diferencia=r.getSaldoFinalBanco().subtract(saldoConciliado);
        long bancoPendiente=todos.size()-matches.size();long internos=financieros.candidatosConciliacion(r.getTenantId(),r.getEmpresaId(),
            r.getCuentaBancariaId(),r.getMonedaId(),r.getFechaFinal(),PageRequest.of(0,1)).getTotalElements();
        int escala=escala(r);BigDecimal cero=BigDecimal.ZERO.setScale(escala);diferencia=diferencia.setScale(escala,RoundingMode.HALF_UP);
        boolean consistente=r.getSaldoInicialBanco().add(neto).setScale(escala,RoundingMode.HALF_UP)
            .compareTo(r.getSaldoFinalBanco().setScale(escala,RoundingMode.HALF_UP))==0;
        return new ResumenConciliacionBancariaDto(r.getSaldoInicialBanco(),creditos,debitos,neto,r.getSaldoFinalBanco(),
            saldoConciliado,diferencia,internos,bancoPendiente,matches.size(),consistente,diferencia.compareTo(cero)==0);
    }

    private Page<AsociacionConciliacionDto> mapearAsociaciones(Page<AsociacionConciliacionBancaria> page,ConciliacionBancaria r){
        List<UUID> fids=page.stream().map(AsociacionConciliacionBancaria::getMovimientoFinancieroId).toList();
        List<UUID> bids=page.stream().map(AsociacionConciliacionBancaria::getMovimientoBancarioId).toList();
        Map<UUID,MovimientoFinanciero> fs=new HashMap<>();financieros.findAllById(fids).forEach(x->fs.put(x.getId(),x));
        Map<UUID,MovimientoEstadoBancario> bs=new HashMap<>();bancos.findAllById(bids).forEach(x->bs.put(x.getId(),x));
        return page.map(a->{MovimientoFinanciero f=fs.get(a.getMovimientoFinancieroId());MovimientoEstadoBancario b=bs.get(a.getMovimientoBancarioId());
            if(f==null||b==null||!f.getTenantId().equals(r.getTenantId())||!b.getTenantId().equals(r.getTenantId()))
                throw new ReglaNegocioException("La conciliación contiene una asociación inconsistente.");
            return new AsociacionConciliacionDto(a.getId(),f.getId(),b.getId(),f.getFecha(),f.getConcepto(),b.getFecha(),
                b.getDescription(),b.getReference(),b.getAmount(),b.getDirection(),a.getConciliadoEn());});
    }

    private ConciliacionBancaria segura(UUID id){var p=TenantContext.principalActual();return reconciliaciones
        .findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
        .orElseThrow(()->new RecursoNoEncontradoException("Conciliación bancaria no encontrada."));}
    private ConciliacionBancaria bloqueada(UUID id){var p=TenantContext.principalActual();return reconciliaciones
        .bloquear(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Conciliación bancaria no encontrada."));}
    private CuentaBancaria cuentaSegura(UUID id,boolean activa){var p=TenantContext.principalActual();CuentaBancaria c=cuentas
        .findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new ReglaNegocioException("La cuenta bancaria seleccionada no es válida."));
        if(activa&&!c.isActivo())throw new ReglaNegocioException("La cuenta bancaria seleccionada está inactiva.");return c;}
    private static void validarInput(ConciliacionBancariaInput i){if(i==null)throw new ReglaNegocioException("Los datos de la conciliación son obligatorios.");
        if(i.cuentaBancariaId()==null)throw new ReglaNegocioException("Selecciona una cuenta bancaria.");
        if(i.fechaInicial()==null)throw new ReglaNegocioException("Selecciona una fecha inicial.");
        if(i.fechaFinal()==null)throw new ReglaNegocioException("Selecciona una fecha final.");
        if(i.fechaInicial().isAfter(i.fechaFinal()))throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        if(i.saldoInicialBanco()==null)throw new ReglaNegocioException("Ingresa el saldo inicial según el banco.");
        if(i.saldoFinalBanco()==null)throw new ReglaNegocioException("Ingresa el saldo final según el banco.");}
    private static void validarMovimientoBanco(MovimientoEstadoBancarioInput i,ConciliacionBancaria r){
        if(i==null||i.fecha()==null)throw new ReglaNegocioException("Selecciona la fecha del movimiento bancario.");
        if(i.fecha().isBefore(r.getFechaInicial())||i.fecha().isAfter(r.getFechaFinal()))throw new ReglaNegocioException("La fecha del movimiento debe pertenecer al período de conciliación.");
        if(i.direccion()==null)throw new ReglaNegocioException("Selecciona el tipo de movimiento bancario.");
        requerido(i.descripcion(),"Ingresa la descripción del movimiento bancario.",180);
        if(i.monto()==null||i.monto().signum()<=0)throw new ReglaNegocioException("Ingresa un monto mayor que cero.");
        if(i.referencia()!=null&&i.referencia().trim().length()>100)throw new ReglaNegocioException("La referencia excede 100 caracteres.");}
    private static void enProceso(ConciliacionBancaria r){if(r.getStatus()!=EstadoConciliacionBancaria.IN_PROGRESS)
        throw new ReglaNegocioException(r.getStatus()==EstadoConciliacionBancaria.FINALIZED?"La conciliación ya fue finalizada.":"La conciliación está anulada.");}
    private static MovimientoConciliableDto movimientoDto(MovimientoFinanciero m,LocalDate inicio){return new MovimientoConciliableDto(
        m.getId(),m.getFecha(),m.getConcepto(),m.getReferencia(),m.getMonto(),direccion(m),m.getFecha().isBefore(inicio));}
    private static DireccionMovimientoBancario direccion(MovimientoFinanciero m){return m.getTipoMovimiento()==TipoMovimientoFinanciero.INCOME
        ?DireccionMovimientoBancario.INFLOW:DireccionMovimientoBancario.OUTFLOW;}
    private static BigDecimal sumar(List<MovimientoEstadoBancario> items,DireccionMovimientoBancario d){return items.stream()
        .filter(x->x.getDirection()==d).map(MovimientoEstadoBancario::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static BigDecimal normalizar(BigDecimal n,ConciliacionBancaria r){return n.setScale(escala(r),RoundingMode.HALF_UP);}
    private static int escala(ConciliacionBancaria r){return Math.max(0,Math.min(4,r.getMoneda().getDecimales()));}
    private static Sort orden(String campo,boolean asc){String p=switch(campo==null?"periodo":campo){case "cuenta"->"cuentaBancaria.nombreCuenta";
        case "moneda"->"moneda.codigoIso";case "estado"->"status";default->"fechaInicial";};Sort.Direction d=asc?Sort.Direction.ASC:Sort.Direction.DESC;
        return Sort.by(d,p).and(Sort.by(d,"creadoEn"));}
    private static void paginacion(int p,int s){if(p<0||!PAGE_SIZES.contains(s))throw new ReglaNegocioException("Paginación inválida.");}
    private static void fechasFiltro(LocalDate d,LocalDate h){if(d!=null&&h!=null&&d.isAfter(h))throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la fecha final.");}
    private static String requerido(String s,String mensaje,int max){String x=s==null?"":s.trim();if(x.isEmpty())throw new ReglaNegocioException(mensaje);
        if(x.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return x;}
    private static String limpiarNulo(String s){String x=s==null?"":s.trim();return x.isEmpty()?null:x;}
    private static String detalle(ConciliacionBancaria r){return "{\"cuentaBancariaId\":\""+r.getCuentaBancariaId()+"\",\"estado\":\""+r.getStatus()+"\"}";}
    private static String detalleAsociacion(AsociacionConciliacionBancaria a){return "{\"conciliacionId\":\""+a.getConciliacionId()+
        "\",\"financialMovementId\":\""+a.getMovimientoFinancieroId()+"\",\"bankStatementMovementId\":\""+a.getMovimientoBancarioId()+"\"}";}
}
