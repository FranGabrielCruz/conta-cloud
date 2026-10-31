package com.citacloud.springboot.contacloud.app.services;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PrintAgentNumberService {
    private final JdbcTemplate jdbc;
    public PrintAgentNumberService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String siguiente(UUID tenant,UUID empresa){Long value=jdbc.queryForObject("""
        INSERT INTO print_agent_sequence(tenant_id,empresa_id,current_value) VALUES (?,?,1)
        ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET current_value=print_agent_sequence.current_value+1
        RETURNING current_value""",Long.class,tenant,empresa);return "PA-%06d".formatted(value);}
}
