package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Caja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.Set;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caja c where c.id=:id and c.tenantId=:tenant and c.empresaId=:empresa")
    Optional<Caja> bloquearParaOperacion(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);

    @EntityGraph(attributePaths = {"sucursal", "moneda"})
    @Query("""
        select c from Caja c where c.tenantId=:tenant and c.empresaId=:empresa
          and (:buscar='' or lower(c.nombre) like lower(concat('%',:buscar,'%')))
          and (:sucursal is null or c.sucursalId=:sucursal)
          and (:todas=true or c.sucursalId in :sucursales)
          and (:abierta is null or (:abierta=true and exists(select s.id from SesionCaja s where s.tenantId=:tenant and s.empresaId=:empresa and s.cajaId=c.id and s.status=com.citacloud.springboot.contacloud.app.models.EstadoSesionCaja.OPEN))
            or (:abierta=false and not exists(select s.id from SesionCaja s where s.tenantId=:tenant and s.empresaId=:empresa and s.cajaId=c.id and s.status=com.citacloud.springboot.contacloud.app.models.EstadoSesionCaja.OPEN)))
        """)
    Page<Caja> buscarOperaciones(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("sucursal")UUID sucursal,@Param("abierta")Boolean abierta,@Param("todas")boolean todas,
        @Param("sucursales")Set<UUID> sucursales,Pageable pageable);

    boolean existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCase(
        UUID tenantId, UUID empresaId, UUID sucursalId, String nombre);
    boolean existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCaseAndIdNot(
        UUID tenantId, UUID empresaId, UUID sucursalId, String nombre, UUID id);
}
