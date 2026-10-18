package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Almacen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AlmacenRepository extends JpaRepository<Almacen,UUID> {
    @EntityGraph(attributePaths={"sucursal"})
    @Query("select w from Almacen w where w.tenantId=:tenant and w.empresaId=:empresa and w.activo=true and w.administradoSistema=false order by w.nombre")
    List<Almacen> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(@Param("tenant")UUID tenantId,@Param("empresa")UUID empresaId);
    @EntityGraph(attributePaths={"sucursal"})
    List<Almacen> findAllByTenantIdAndEmpresaIdOrderByNombre(UUID tenantId,UUID empresaId);
    Optional<Almacen> findFirstByTenantIdAndEmpresaIdAndAdministradoSistemaTrueAndTipo(UUID tenantId,UUID empresaId,com.citacloud.springboot.contacloud.app.models.TipoAlmacen tipo);
    Optional<Almacen> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
    Optional<Almacen> findFirstByTenantIdAndEmpresaIdAndSucursalIdAndActivoTrueAndAdministradoSistemaFalseOrderByNombre(
        UUID tenantId,UUID empresaId,UUID sucursalId);
    @EntityGraph(attributePaths={"sucursal","responsable"})
    @Query("""
        select w from Almacen w join w.sucursal s left join w.responsable r
        where w.tenantId=:tenant and w.empresaId=:empresa
        and (:buscar='' or w.nombreNormalizado like concat('%',:buscar,'%') or lower(w.codigo) like concat('%',:buscar,'%')
             or lower(s.nombre) like concat('%',:buscar,'%')
             or lower(concat(coalesce(r.nombre,''),' ',coalesce(r.apellido,''))) like concat('%',:buscar,'%'))
        and (:sucursal is null or w.sucursalId=:sucursal)
        and (:tipo is null or w.tipo=:tipo)
        and w.administradoSistema=false and (:activo is null or w.activo=:activo)
        """)
    Page<Almacen> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
                         @Param("sucursal")UUID sucursal,@Param("tipo")com.citacloud.springboot.contacloud.app.models.TipoAlmacen tipo,
                         @Param("activo")Boolean activo,Pageable pageable);
    @EntityGraph(attributePaths={"sucursal","responsable"})
    @Query("select w from Almacen w where w.id=:id and w.tenantId=:tenant and w.empresaId=:empresa")
    Optional<Almacen> obtener(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    boolean existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreNormalizadoAndActivoTrue(UUID tenant,UUID empresa,UUID sucursal,String nombre);
    boolean existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreNormalizadoAndActivoTrueAndIdNot(UUID tenant,UUID empresa,UUID sucursal,String nombre,UUID id);
}
