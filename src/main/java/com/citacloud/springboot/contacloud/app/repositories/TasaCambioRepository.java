package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.TasaCambio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TasaCambioRepository extends JpaRepository<TasaCambio, UUID> {
    @EntityGraph(attributePaths = {"monedaOrigen", "monedaDestino"})
    @Query("""
        select t from TasaCambio t
        where t.tenantId=:tenantId and t.empresaId=:empresaId
          and (:buscar='' or lower(t.monedaOrigen.codigoIso) like lower(concat('%',:buscar,'%'))
            or lower(t.monedaOrigen.nombre) like lower(concat('%',:buscar,'%'))
            or lower(t.monedaDestino.codigoIso) like lower(concat('%',:buscar,'%'))
            or lower(t.monedaDestino.nombre) like lower(concat('%',:buscar,'%')))
          and (:monedaId is null or t.monedaOrigenId=:monedaId or t.monedaDestinoId=:monedaId)
          and (:fecha is null or t.fecha=:fecha)
        """)
    Page<TasaCambio> buscar(@Param("tenantId") UUID tenantId, @Param("empresaId") UUID empresaId,
        @Param("buscar") String buscar, @Param("monedaId") UUID monedaId,
        @Param("fecha") LocalDate fecha, Pageable pageable);

    @EntityGraph(attributePaths = {"monedaOrigen", "monedaDestino"})
    Optional<TasaCambio> findByIdAndTenantIdAndEmpresaId(UUID id, UUID tenantId, UUID empresaId);

    boolean existsByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFecha(
        UUID tenantId, UUID empresaId, UUID origenId, UUID destinoId, LocalDate fecha);

    boolean existsByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndIdNot(
        UUID tenantId, UUID empresaId, UUID origenId, UUID destinoId, LocalDate fecha, UUID id);

    @EntityGraph(attributePaths = {"monedaOrigen", "monedaDestino"})
    Optional<TasaCambio> findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
        UUID tenantId, UUID empresaId, UUID origenId, UUID destinoId, LocalDate fecha);
}
