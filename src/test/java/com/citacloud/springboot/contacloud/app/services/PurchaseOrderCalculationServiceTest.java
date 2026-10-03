package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LineaOrdenCompraInput;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PurchaseOrderCalculationServiceTest {
    private final PurchaseOrderCalculationService service=new PurchaseOrderCalculationService();

    @Test void calculaDescuentoImpuestoYTotalConPrecisionDecimal(){var line=service.calculate(
        new LineaOrdenCompraInput("Servicio",new BigDecimal("3"),new BigDecimal("100.50"),new BigDecimal("1.50"),null),
        "ITBIS",new BigDecimal("18"));
        assertThat(line.grossSubtotal()).isEqualByComparingTo("301.5000");
        assertThat(line.taxableBase()).isEqualByComparingTo("300.0000");
        assertThat(line.taxAmount()).isEqualByComparingTo("54.0000");
        assertThat(line.total()).isEqualByComparingTo("354.0000");
    }

    @Test void sumaTotalesDeVariasLineas(){var one=service.calculate(new LineaOrdenCompraInput("Uno",BigDecimal.ONE,
        new BigDecimal("100"),BigDecimal.ZERO,null),null,BigDecimal.ZERO);var two=service.calculate(new LineaOrdenCompraInput(
        "Dos",new BigDecimal("2"),new BigDecimal("50"),new BigDecimal("10"),null),"ITBIS",new BigDecimal("18"));
        var totals=service.totals(List.of(one,two));assertThat(totals.subtotal()).isEqualByComparingTo("200.0000");
        assertThat(totals.discount()).isEqualByComparingTo("10.0000");assertThat(totals.tax()).isEqualByComparingTo("16.2000");
        assertThat(totals.total()).isEqualByComparingTo("206.2000");}

    @Test void rechazaCantidadCeroYDescuentoMayorAlSubtotal(){assertThatThrownBy(()->service.calculate(
        new LineaOrdenCompraInput("Línea",BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO,null),null,BigDecimal.ZERO))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cantidad");assertThatThrownBy(()->service.calculate(
        new LineaOrdenCompraInput("Línea",BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("11"),null),null,BigDecimal.ZERO))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("descuento");}

    @Test void requiereAlMenosUnaLinea(){assertThatThrownBy(()->service.totals(List.of()))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos una línea");}
}
