package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class SaldosCuentasService {
    private static final Set<Integer> PAGE_SIZES=Set.of(5,10,25,50,100);
    private static final UUID EMPTY_ID=new UUID(0,0);
    private final FinancialMovementRepository movimientos;
    private final CashRegisterSessionRepository sesiones;
    private final BankAccountRepository bancos;
    private final CashRegisterRepository cajas;
    private final MonedaRepository monedas;
    private final SucursalRepository sucursales;
    private final TasaCambioRepository tasas;

    public SaldosCuentasService(FinancialMovementRepository movimientos,CashRegisterSessionRepository sesiones,
            BankAccountRepository bancos,CashRegisterRepository cajas,MonedaRepository monedas,
            SucursalRepository sucursales,TasaCambioRepository tasas){
        this.movimientos=movimientos;this.sesiones=sesiones;this.bancos=bancos;this.cajas=cajas;
        this.monedas=monedas;this.sucursales=sucursales;this.tasas=tasas;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('saldos_cuentas.ver')")
    public SaldosCatalogosDto catalogos(){
        UUID empresa=EmpresaContext.requerirEmpresaId();
        var currencies=monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresa).stream()
            .map(m->new SaldosCatalogosDto.Opcion(m.getId(),m.getCodigoIso()+" · "+m.getNombre())).toList();
        var branches=sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa).stream()
            .filter(s->EmpresaContext.permiteSucursal(s.getId()))
            .map(s->new SaldosCatalogosDto.Opcion(s.getId(),s.getNombre())).toList();
        UUID base=monedas.findByEmpresaIdAndMonedaBaseTrue(empresa).filter(Moneda::isActivo)
            .map(Moneda::getId).orElse(null);
        return new SaldosCatalogosDto(currencies,base,branches);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('saldos_cuentas.ver') and hasAuthority('saldos_cuentas.ver_bancaria')")
    public Page<SaldoCuentaDto> bancos(LocalDate hasta,String buscar,UUID moneda,Boolean activo,int pagina,int tamano){
        validar(hasta,pagina,tamano);var p=TenantContext.principalActual();
        validarMoneda(moneda,p.empresaId());
        return movimientos.saldosBancarios(p.tenantId(),p.empresaId(),hasta,limpiar(buscar),moneda,activo,
            PageRequest.of(pagina,tamano)).map(x->saldo(x,TipoCuentaDinero.BANK_ACCOUNT));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('saldos_cuentas.ver')")
    public Page<SaldoCuentaDto> cajas(LocalDate hasta,String buscar,UUID moneda,Boolean activo,UUID sucursal,
            int pagina,int tamano){
        validar(hasta,pagina,tamano);var p=TenantContext.principalActual();
        validarMoneda(moneda,p.empresaId());validarSucursal(sucursal,p.empresaId());
        return movimientos.saldosCajas(p.tenantId(),p.empresaId(),hasta,limpiar(buscar),moneda,activo,sucursal,
            p.accesoTodasSucursales(),ramas(p.sucursalIds()),PageRequest.of(pagina,tamano))
            .map(x->saldo(x,TipoCuentaDinero.CASH_REGISTER));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('saldos_cuentas.ver')")
    public ResumenSaldosDto resumen(LocalDate hasta,String buscar,UUID moneda,Boolean activo,UUID sucursal,
            UUID monedaPresentacion,boolean incluirBancos,boolean incluirCajas){
        if(hasta==null)throw new ReglaNegocioException("La fecha de corte es obligatoria.");
        var p=TenantContext.principalActual();validarMoneda(moneda,p.empresaId());validarSucursal(sucursal,p.empresaId());
        Moneda display=monedas.findByIdAndEmpresaId(monedaPresentacion,p.empresaId()).filter(Moneda::isActivo)
            .orElseGet(()->monedas.findByEmpresaIdAndMonedaBaseTrue(p.empresaId()).filter(Moneda::isActivo)
                .orElseThrow(()->new ReglaNegocioException("Configura una moneda base para consultar el resumen.")));
        List<TotalSaldoMonedaProjection> bankRows=incluirBancos&&puede("saldos_cuentas.ver_bancaria")
            ? movimientos.totalesBancarios(p.tenantId(),p.empresaId(),hasta,limpiar(buscar),moneda,activo):List.of();
        List<TotalSaldoMonedaProjection> cashRows=incluirCajas?movimientos.totalesCajas(p.tenantId(),p.empresaId(),hasta,
            limpiar(buscar),moneda,activo,sucursal,p.accesoTodasSucursales(),ramas(p.sucursalIds())):List.of();
        Map<String,BigDecimal> originales=new TreeMap<>();
        List<String> warnings=new ArrayList<>();
        Optional<BigDecimal> bank=convertir(bankRows,display,hasta,p,warnings,originales);
        Optional<BigDecimal> cash=convertir(cashRows,display,hasta,p,warnings,originales);
        boolean equivalent=warnings.isEmpty();
        boolean converted=java.util.stream.Stream.concat(bankRows.stream(),cashRows.stream())
            .anyMatch(row->!row.getMonedaId().equals(display.getId()));
        BigDecimal b=bank.orElse(null),c=cash.orElse(null);
        BigDecimal total=equivalent?(nvl(b).add(nvl(c))):null;
        return new ResumenSaldosDto(b,c,total,display.getCodigoIso(),display.getSimbolo(),equivalent,converted,
            Collections.unmodifiableMap(originales),List.copyOf(new LinkedHashSet<>(warnings)),OffsetDateTime.now());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('saldos_cuentas.ver') and hasAuthority('saldos_cuentas.ver_movimientos')")
    public Page<MovimientoSaldoDto> movimientos(TipoCuentaDinero tipo,UUID cuenta,LocalDate desde,LocalDate hasta,
            String buscar,TipoMovimientoFinanciero direccion,int pagina,int tamano){
        validar(hasta,pagina,tamano);if(tipo==null||cuenta==null)throw new RecursoNoEncontradoException("Cuenta no encontrada.");
        var p=TenantContext.principalActual();BigDecimal inicial=BigDecimal.ZERO;
        if(tipo==TipoCuentaDinero.BANK_ACCOUNT){
            if(!puede("saldos_cuentas.ver_bancaria"))throw new ReglaNegocioException("No tienes permiso para consultar movimientos bancarios.");
            bancos.findByIdAndTenantIdAndEmpresaId(cuenta,p.tenantId(),p.empresaId())
                .orElseThrow(()->new RecursoNoEncontradoException("Cuenta bancaria no encontrada."));
        }else{
            Caja caja=cajas.findByIdAndTenantIdAndEmpresaId(cuenta,p.tenantId(),p.empresaId())
                .orElseThrow(()->new RecursoNoEncontradoException("Caja no encontrada."));
            if(!EmpresaContext.permiteSucursal(caja.getSucursalId()))throw new RecursoNoEncontradoException("Caja no encontrada.");
            inicial=nvl(sesiones.fondoInicialHasta(p.tenantId(),p.empresaId(),cuenta,hasta));
        }
        if(desde!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        return movimientos.detalleSaldo(p.tenantId(),p.empresaId(),tipo.name(),cuenta,inicial,desde,hasta,
            limpiar(buscar),direccion==null?null:direccion.name(),PageRequest.of(pagina,tamano)).map(this::movimiento);
    }

    private Optional<BigDecimal> convertir(List<TotalSaldoMonedaProjection> rows,Moneda destino,LocalDate fecha,
            com.citacloud.springboot.contacloud.app.security.TenantPrincipal p,List<String>warnings,
            Map<String,BigDecimal> originales){
        BigDecimal total=BigDecimal.ZERO;boolean complete=true;
        for(var row:rows){BigDecimal amount=nvl(row.getTotal());originales.merge(row.getMoneda(),amount,BigDecimal::add);
            if(row.getMonedaId().equals(destino.getId())){total=total.add(amount);continue;}
            var direct=tasas.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
                p.tenantId(),p.empresaId(),row.getMonedaId(),destino.getId(),fecha);
            if(direct.isPresent()){total=total.add(amount.multiply(direct.get().getTasa()));continue;}
            var reverse=tasas.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
                p.tenantId(),p.empresaId(),destino.getId(),row.getMonedaId(),fecha);
            if(reverse.isPresent()&&reverse.get().getTasa().signum()!=0){total=total.add(amount.divide(reverse.get().getTasa(),8,RoundingMode.HALF_UP));continue;}
            complete=false;warnings.add("No existe una tasa de cambio para "+row.getMoneda()+" → "+destino.getCodigoIso()+" al "+
                fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+".");
        }
        return complete?Optional.of(total):Optional.empty();
    }
    private SaldoCuentaDto saldo(SaldoCuentaProjection x,TipoCuentaDinero tipo){return new SaldoCuentaDto(
        x.getCuentaId(),tipo,x.getCuenta(),x.getMonedaId(),x.getMoneda(),x.getSimbolo(),x.getSucursalId(),x.getSucursal(),
        x.getActivo(),nvl(x.getSaldoInicial()),nvl(x.getEntradas()),nvl(x.getSalidas()),nvl(x.getSaldoActual()));}
    private MovimientoSaldoDto movimiento(MovimientoSaldoProjection x){
        TipoMovimientoFinanciero type=TipoMovimientoFinanciero.valueOf(x.getTipo());BigDecimal amount=nvl(x.getMonto());
        return new MovimientoSaldoDto(x.getId(),x.getFecha(),x.getRegistradoEn(),x.getDocumento(),x.getConcepto(),type,
            type==TipoMovimientoFinanciero.INCOME?amount:BigDecimal.ZERO,type==TipoMovimientoFinanciero.EXPENSE?amount:BigDecimal.ZERO,
            nvl(x.getSaldo()),EstadoMovimientoFinanciero.valueOf(x.getEstado()),TipoOrigenMovimiento.valueOf(x.getOrigen()),x.getOrigenId());
    }
    private void validar(LocalDate hasta,int pagina,int tamano){if(hasta==null)throw new ReglaNegocioException("La fecha de corte es obligatoria.");
        if(pagina<0||!PAGE_SIZES.contains(tamano))throw new IllegalArgumentException("Paginación inválida");}
    private void validarMoneda(UUID id,UUID empresa){if(id!=null&&monedas.findByIdAndEmpresaId(id,empresa).isEmpty())throw new ReglaNegocioException("La moneda seleccionada no es válida.");}
    private void validarSucursal(UUID id,UUID empresa){if(id==null)return;var s=sucursales.findByIdAndEmpresaId(id,empresa)
        .orElseThrow(()->new ReglaNegocioException("La sucursal seleccionada no es válida."));if(!EmpresaContext.permiteSucursal(s.getId()))throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");}
    private static Set<UUID> ramas(Set<UUID> ids){return ids.isEmpty()?Set.of(EMPTY_ID):ids;}
    private static String limpiar(String value){return value==null?"":value.trim();}
    private static BigDecimal nvl(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static boolean puede(String permission){return TenantContext.principalActual().permisos().contains(permission);}
}
