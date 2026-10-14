package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ExistenciaInventarioPdfServiceTest {
    @Test void generaPdfConIdentidadFiltrosYExistencias() {
        var data=new ReporteExistenciaInventarioData("DEMO","Empresa Demo","124543678","Santo Domingo","8095551234","demo@example.com",null,
            "13/10/2026 21:00","naranja","Todas","Todos","Alimentos","Activos","Todos",2,true,true,"Administrador",OffsetDateTime.parse("2026-10-13T21:00:00-04:00"),
            List.of(new LineaReporteExistenciaInventario("Naranja","PRD-000001","Unidad","Principal","Almacén Principal","125.00"),new LineaReporteExistenciaInventario("Arroz","PRD-000002","Libra","Principal","Almacén Principal","1,000.00")));
        byte[] pdf=new ExistenciaInventarioPdfService().generar(data);
        assertThat(pdf).startsWith("%PDF".getBytes()).hasSizeGreaterThan(1000);
    }
}
