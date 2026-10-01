package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaDineroOpcionDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
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

    public record CuentaResuelta(TipoCuentaDinero tipo,UUID id,UUID monedaId,Moneda moneda,Caja caja,
        CuentaBancaria cuentaBancaria,String nombre) {}
}
