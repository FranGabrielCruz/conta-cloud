package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SecuenciaService {
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    public SecuenciaService(JdbcTemplate jdbc, AuditoriaService auditoria) { this.jdbc = jdbc; this.auditoria = auditoria; }

    @Transactional @PreAuthorize("hasAuthority('SECUENCIA_EDITAR')")
    public String siguiente(UUID secuenciaId) {
        UUID empresaId = TenantContext.requerirEmpresaId();
        var datos = jdbc.query("SELECT valor_actual,longitud,COALESCE(serie,'') FROM secuencias " +
                "WHERE id=? AND empresa_id=? AND activo=TRUE FOR UPDATE", rs -> {
            if (!rs.next()) throw new RecursoNoEncontradoException("Secuencia no encontrada");
            return new DatosSecuencia(rs.getLong(1), rs.getInt(2), rs.getString(3));
        }, secuenciaId, empresaId);
        long siguiente = Math.addExact(datos.valorActual(), 1);
        jdbc.update("UPDATE secuencias SET valor_actual=? WHERE id=? AND empresa_id=?", siguiente, secuenciaId, empresaId);
        auditoria.registrar("GENERAR_NUMERO", "Secuencia", secuenciaId, "{}");
        return datos.serie() + String.format("%0" + datos.longitud() + "d", siguiente);
    }
    private record DatosSecuencia(long valorActual, int longitud, String serie) {}
}
