package com.citacloud.springboot.contacloud.app.dto;

public record ArchivoPdfDto(String nombreArchivo, byte[] contenido) {
    public ArchivoPdfDto {
        contenido = contenido.clone();
    }

    @Override
    public byte[] contenido() {
        return contenido.clone();
    }
}
