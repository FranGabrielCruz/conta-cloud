package com.citacloud.springboot.contacloud.app.repositories;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovimientoInventarioConsultaRepositoryTest {

    @Test
    void consultaUsaElNombreRealDeLaSucursal() {
        String sql = MovimientoInventarioConsultaRepository.cte(false);

        assertThat(sql)
                .contains("s.nombre branch_name")
                .contains(":todasSucursales=true OR w.branch_id IN (:sucursalesPermitidas)")
                .doesNotContain("s.name branch_name");
    }

    @Test
    void consultaCompletaNoDependeDeLaPaginacionDelGrid() {
        assertThat(MovimientoInventarioConsultaRepository.generalSql(false))
                .doesNotContain("LIMIT :limit")
                .contains("ORDER BY effective_at DESC");
        assertThat(MovimientoInventarioConsultaRepository.kardexSql(false))
                .doesNotContain("LIMIT :limit")
                .contains("ORDER BY effective_at,created_at");
    }
}
