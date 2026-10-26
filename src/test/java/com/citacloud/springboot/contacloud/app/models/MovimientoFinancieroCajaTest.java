package com.citacloud.springboot.contacloud.app.models;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MovimientoFinancieroCajaTest {
    @Test void laReversionConservaElOriginalYCreaElMovimientoOpuesto() {
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),caja=UUID.randomUUID(),moneda=UUID.randomUUID();
        UUID sesion=UUID.randomUUID(),usuario=UUID.randomUUID(),concepto=UUID.randomUUID(),originalId=UUID.randomUUID();
        MovimientoFinanciero original=MovimientoFinanciero.manualCaja(tenant,empresa,TipoMovimientoFinanciero.INCOME,
            LocalDate.of(2026,10,26),caja,moneda,new BigDecimal("125.0000"),"MOV-000001",concepto,
            "Reposición de efectivo",null,null,sesion,usuario);
        ReflectionTestUtils.setField(original,"id",originalId);

        MovimientoFinanciero reverso=MovimientoFinanciero.reversionCaja(original,LocalDate.of(2026,10,26),
            "MOV-000002","Registro duplicado",usuario);

        assertThat(original.getTipoMovimiento()).isEqualTo(TipoMovimientoFinanciero.INCOME);
        assertThat(original.getEstado()).isEqualTo(EstadoMovimientoFinanciero.REGISTERED);
        assertThat(reverso.getTipoMovimiento()).isEqualTo(TipoMovimientoFinanciero.EXPENSE);
        assertThat(reverso.getTipoOrigen()).isEqualTo(TipoOrigenMovimiento.REVERSAL);
        assertThat(reverso.getReversionDeMovimientoId()).isEqualTo(originalId);
        assertThat(reverso.getMonto()).isEqualByComparingTo("125.0000");
        assertThat(reverso.getSesionCajaId()).isEqualTo(sesion);
    }
}
