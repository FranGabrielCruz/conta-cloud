package com.citacloud.springboot.contacloud.app.dto;
import java.util.List;
public record PaginaLineasConteoFisicoDto(List<ConteoFisicoDto.Linea> items,long total,long contados,long pendientes,int pagina,int tamano,int totalPaginas){}
