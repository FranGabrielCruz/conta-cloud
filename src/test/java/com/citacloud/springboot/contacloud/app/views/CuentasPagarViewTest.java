package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ResumenCuentasPagarDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CuentasPagarViewTest {
    @Test void muestraLosImportesPorMonedaCuandoElIndicadorNoSePuedeConsolidar(){
        var resumen=resumen(null);

        assertThat(CuentasPagarView.display(null,resumen,Map.of(
            "DOP",new BigDecimal("6365.80"),"USD",new BigDecimal("100.00"))))
            .isEqualTo("DOP 6,365.80 · USD 100.00");
    }

    @Test void muestraElIndicadorConsolidadoCuandoExisteConversion(){
        var resumen=resumen(new BigDecimal("35565.80"));

        assertThat(CuentasPagarView.display(resumen.pendiente(),resumen,Map.of("DOP",new BigDecimal("35565.80"))))
            .isEqualTo("RD$ 35,565.80");
    }

    private static ResumenCuentasPagarDto resumen(BigDecimal pendiente){
        return new ResumenCuentasPagarDto(pendiente,null,null,null,null,null,"DOP","RD$",false,
            Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),List.of(),OffsetDateTime.now());
    }
}
