package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.AlcanceDescuentoVenta;
import com.citacloud.springboot.contacloud.app.models.TipoDescuentoVenta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DescuentoVentaCalculoTest {
    private final UUID productoA=UUID.randomUUID();
    private final UUID productoB=UUID.randomUUID();

    @Test
    void aplicaPorcentajeAntesDelImpuesto() {
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.SALE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("10"),List.of(),List.of(linea(productoA,"100.00","18",true)));

        assertThat(resultado.montoDescuento()).isEqualByComparingTo("10.0000");
        assertThat(resultado.asignaciones().getFirst().baseImponible()).isEqualByComparingTo("90.0000");
        assertThat(resultado.asignaciones().getFirst().impuesto()).isEqualByComparingTo("16.2000");
        assertThat(resultado.asignaciones().getFirst().total()).isEqualByComparingTo("106.2000");
    }

    @Test
    void distribuyeMontoFijoYReconciliaElUltimoResiduo() {
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.SALE,TipoDescuentoVenta.AMOUNT,
                new BigDecimal("10.00"),List.of(),List.of(
                        linea(productoA,"33.33","18",true),linea(productoB,"66.67","0",true)));

        assertThat(resultado.asignaciones()).extracting(DescuentoVentaCalculo.Asignacion::descuento)
                .containsExactly(new BigDecimal("3.3330"),new BigDecimal("6.6670"));
        assertThat(resultado.asignaciones()).extracting(DescuentoVentaCalculo.Asignacion::baseImponible)
                .containsExactly(new BigDecimal("29.9970"),new BigDecimal("60.0030"));
        assertThat(resultado.asignaciones().stream().map(DescuentoVentaCalculo.Asignacion::descuento).reduce(BigDecimal.ZERO,BigDecimal::add))
                .isEqualByComparingTo("10.0000");
    }

    @Test
    void excluyeProductosQueNoPermitenDescuento() {
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.SALE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("10"),List.of(),List.of(
                        linea(productoA,"100","18",true),linea(productoB,"200","18",false)));

        assertThat(resultado.baseElegible()).isEqualByComparingTo("100.0000");
        assertThat(resultado.asignaciones().get(1).descuento()).isZero();
        assertThat(resultado.asignaciones().get(1).baseImponible()).isEqualByComparingTo("200.0000");
    }

    @Test
    void aplicaDescuentoSoloALasLineasSeleccionadas() {
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.LINE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("25"),List.of(productoB),List.of(
                        linea(productoA,"100","18",true),linea(productoB,"40","0",true)));

        assertThat(resultado.asignaciones().getFirst().descuento()).isZero();
        assertThat(resultado.asignaciones().get(1).descuento()).isEqualByComparingTo("10.0000");
    }

    @Test
    void aplicaPorcentajeAVariosProductos() {
        UUID lineaA=UUID.randomUUID(),lineaB=UUID.randomUUID(),lineaC=UUID.randomUUID();
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.LINE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("10"),List.of(lineaA,lineaB),List.of(
                        linea(lineaA,productoA,"1000","18",true),
                        linea(lineaB,productoB,"500","0",true),
                        linea(lineaC,UUID.randomUUID(),"300","18",true)));

        assertThat(resultado.montoDescuento()).isEqualByComparingTo("150.0000");
        assertThat(resultado.asignaciones()).extracting(DescuentoVentaCalculo.Asignacion::descuento)
                .containsExactly(new BigDecimal("100.0000"),new BigDecimal("50.0000"),new BigDecimal("0.0000"));
    }

    @Test
    void distribuyeMontoEntreVariasLineasYReconcilia() {
        UUID lineaA=UUID.randomUUID(),lineaB=UUID.randomUUID();
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.LINE,TipoDescuentoVenta.AMOUNT,
                new BigDecimal("300"),List.of(lineaA,lineaB),List.of(
                        linea(lineaA,productoA,"1000","18",true),linea(lineaB,productoB,"500","0",true)));

        assertThat(resultado.asignaciones()).extracting(DescuentoVentaCalculo.Asignacion::descuento)
                .containsExactly(new BigDecimal("200.0000"),new BigDecimal("100.0000"));
    }

    @Test
    void distingueDosLineasDelMismoProducto() {
        UUID lineaA=UUID.randomUUID(),lineaB=UUID.randomUUID();
        var resultado=DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.LINE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("10"),List.of(lineaA),List.of(
                        linea(lineaA,productoA,"500","18",true),linea(lineaB,productoA,"450","18",true)));

        assertThat(resultado.asignaciones()).extracting(DescuentoVentaCalculo.Asignacion::descuento)
                .containsExactly(new BigDecimal("50.0000"),new BigDecimal("0.0000"));
    }

    @Test
    void rechazaSeleccionVacia() {
        assertThatThrownBy(()->DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.LINE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("10"),List.of(),List.of(linea(productoA,"100","18",true))))
                .hasMessage("Seleccione al menos un producto.");
    }

    @Test
    void rechazaLineaSeleccionadaNoElegible() {
        assertThatThrownBy(()->DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.LINE,TipoDescuentoVenta.PERCENTAGE,
                new BigDecimal("10"),List.of(productoA),List.of(linea(productoA,"100","18",false))))
                .hasMessage("Los productos seleccionados no admiten descuentos.");
    }

    @Test
    void rechazaDescuentoSuperiorALaBase() {
        assertThatThrownBy(()->DescuentoVentaCalculo.calcular(AlcanceDescuentoVenta.SALE,TipoDescuentoVenta.AMOUNT,
                new BigDecimal("100.01"),List.of(),List.of(linea(productoA,"100","18",true))))
                .hasMessage("El descuento no puede superar la base elegible.");
    }

    private static DescuentoVentaCalculo.Linea linea(UUID id,String bruto,String impuesto,boolean elegible){
        return new DescuentoVentaCalculo.Linea(id,new BigDecimal(bruto),new BigDecimal(impuesto),elegible);
    }

    private static DescuentoVentaCalculo.Linea linea(UUID lineaId,UUID productoId,String bruto,String impuesto,boolean elegible){
        return new DescuentoVentaCalculo.Linea(lineaId,productoId,new BigDecimal(bruto),new BigDecimal(impuesto),elegible);
    }
}
