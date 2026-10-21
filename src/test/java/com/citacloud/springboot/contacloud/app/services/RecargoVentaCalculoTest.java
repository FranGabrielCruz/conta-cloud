package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecargoVentaCalculoTest {
 @Test void porcentajeSeDistribuyePorLinea(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();var r=RecargoVentaCalculo.calcular(AlcanceRecargoVenta.SALE,TipoRecargoVenta.PERCENTAGE,new BigDecimal("5"),List.of(),List.of(linea(a,"1000"),linea(b,"500")));assertEquals(new BigDecimal("75.0000"),r.montoRecargo());assertEquals(new BigDecimal("50.0000"),r.asignaciones().get(0).recargo());assertEquals(new BigDecimal("25.0000"),r.asignaciones().get(1).recargo());}
 @Test void montoReconciliaResiduo(){UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();var r=RecargoVentaCalculo.calcular(AlcanceRecargoVenta.LINE,TipoRecargoVenta.AMOUNT,new BigDecimal("100"),List.of(a,b,c),List.of(linea(a,"1"),linea(b,"1"),linea(c,"1")));assertEquals(new BigDecimal("100.0000"),r.asignaciones().stream().map(RecargoVentaCalculo.Asignacion::recargo).reduce(BigDecimal.ZERO,BigDecimal::add));}
 @Test void bloqueaProductoNoElegible(){UUID a=UUID.randomUUID();assertThrows(ReglaNegocioException.class,()->RecargoVentaCalculo.calcular(AlcanceRecargoVenta.LINE,TipoRecargoVenta.AMOUNT,BigDecimal.ONE,List.of(a),List.of(new RecargoVentaCalculo.Linea(a,a,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,false))));}
 @Test void recargoExentoConservaImpuestoDelProducto(){UUID a=UUID.randomUUID();var r=RecargoVentaCalculo.calcular(AlcanceRecargoVenta.SALE,TipoRecargoVenta.AMOUNT,new BigDecimal("20"),List.of(),List.of(new RecargoVentaCalculo.Linea(a,a,new BigDecimal("100"),new BigDecimal("18"),BigDecimal.ZERO,true)));assertEquals(new BigDecimal("18.0000"),r.asignaciones().get(0).impuesto());assertEquals(new BigDecimal("138.0000"),r.asignaciones().get(0).total());}
 private static RecargoVentaCalculo.Linea linea(UUID id,String base){return new RecargoVentaCalculo.Linea(id,id,new BigDecimal(base),new BigDecimal("18"),new BigDecimal("18"),true);}
}
