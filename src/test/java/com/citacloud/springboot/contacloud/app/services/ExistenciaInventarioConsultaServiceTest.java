package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ExistenciaInventarioConsultaServiceTest {
    @Test void validaFiltrosYPaginacion(){
        assertThat(ExistenciaInventarioConsultaService.normalizarStock("NEGATIVA")).isEqualTo("NEGATIVA");
        assertThat(ExistenciaInventarioConsultaService.estado("TODOS")).isNull();
        assertThat(ExistenciaInventarioConsultaService.estado("INACTIVOS")).isFalse();
        assertThatThrownBy(()->ExistenciaInventarioConsultaService.validar(0,7,"TODOS"))
            .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(()->ExistenciaInventarioConsultaService.normalizarStock("DISPONIBLE"))
            .isInstanceOf(ReglaNegocioException.class);
    }

    @Test void consultaTipaFiltrosOpcionalesParaPostgresql() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/citacloud/springboot/contacloud/app/repositories/ExistenciaInventarioConsultaRepository.java"));
        assertThat(source).contains("CAST(:sucursal AS uuid)","CAST(:almacen AS uuid)",
            "CAST(:categoria AS uuid)","CAST(:activo AS boolean)");
    }
}
