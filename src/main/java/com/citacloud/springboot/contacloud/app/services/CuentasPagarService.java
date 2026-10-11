package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CuentasPagarService {
    private static final Set<Integer> PAGE_SIZES=Set.of(5,10,25,50,100);
    private final CuentasPagarConsultaRepository consultas;private final SupplierRepository proveedores;
    private final MonedaRepository monedas;private final SucursalRepository sucursales;private final TasaCambioRepository tasas;

    public CuentasPagarService(CuentasPagarConsultaRepository consultas,SupplierRepository proveedores,
            MonedaRepository monedas,SucursalRepository sucursales,TasaCambioRepository tasas){
        this.consultas=consultas;this.proveedores=proveedores;this.monedas=monedas;this.sucursales=sucursales;this.tasas=tasas;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('cuentas_pagar.ver')")
    public CuentasPagarCatalogosDto catalogos(){var p=TenantContext.principalActual();UUID empresa=p.empresaId();
        var suppliers=proveedores.buscar(p.tenantId(),empresa,"","",true,PageRequest.of(0,500,Sort.by("nombreComercial")))
            .map(x->new CuentasPagarCatalogosDto.Opcion(x.getId(),x.getNombreComercial())).getContent();
        var currencies=monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresa).stream()
            .map(x->new CuentasPagarCatalogosDto.Opcion(x.getId(),x.getCodigoIso()+" · "+x.getNombre())).toList();
        var branches=sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa).stream()
            .filter(x->EmpresaContext.permiteSucursal(x.getId()))
            .map(x->new CuentasPagarCatalogosDto.Opcion(x.getId(),x.getNombre())).toList();
        UUID base=monedas.findByEmpresaIdAndMonedaBaseTrue(empresa).filter(Moneda::isActivo).map(Moneda::getId).orElse(null);
        return new CuentasPagarCatalogosDto(suppliers,currencies,base,branches);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('cuentas_pagar.ver')")
    public Page<CuentaPagarFacturaDto> facturas(Consulta c,int pagina,int tamano){validar(c,pagina,tamano);return consultas.facturas(filtros(c),PageRequest.of(pagina,tamano));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('cuentas_pagar.ver')")
    public Page<CuentaPagarProveedorDto> proveedores(Consulta c,int pagina,int tamano){validar(c,pagina,tamano);return consultas.proveedores(filtros(c),PageRequest.of(pagina,tamano));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('cuentas_pagar.ver') and hasAuthority('cuentas_pagar.ver_antiguedad')")
    public Page<AntiguedadCuentaPagarDto> antiguedad(Consulta c,int pagina,int tamano){validar(c,pagina,tamano);return consultas.antiguedad(filtros(c),PageRequest.of(pagina,tamano));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('cuentas_pagar.ver')")
    public ResumenCuentasPagarDto resumen(Consulta c,UUID monedaPresentacion){validar(c,0,10);var p=TenantContext.principalActual();
        Moneda display=monedas.findByIdAndEmpresaId(monedaPresentacion,p.empresaId()).filter(Moneda::isActivo)
            .orElseGet(()->monedas.findByEmpresaIdAndMonedaBaseTrue(p.empresaId()).filter(Moneda::isActivo)
                .orElseThrow(()->new ReglaNegocioException("Configura una moneda base para consultar Cuentas por pagar.")));
        Set<String>warnings=new LinkedHashSet<>();
        Map<String,BigDecimal> debts=new TreeMap<>(),overdueByCurrency=new TreeMap<>(),todayByCurrency=new TreeMap<>(),
            futureByCurrency=new TreeMap<>(),credits=new TreeMap<>();
        BigDecimal pending=BigDecimal.ZERO,overdue=BigDecimal.ZERO,today=BigDecimal.ZERO,future=BigDecimal.ZERO,available=BigDecimal.ZERO;
        boolean pendingComplete=true,overdueComplete=true,todayComplete=true,futureComplete=true,creditsComplete=true;
        for(var row:consultas.totales(filtros(c))){merge(debts,row.moneda(),row.pendiente());merge(overdueByCurrency,row.moneda(),row.vencido());
            merge(todayByCurrency,row.moneda(),row.venceHoy());merge(futureByCurrency,row.moneda(),row.porVencer());merge(credits,row.moneda(),row.creditos());
            Optional<BigDecimal> factor=factor(row.monedaId(),display,c.al(),p.tenantId(),p.empresaId());if(factor.isEmpty()){
                pendingComplete&=isZero(row.pendiente());overdueComplete&=isZero(row.vencido());todayComplete&=isZero(row.venceHoy());
                futureComplete&=isZero(row.porVencer());creditsComplete&=isZero(row.creditos());
                if(!isZero(row.pendiente())||!isZero(row.creditos()))warnings.add("No existe una tasa de cambio activa para "+row.moneda()+" → "+display.getCodigoIso()+" con fecha igual o anterior al "+c.al().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+".");continue;}
            BigDecimal x=factor.get();pending=pending.add(row.pendiente().multiply(x));overdue=overdue.add(row.vencido().multiply(x));today=today.add(row.venceHoy().multiply(x));future=future.add(row.porVencer().multiply(x));available=available.add(row.creditos().multiply(x));}
        boolean netComplete=pendingComplete&&creditsComplete;
        boolean complete=netComplete&&overdueComplete&&todayComplete&&futureComplete;
        Map<String,BigDecimal> netByCurrency=new TreeMap<>(debts);credits.forEach((currency,amount)->netByCurrency.merge(currency,amount.negate(),BigDecimal::add));
        return new ResumenCuentasPagarDto(pendingComplete?pending:null,overdueComplete?overdue:null,todayComplete?today:null,
            futureComplete?future:null,creditsComplete?available:null,netComplete?pending.subtract(available):null,
            display.getCodigoIso(),display.getSimbolo(),complete,immutable(debts),immutable(overdueByCurrency),immutable(todayByCurrency),
            immutable(futureByCurrency),immutable(credits),immutable(netByCurrency),List.copyOf(warnings),OffsetDateTime.now());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('cuentas_pagar.ver') and hasAuthority('cuentas_pagar.ver_movimientos')")
    public List<MovimientoCuentaPagarDto> movimientos(UUID factura,LocalDate al){if(factura==null||al==null)throw new ReglaNegocioException("La factura y la fecha de corte son obligatorias.");var p=TenantContext.principalActual();return consultas.movimientos(p.tenantId(),p.empresaId(),factura,al);}

    Optional<BigDecimal> factor(UUID origen,Moneda destino,LocalDate fecha,UUID tenant,UUID empresa){if(origen.equals(destino.getId()))return Optional.of(BigDecimal.ONE);
        var direct=tasas.findFirstByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaLessThanEqualAndActivoTrueOrderByFechaDesc(tenant,empresa,origen,destino.getId(),fecha);if(direct.isPresent())return Optional.of(direct.get().getTasa());
        var reverse=tasas.findFirstByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaLessThanEqualAndActivoTrueOrderByFechaDesc(tenant,empresa,destino.getId(),origen,fecha);return reverse.filter(x->x.getTasa().signum()!=0).map(x->BigDecimal.ONE.divide(x.getTasa(),8,RoundingMode.HALF_UP));}
    private static void merge(Map<String,BigDecimal> amounts,String currency,BigDecimal amount){amounts.merge(currency,amount,BigDecimal::add);}
    private static boolean isZero(BigDecimal amount){return amount==null||amount.signum()==0;}
    private static Map<String,BigDecimal> immutable(Map<String,BigDecimal> amounts){return Collections.unmodifiableMap(new TreeMap<>(amounts));}
    private CuentasPagarConsultaRepository.Filtros filtros(Consulta c){var p=TenantContext.principalActual();return new CuentasPagarConsultaRepository.Filtros(p.tenantId(),p.empresaId(),c.al(),clean(c.buscar()),c.proveedor(),c.moneda(),c.sucursal(),p.accesoTodasSucursales(),p.sucursalIds(),defaultValue(c.estado(),"PENDIENTES"),defaultValue(c.situacion(),"TODAS"),c.venceDesde(),c.venceHasta());}
    private void validar(Consulta c,int pagina,int tamano){if(c==null||c.al()==null)throw new ReglaNegocioException("La fecha de corte es obligatoria.");if(c.al().isAfter(LocalDate.now()))throw new ReglaNegocioException("La fecha de corte no puede ser futura.");if(pagina<0||!PAGE_SIZES.contains(tamano))throw new IllegalArgumentException("Paginación inválida.");if(c.venceDesde()!=null&&c.venceHasta()!=null&&c.venceDesde().isAfter(c.venceHasta()))throw new ReglaNegocioException("El vencimiento desde no puede ser posterior al vencimiento hasta.");UUID empresa=EmpresaContext.requerirEmpresaId();if(c.moneda()!=null&&monedas.findByIdAndEmpresaId(c.moneda(),empresa).isEmpty())throw new ReglaNegocioException("La moneda no es válida.");if(c.sucursal()!=null){var s=sucursales.findByIdAndEmpresaId(c.sucursal(),empresa).orElseThrow(()->new ReglaNegocioException("La sucursal no es válida."));if(!EmpresaContext.permiteSucursal(s.getId()))throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");}}
    private static String clean(String x){return x==null?"":x.trim();}private static String defaultValue(String x,String d){return x==null||x.isBlank()?d:x;}
    public record Consulta(LocalDate al,String buscar,UUID proveedor,UUID moneda,UUID sucursal,String estado,
            String situacion,LocalDate venceDesde,LocalDate venceHasta) {}
}
