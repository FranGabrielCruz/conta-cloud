package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.AlmacenInput;
import com.citacloud.springboot.contacloud.app.models.TipoAlmacen;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AlmacenServiceTest {
    @Test void normalizaNombreParaEvitarDuplicadosPorMayusculasYEspacios(){
        assertThat(AlmacenService.normalizar("  Almacén   Principal ")).isEqualTo("almacén principal");
    }

    @Test void exigeNombreSucursalYTipo(){
        assertThatThrownBy(()->AlmacenService.validar(new AlmacenInput("",UUID.randomUUID(),TipoAlmacen.GENERAL,null,null,null,false,null)))
            .hasMessage("El nombre es obligatorio.");
        assertThatThrownBy(()->AlmacenService.validar(new AlmacenInput("Principal",null,TipoAlmacen.GENERAL,null,null,null,false,null)))
            .hasMessage("La sucursal es obligatoria.");
        assertThatThrownBy(()->AlmacenService.validar(new AlmacenInput("Principal",UUID.randomUUID(),null,null,null,null,false,null)))
            .hasMessage("El tipo de almacén es obligatorio.");
    }

    @Test void aceptaLosDatosValidosDelAlmacen(){
        var input=new AlmacenInput("  Principal  ",UUID.randomUUID(),TipoAlmacen.GENERAL,null,"  Piso 1  ",null,true,null);
        var result=AlmacenService.validar(input);
        assertThat(result.nombre()).isEqualTo("Principal");
        assertThat(result.nombreNormalizado()).isEqualTo("principal");
        assertThat(result.direccion()).isEqualTo("Piso 1");
    }

    @Test void reservaElAlmacenDeTransitoParaElSistema(){
        assertThatThrownBy(()->AlmacenService.validar(new AlmacenInput("Tránsito",UUID.randomUUID(),TipoAlmacen.TRANSITO,null,null,null,false,null)))
            .hasMessage("El almacén de tránsito es administrado automáticamente por el sistema.");
    }
}
