package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.TasaCambioRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class CuentasPagarServiceTest {
    @Test void utilizaLaTasaActivaMasRecienteAnteriorAlCorte(){
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usd=UUID.randomUUID(),dop=UUID.randomUUID();
        LocalDate corte=LocalDate.of(2026,10,11),fechaTasa=LocalDate.of(2026,10,10);
        TasaCambio tasa=new TasaCambio(tenant,empresa,usd,dop,new BigDecimal("63.25000000"),fechaTasa,true,UUID.randomUUID());
        TasaCambioRepository tasas=repository((origen,destino)->origen.equals(usd)&&destino.equals(dop)?Optional.of(tasa):Optional.empty());
        Moneda destino=moneda(dop,empresa,"DOP");
        CuentasPagarService service=new CuentasPagarService(null,null,null,null,tasas);

        assertThat(service.factor(usd,destino,corte,tenant,empresa)).contains(new BigDecimal("63.25000000"));
    }

    @Test void noUtilizaUnaTasaFuturaNiDeOtroSentidoSinConvertirla(){
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usd=UUID.randomUUID(),dop=UUID.randomUUID();
        LocalDate corte=LocalDate.of(2026,10,11);
        TasaCambio inversa=new TasaCambio(tenant,empresa,dop,usd,new BigDecimal("0.01581028"),LocalDate.of(2026,10,10),true,UUID.randomUUID());
        TasaCambioRepository tasas=repository((origen,destinoId)->origen.equals(dop)&&destinoId.equals(usd)?Optional.of(inversa):Optional.empty());Moneda destino=moneda(dop,empresa,"DOP");
        CuentasPagarService service=new CuentasPagarService(null,null,null,null,tasas);

        assertThat(service.factor(usd,destino,corte,tenant,empresa).orElseThrow())
            .isEqualByComparingTo(new BigDecimal("63.24998672"));
    }

    private static Moneda moneda(UUID id,UUID empresa,String codigo){Moneda moneda=new Moneda(empresa,codigo,codigo,"$",(short)2);try{var field=Moneda.class.getDeclaredField("id");field.setAccessible(true);field.set(moneda,id);}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}return moneda;}
    private static TasaCambioRepository repository(RateLookup lookup){return (TasaCambioRepository)Proxy.newProxyInstance(
        TasaCambioRepository.class.getClassLoader(),new Class<?>[]{TasaCambioRepository.class},(proxy,method,args)->{
            if(method.getName().equals("findFirstByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaLessThanEqualAndActivoTrueOrderByFechaDesc"))
                return lookup.find((UUID)args[2],(UUID)args[3]);
            if(method.getName().equals("toString"))return "TasaCambioRepositoryTestDouble";
            throw new UnsupportedOperationException(method.getName());});}
    @FunctionalInterface private interface RateLookup{Optional<TasaCambio> find(UUID origen,UUID destino);}
}
