package com.citacloud.springboot.contacloud.app.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.UUID;

@Service
public class MovimientoCajaNumeroService {
    private final JdbcTemplate jdbc;
    public MovimientoCajaNumeroService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String siguiente(UUID tenant,UUID empresa){Long valor=jdbc.queryForObject("""
        INSERT INTO cash_movement_sequence(tenant_id,empresa_id,current_value) VALUES(?,?,1)
        ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET current_value=cash_movement_sequence.current_value+1
        RETURNING current_value
        """,Long.class,tenant,empresa);return "MOV-"+String.format(Locale.ROOT,"%06d",valor);}
}
