package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Sucursal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {
    Page<Sucursal> findAllByEmpresaId(UUID empresaId, Pageable pageable);
    Optional<Sucursal> findByIdAndEmpresaId(UUID id, UUID empresaId);
    long countByEmpresaIdAndActivoTrue(UUID empresaId);
    boolean existsByEmpresaIdAndPrincipalTrue(UUID empresaId);
    @Query("""
        select s from Sucursal s where s.empresaId=:empresaId
          and (:buscar='' or lower(s.nombre) like lower(concat('%',:buscar,'%'))
            or lower(coalesce(s.direccion,'')) like lower(concat('%',:buscar,'%'))
            or lower(coalesce(s.telefono,'')) like lower(concat('%',:buscar,'%')))
        """)
    Page<Sucursal> buscar(@Param("empresaId") UUID empresaId, @Param("buscar") String buscar, Pageable pageable);
    @Query("""
        select s from Sucursal s where s.empresaId=:empresaId and s.activo=:activo
          and (:buscar='' or lower(s.nombre) like lower(concat('%',:buscar,'%'))
            or lower(coalesce(s.direccion,'')) like lower(concat('%',:buscar,'%'))
            or lower(coalesce(s.telefono,'')) like lower(concat('%',:buscar,'%')))
        """)
    Page<Sucursal> buscarPorEstado(@Param("empresaId") UUID empresaId, @Param("buscar") String buscar,
                                   @Param("activo") boolean activo, Pageable pageable);
}
