package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditoriaService {
    private final JdbcTemplate jdbc;
    public AuditoriaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void registrar(String accion, String entidad, UUID registroId, String detalleJson) {
        var principal = TenantContext.principalActual();
        registrarPara(principal.empresaId(), principal.usuarioId(), accion, entidad, registroId, detalleJson);
    }

    public void registrarPara(UUID empresaId, UUID usuarioId, String accion, String entidad,
                              UUID registroId, String detalleJson) {
        jdbc.update("INSERT INTO auditoria(empresa_id,usuario_id,accion,entidad,registro_id,detalle) " +
                "VALUES (?,?,?,?,?,CAST(? AS jsonb))", empresaId, usuarioId, accion,
            entidad, registroId, detalleJson == null ? "{}" : detalleJson);
    }
}
