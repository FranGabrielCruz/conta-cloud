package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LineaOrdenCompraInput;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderServiceValidationTest {
    @Test void rechazaElMismoProductoEnVariasLineas(){
        UUID productId=UUID.randomUUID();
        var first=line(productId,"Primera línea");
        var duplicate=line(productId,"Segunda línea");

        assertThatThrownBy(()->PurchaseOrderService.validateUniqueProducts(List.of(first,duplicate)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El producto ya existe en la orden de compra.");
    }

    @Test void permiteProductosDiferentes(){
        assertThatCode(()->PurchaseOrderService.validateUniqueProducts(List.of(
            line(UUID.randomUUID(),"Producto uno"),line(UUID.randomUUID(),"Producto dos"))))
            .doesNotThrowAnyException();
    }

    private static LineaOrdenCompraInput line(UUID productId,String description){
        return new LineaOrdenCompraInput(productId,description,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,null);
    }
}
