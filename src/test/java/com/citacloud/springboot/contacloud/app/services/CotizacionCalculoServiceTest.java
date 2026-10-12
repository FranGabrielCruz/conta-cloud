package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LineaCotizacionInput;
import com.citacloud.springboot.contacloud.app.models.TipoDescuentoCotizacion;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CotizacionCalculoServiceTest {
    private final CotizacionCalculoService service=new CotizacionCalculoService();

    @Test void calculaElEjemploFuncionalConItbis(){
        var product=service.calcular(line("Laptop Dell","2","25000",TipoDescuentoCotizacion.MONTO,"0"),"ITBIS",new BigDecimal("18"));
        var installation=service.calcular(line("Instalación","1","5000",TipoDescuentoCotizacion.MONTO,"0"),"ITBIS",new BigDecimal("18"));
        var totals=service.totales(List.of(product,installation));
        assertEquals(new BigDecimal("55000.0000"),totals.subtotal());
        assertEquals(new BigDecimal("9900.0000"),totals.impuesto());
        assertEquals(new BigDecimal("64900.0000"),totals.total());
    }

    @Test void admiteDescuentoPorcentualSinPerderPrecision(){
        var result=service.calcular(line("Servicio","2","1000",TipoDescuentoCotizacion.PORCENTAJE,"10"),"ITBIS",new BigDecimal("18"));
        assertEquals(new BigDecimal("200.0000"),result.descuento());
        assertEquals(new BigDecimal("324.0000"),result.impuesto());
        assertEquals(new BigDecimal("2124.0000"),result.total());
    }

    @Test void rechazaCantidadCeroYDescuentoMayorAlSubtotal(){
        assertThrows(ReglaNegocioException.class,()->service.calcular(line("Servicio","0","100",TipoDescuentoCotizacion.MONTO,"0"),null,BigDecimal.ZERO));
        assertThrows(ReglaNegocioException.class,()->service.calcular(line("Servicio","1","100",TipoDescuentoCotizacion.MONTO,"101"),null,BigDecimal.ZERO));
    }

    private static LineaCotizacionInput line(String description,String quantity,String price,TipoDescuentoCotizacion type,String discount){
        return new LineaCotizacionInput(java.util.UUID.randomUUID(),description,new BigDecimal(quantity),new BigDecimal(price),type,new BigDecimal(discount),null);
    }
}
