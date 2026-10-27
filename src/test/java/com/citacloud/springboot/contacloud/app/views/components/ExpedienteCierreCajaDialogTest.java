package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.CierreCajaDto;
import com.citacloud.springboot.contacloud.app.models.ResultadoRecuentoCaja;
import com.citacloud.springboot.contacloud.app.services.ExpedienteCierreCajaService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExpedienteCierreCajaDialogTest {
    @Test void cierreEsInicialYConservaIdentificadoresAlCrearExpediente(){
        UUID cierre=UUID.randomUUID(),sesion=UUID.randomUUID();CierreCajaDto dto=dto(cierre,sesion);
        ExpedienteCierreCajaDialog dialog=new ExpedienteCierreCajaDialog(dto,mock(ExpedienteCierreCajaService.class),
            false,false,false,false,false,id->{},id->{},id->{},id->{},id->{});
        assertThat(ExpedienteCierreCajaDialog.ordenPestanas()).containsExactly("Cierre","Ventas","Cobros","Movimientos","Recuentos");
        assertThat(dialog.cierreEsPestanaInicial()).isTrue();assertThat(dialog.cierreId()).isEqualTo(cierre);assertThat(dialog.sesionId()).isEqualTo(sesion);
    }

    private static CierreCajaDto dto(UUID cierre,UUID sesion){BigDecimal cero=BigDecimal.ZERO;OffsetDateTime ahora=OffsetDateTime.now();
        return new CierreCajaDto(cierre,"CIE-000001",sesion,"TUR-1","Caja","Sucursal","DOP","RD$",ahora,ahora,
            "Admin","Admin",cero,cero,cero,cero,cero,cero,cero,cero,cero,cero,cero,cero,ResultadoRecuentoCaja.BALANCED,null,List.of(),List.of());}
}
