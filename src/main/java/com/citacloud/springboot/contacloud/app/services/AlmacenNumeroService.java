package com.citacloud.springboot.contacloud.app.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AlmacenNumeroService {
    private final JdbcTemplate jdbc;
    public AlmacenNumeroService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String siguiente(UUID tenant,UUID empresa){
        Long number=jdbc.queryForObject("""
            INSERT INTO warehouse_sequence(tenant_id,empresa_id,last_number) VALUES(?,?,1)
            ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number=warehouse_sequence.last_number+1
            RETURNING last_number
            """,Long.class,tenant,empresa);
        return "ALM-"+String.format("%06d",number);
    }
}
