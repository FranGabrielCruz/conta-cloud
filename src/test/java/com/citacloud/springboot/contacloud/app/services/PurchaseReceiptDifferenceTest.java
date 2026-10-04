package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.mappers.RecepcionCompraMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReceiptDifferenceTest {
    @Test
    void mapperExponeSobrerecepcionSinModificarCantidadOrdenada() {
        UUID tenant = UUID.randomUUID(), empresa = UUID.randomUUID(), usuario = UUID.randomUUID();
        UUID lineaOrden = UUID.randomUUID(), producto = UUID.randomUUID();
        RecepcionCompra receipt = new RecepcionCompra(tenant, empresa, "REC-000001", UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), null, null, usuario);
        LineaRecepcionCompra line = new LineaRecepcionCompra(tenant, empresa, lineaOrden, producto, "P-1",
            "Laptop", "Unidad", new BigDecimal("12.0000"), OrigenLineaRecepcion.ORDER_LINE,
            TipoDiferenciaRecepcion.OVER_RECEIPT, 1);
        receipt.reemplazarLineas(java.util.List.of(line));

        var dto = new RecepcionCompraMapper().toDto(receipt,
            Map.of(lineaOrden, new BigDecimal("10.0000")), Map.of(lineaOrden, BigDecimal.ZERO));

        assertThat(dto.lineas().getFirst().diferencia()).isEqualTo(TipoDiferenciaRecepcion.OVER_RECEIPT);
        assertThat(dto.lineas().getFirst().exceso()).isEqualByComparingTo("2.0000");
        assertThat(dto.lineas().getFirst().ordenada()).isEqualByComparingTo("10.0000");
    }

    @Test
    void lineaManualFueraDeOrdenConservaOrigenYDiferenciaExplicitos() {
        LineaRecepcionCompra line = new LineaRecepcionCompra(UUID.randomUUID(), UUID.randomUUID(), null,
            UUID.randomUUID(), "P-2", "Teclado", "Unidad", new BigDecimal("5.0000"),
            OrigenLineaRecepcion.MANUAL, TipoDiferenciaRecepcion.UNORDERED_PRODUCT, 1);

        assertThat(line.getLineaOrdenId()).isNull();
        assertThat(line.getOrigen()).isEqualTo(OrigenLineaRecepcion.MANUAL);
        assertThat(line.getDiferencia()).isEqualTo(TipoDiferenciaRecepcion.UNORDERED_PRODUCT);
    }
}
