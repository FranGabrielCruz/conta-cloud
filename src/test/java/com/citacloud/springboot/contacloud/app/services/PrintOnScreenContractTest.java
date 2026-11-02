package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PrintOnScreenContractTest {
    final JsonMapper json=JsonMapper.builder().build();
    @ParameterizedTest @ValueSource(booleans={true,false})
    void contratoCanonicoYAliasDeEntradaConservanElModo(boolean modo){
        var input=new CrearAgenteImpresionInput("Agente",UUID.randomUUID(),List.of(UUID.randomUUID()),modo);
        String serialized=json.writeValueAsString(input);
        assertThat(serialized).contains("\"printOnScreen\":"+modo).doesNotContain("showPdfOnPrint","mostrarPdfAlImprimir");
        for(String nombre:List.of("printOnScreen","showPdfOnPrint","mostrarPdfAlImprimir")){
            var parsed=json.readValue(serialized.replace("printOnScreen",nombre),CrearAgenteImpresionInput.class);
            assertThat(parsed.printOnScreen()).isEqualTo(modo);
            String edit=serialized.replace("printOnScreen",nombre).replace("}",",\"version\":7}");
            assertThat(json.readValue(edit,EditarAgenteImpresionInput.class).printOnScreen()).isEqualTo(modo);
        }
        var salida=new SolicitudImpresionResultadoDto(modo?null:UUID.randomUUID(),modo);
        assertThat(json.writeValueAsString(salida)).contains("\"printOnScreen\":"+modo).doesNotContain("mostrarPdfAlImprimir","showPdfOnPrint");
    }
}
