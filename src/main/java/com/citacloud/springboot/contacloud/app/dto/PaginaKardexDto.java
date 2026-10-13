package com.citacloud.springboot.contacloud.app.dto;
import org.springframework.data.domain.Page;
public record PaginaKardexDto(Page<MovimientoInventarioDto> pagina,ResumenKardexDto resumen) {}
