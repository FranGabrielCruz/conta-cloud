package com.citacloud.springboot.contacloud.app.repositories;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Types;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CuentasPagarConsultaRepositoryTest {
    private static final LocalDate AL=LocalDate.of(2026,10,11);

    @Test void distingueSituacionesPorFechaDeVencimiento(){
        assertThat(CuentasPagarConsultaRepository.situacion(LocalDate.of(2026,10,1),AL)).isEqualTo("Vencida · 10 días");
        assertThat(CuentasPagarConsultaRepository.situacion(AL,AL)).isEqualTo("Vence hoy");
        assertThat(CuentasPagarConsultaRepository.situacion(LocalDate.of(2026,10,20),AL)).isEqualTo("Vence en 9 días");
    }

    @Test void distingueEstadoFinancieroDelDocumento(){
        assertThat(CuentasPagarConsultaRepository.estado(BigDecimal.ZERO,new BigDecimal("100"))).isEqualTo("PENDIENTE");
        assertThat(CuentasPagarConsultaRepository.estado(new BigDecimal("40"),new BigDecimal("60"))).isEqualTo("PARCIALMENTE PAGADA");
        assertThat(CuentasPagarConsultaRepository.estado(new BigDecimal("100"),BigDecimal.ZERO)).isEqualTo("PAGADA");
    }

    @Test void tipaLosFiltrosOpcionalesAunqueNoTenganValor(){
        var repository=new CuentasPagarConsultaRepository(null);
        var filtros=new CuentasPagarConsultaRepository.Filtros(UUID.randomUUID(),UUID.randomUUID(),AL,"",
            null,null,null,true,Set.of(),"PENDIENTES","TODAS",null,null);
        var parameters=repository.params(filtros,null);

        assertThat(parameters.getSqlType("proveedor")).isEqualTo(Types.OTHER);
        assertThat(parameters.getSqlType("moneda")).isEqualTo(Types.OTHER);
        assertThat(parameters.getSqlType("sucursal")).isEqualTo(Types.OTHER);
        assertThat(parameters.getSqlType("venceDesde")).isEqualTo(Types.DATE);
        assertThat(parameters.getSqlType("venceHasta")).isEqualTo(Types.DATE);
    }
}
