package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.errors.IdempotencyConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fuente PostgreSQL de idempotencia. El advisory lock es transaccional y, por tanto,
 * coordina instancias distintas sin introducir un lock distribuido adicional.
 */
@Service
public class IdempotentOperationService {
    private static final String DIFFERENT_REQUEST = "La solicitud utiliza una clave de operación que ya fue utilizada con información diferente.";
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final int lockTimeoutMs;

    public IdempotentOperationService(JdbcTemplate jdbc, MeterRegistry metrics,
            @Value("${contacloud.locks.timeout-ms:5000}") int lockTimeoutMs) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        this.lockTimeoutMs = Math.max(250, lockTimeoutMs);
    }

    public Start begin(UUID tenantId, UUID empresaId, String operationType, UUID key, String fingerprint) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La idempotencia debe ejecutarse dentro de una transacción.");
        if (tenantId == null || empresaId == null || key == null || operationType == null || fingerprint == null)
            throw new ReglaNegocioException("No fue posible identificar la operación.");
        jdbc.execute("set local lock_timeout = '" + lockTimeoutMs + "ms'");
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, advisoryKey(tenantId, empresaId, operationType, key));
        List<Row> rows = jdbc.query("""
            select huella_solicitud,estado,tipo_resultado,resultado_id,completado_en
              from operaciones_idempotentes
             where tenant_id=? and empresa_id=? and tipo_operacion=? and clave_idempotencia=?
            """, (rs, row) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getObject(5, OffsetDateTime.class)),
            tenantId, empresaId, operationType, key);
        if (!rows.isEmpty()) {
            Row existing = rows.getFirst();
            if (!existing.fingerprint().equals(fingerprint)) {
                metrics.counter("contacloud.idempotency", "result", "conflict", "operation", operationType).increment();
                throw new IdempotencyConflictException(DIFFERENT_REQUEST);
            }
            if ("COMPLETED".equals(existing.status())) {
                metrics.counter("contacloud.idempotency", "result", "replay", "operation", operationType).increment();
                return new Start(false, existing.resultType(), existing.resultId());
            }
            throw new IdempotencyConflictException("La operación ya está siendo procesada. Intente nuevamente en unos segundos.");
        }
        jdbc.update("""
            insert into operaciones_idempotentes(tenant_id,empresa_id,tipo_operacion,clave_idempotencia,huella_solicitud,estado)
            values (?,?,?,?,?,'PROCESSING')
            """, tenantId, empresaId, operationType, key, fingerprint);
        return new Start(true, null, null);
    }

    public void complete(UUID tenantId, UUID empresaId, String operationType, UUID key, String resultType, UUID resultId) {
        int changed = jdbc.update("""
            update operaciones_idempotentes
               set estado='COMPLETED',tipo_resultado=?,resultado_id=?,completado_en=current_timestamp
             where tenant_id=? and empresa_id=? and tipo_operacion=? and clave_idempotencia=? and estado='PROCESSING'
            """, resultType, resultId, tenantId, empresaId, operationType, key);
        if (changed != 1) throw new IllegalStateException("No fue posible completar el registro idempotente.");
    }

    private static long advisoryKey(UUID tenant, UUID company, String type, UUID key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(tenant.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(company.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(type.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible crear la clave de bloqueo", exception);
        }
    }

    public record Start(boolean owner, String resultType, UUID resultId) {
        public boolean replay() { return !owner; }
    }
    private record Row(String fingerprint, String status, String resultType, UUID resultId, OffsetDateTime completedAt) {}
}
