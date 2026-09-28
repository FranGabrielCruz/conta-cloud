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

    @Transactional @PreAuthorize("@empresaModuloService.habilitado('SECUENCIAS') and hasAnyAuthority('secuencias.editar','SECUENCIA_EDITAR')")
    public String siguiente(UUID secuenciaId) {
        UUID tenantId = TenantContext.requerirTenantId();
        UUID empresaId = TenantContext.requerirEmpresaId();
        var datos = jdbc.query("SELECT s.valor_actual,s.longitud,c.prefijo,s.numero_final FROM secuencias s " +
                "JOIN tipos_comprobantes_fiscales c ON c.id=s.tipo_comprobante_id AND c.tenant_id=s.tenant_id AND c.empresa_id=s.empresa_id " +
                "WHERE s.id=? AND s.tenant_id=? AND s.empresa_id=? AND s.activo=TRUE FOR UPDATE OF s", rs -> {
            if (!rs.next()) throw new RecursoNoEncontradoException("Secuencia no encontrada");
            Long fin=rs.getObject(4,Long.class);
            return new DatosSecuencia(rs.getLong(1), rs.getInt(2), rs.getString(3),fin);
        }, secuenciaId, tenantId, empresaId);
        long siguiente = Math.addExact(datos.valorActual(), 1);
        if(datos.numeroFinal()!=null&&siguiente>datos.numeroFinal())throw new ReglaNegocioException("La secuencia está agotada.");
        boolean agotada=datos.numeroFinal()!=null&&siguiente>=datos.numeroFinal();
        jdbc.update("UPDATE secuencias SET valor_actual=?,activo=?,actualizado_en=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND empresa_id=?",
            siguiente,!agotada,secuenciaId,tenantId,empresaId);
        auditoria.registrar("GENERAR_NUMERO", "Secuencia", secuenciaId, "{}");
        if(agotada)auditoria.registrar("FISCAL_SEQUENCE_EXHAUSTED","Secuencia",secuenciaId,"{}");
        return datos.prefijo() + String.format("%0" + datos.longitud() + "d", siguiente);
    }
    private record DatosSecuencia(long valorActual, int longitud, String prefijo,Long numeroFinal) {}
}
