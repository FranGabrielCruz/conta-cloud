package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.router.Route;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class InventarioExistenciasViewTest {
    @Test void registraRutaYConservaPantallaSoloConsulta() throws Exception {
        assertThat(InventarioExistenciasView.class.getAnnotation(Route.class).value()).isEqualTo("inventario/existencias");
        String source=Files.readString(Path.of("src/main/java/com/citacloud/springboot/contacloud/app/views/InventarioExistenciasView.java"));
        assertThat(source).contains("Ver existencia","Generar PDF","Actualizar")
            .doesNotContain("Ver Kardex","ActionType.NEW","ActionType.EDIT");
    }
}
