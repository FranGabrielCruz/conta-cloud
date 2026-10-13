package com.citacloud.springboot.contacloud.app.dto;
import org.springframework.data.domain.Page;
public record PaginaMovimientosInventarioDto(Page<MovimientoInventarioDto> pagina,ResumenMovimientosInventarioDto resumen) {}
