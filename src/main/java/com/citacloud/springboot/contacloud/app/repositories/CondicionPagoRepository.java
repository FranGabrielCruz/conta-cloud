package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.CondicionPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CondicionPagoRepository extends JpaRepository<CondicionPago, UUID> {
    @Query("""
        select c from CondicionPago c
        where c.tenantId=:tenantId and c.empresaId=:empresaId
          and (:buscar='' or lower(c.nombre) like lower(concat('%',:buscar,'%'))
            or lower(coalesce(c.descripcion,'')) like lower(concat('%',:buscar,'%')))
          and (:activo is null or c.activo=:activo)
        """)
    Page<CondicionPago> buscar(@Param("tenantId") UUID tenantId, @Param("empresaId") UUID empresaId,
        @Param("buscar") String buscar, @Param("activo") Boolean activo, Pageable pageable);

    Optional<CondicionPago> findByIdAndTenantIdAndEmpresaId(UUID id, UUID tenantId, UUID empresaId);
    boolean existsByTenantIdAndEmpresaIdAndNombreIgnoreCase(UUID tenantId, UUID empresaId, String nombre);
    boolean existsByTenantIdAndEmpresaIdAndNombreIgnoreCaseAndIdNot(UUID tenantId, UUID empresaId,
        String nombre, UUID id);
    List<CondicionPago> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenantId, UUID empresaId);
}
