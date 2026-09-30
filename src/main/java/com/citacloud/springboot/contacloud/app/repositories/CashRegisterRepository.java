package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Caja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CashRegisterRepository extends JpaRepository<Caja, UUID> {
    @EntityGraph(attributePaths = {"sucursal", "moneda"})
    @Query("""
        select c from Caja c
        where c.tenantId=:tenantId and c.empresaId=:empresaId
          and (:buscar='' or lower(c.nombre) like lower(concat('%',:buscar,'%'))
            or lower(coalesce(c.descripcion,'')) like lower(concat('%',:buscar,'%')))
          and (:sucursalId is null or c.sucursalId=:sucursalId)
          and (:activo is null or c.activo=:activo)
        """)
    Page<Caja> buscar(@Param("tenantId") UUID tenantId, @Param("empresaId") UUID empresaId,
        @Param("buscar") String buscar, @Param("sucursalId") UUID sucursalId,
        @Param("activo") Boolean activo, Pageable pageable);

    @EntityGraph(attributePaths = {"sucursal", "moneda"})
    Optional<Caja> findByIdAndTenantIdAndEmpresaId(UUID id, UUID tenantId, UUID empresaId);

    @EntityGraph(attributePaths = {"sucursal", "moneda"})
    List<Caja> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(
        UUID tenantId, UUID empresaId);

    boolean existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCase(
        UUID tenantId, UUID empresaId, UUID sucursalId, String nombre);
    boolean existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCaseAndIdNot(
        UUID tenantId, UUID empresaId, UUID sucursalId, String nombre, UUID id);
}
