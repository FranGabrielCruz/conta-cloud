package com.citacloud.springboot.contacloud.app.models;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class FacturaVentaTest {
    @Test void calculaCambioSinRegistrarElEfectivoRecibidoComoIngreso(){
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID(),moneda=UUID.randomUUID();
        FacturaVenta f=new FacturaVenta(tenant,empresa,"FAC-000001",LocalDate.of(2026,10,17),usuario,UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"Consumidor Final",null,"Admin",
            moneda,"DOP","RD$",null,new BigDecimal("100.0000"),new BigDecimal("18.0000"),new BigDecimal("118.0000"),
            new BigDecimal("120.0000"),UUID.randomUUID());
        assertEquals(new BigDecimal("2.0000"),f.getCambio());assertEquals(new BigDecimal("118.0000"),f.getTotal());
    }
}
