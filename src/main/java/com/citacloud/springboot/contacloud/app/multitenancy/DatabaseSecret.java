package com.citacloud.springboot.contacloud.app.multitenancy;
public record DatabaseSecret(String jdbcUrl,String username,String password) {}
