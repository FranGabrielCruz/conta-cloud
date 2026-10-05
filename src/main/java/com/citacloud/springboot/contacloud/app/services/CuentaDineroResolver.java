package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaDineroOpcionDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import java.util.*;

@Service
public class CuentaDineroResolver {
    private final CashRegisterRepository cajas;
    private final BankAccountRepository cuentas;
    public CuentaDineroResolver(CashRegisterRepository cajas,BankAccountRepository cuentas){this.cajas=cajas;this.cuentas=cuentas;}

    public List<CuentaDineroOpcionDto> opciones(){
        var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();
        List<CuentaDineroOpcionDto> resultado=new ArrayList<>();
        cajas.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),empresaId).stream()
            .filter(c->EmpresaContext.permiteSucursal(c.getSucursalId()))
            .map(c->new CuentaDineroOpcionDto(c.getId(),TipoCuentaDinero.CASH_REGISTER,
                "Caja · "+c.getNombre()+" · "+c.getSucursal().getNombre(),c.getMonedaId(),c.getMoneda().getCodigoIso()))
            .forEach(resultado::add);
        cuentas.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByBancoNombreAscNombreCuentaAsc(p.tenantId(),empresaId)
            .stream().map(c->new CuentaDineroOpcionDto(c.getId(),TipoCuentaDinero.BANK_ACCOUNT,
                "Cuenta bancaria · "+c.getBancoNombre()+" · "+c.getNombreCuenta(),c.getMonedaId(),c.getMoneda().getCodigoIso()))
            .forEach(resultado::add);
        return List.copyOf(resultado);
    }

    public CuentaResuelta resolver(TipoCuentaDinero tipo,UUID id){
        if(tipo==null||id==null)throw new ReglaNegocioException("Selecciona una cuenta válida.");
        var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();
        if(tipo==TipoCuentaDinero.CASH_REGISTER){
            Caja c=cajas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),empresaId).filter(Caja::isActivo)
                .orElseThrow(()->new ReglaNegocioException("La caja seleccionada no es válida o está inactiva."));
            if(!EmpresaContext.permiteSucursal(c.getSucursalId()))
                throw new ReglaNegocioException("La caja seleccionada no está autorizada para el usuario.");
            return new CuentaResuelta(tipo,id,c.getMonedaId(),c.getMoneda(),c,null,c.getNombre());
        }
        CuentaBancaria c=cuentas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),empresaId).filter(CuentaBancaria::isActivo)
            .orElseThrow(()->new ReglaNegocioException("La cuenta bancaria seleccionada no es válida o está inactiva."));
        return new CuentaResuelta(tipo,id,c.getMonedaId(),c.getMoneda(),null,c,c.getBancoNombre()+" · "+c.getNombreCuenta());
    }

    public List<CuentaDineroOpcionDto> buscarFuentesPago(TipoCuentaDinero tipo,UUID monedaId,String filtro,
            int offset,int limit){
        if(tipo==null||monedaId==null||offset<0||limit<1||limit>50)
            throw new ReglaNegocioException("Parámetros de búsqueda de la fuente de pago inválidos.");
        var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();
        String text=filtro==null?"":filtro.trim();
        if(tipo==TipoCuentaDinero.CASH_REGISTER){
            Set<UUID> branches=p.sucursalIds().isEmpty()?Set.of(new UUID(0,0)):p.sucursalIds();
            return cajas.buscarFuentesPago(p.tenantId(),empresaId,monedaId,text,p.accesoTodasSucursales(),branches,
                PageRequest.of(offset/limit,limit,Sort.by("nombre")))
                .stream()
                .map(c->new CuentaDineroOpcionDto(c.getId(),tipo,"Caja · "+c.getNombre()+" · "+c.getSucursal().getNombre(),
                    c.getMonedaId(),c.getMoneda().getCodigoIso())).toList();
        }
        return cuentas.buscar(p.tenantId(),empresaId,text,monedaId,true,
            PageRequest.of(offset/limit,limit,Sort.by("bancoNombre","nombreCuenta"))).stream()
            .map(c->new CuentaDineroOpcionDto(c.getId(),tipo,"Cuenta bancaria · "+c.getBancoNombre()+" · "+c.getNombreCuenta(),
                c.getMonedaId(),c.getMoneda().getCodigoIso())).toList();
    }

    public record CuentaResuelta(TipoCuentaDinero tipo,UUID id,UUID monedaId,Moneda moneda,Caja caja,
        CuentaBancaria cuentaBancaria,String nombre) {}
}
