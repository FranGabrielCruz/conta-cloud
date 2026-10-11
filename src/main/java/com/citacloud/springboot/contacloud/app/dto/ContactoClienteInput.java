package com.citacloud.springboot.contacloud.app.dto;

public record ContactoClienteInput(String nombre,String cargo,String telefono,String correo,boolean principal,long version) {}
