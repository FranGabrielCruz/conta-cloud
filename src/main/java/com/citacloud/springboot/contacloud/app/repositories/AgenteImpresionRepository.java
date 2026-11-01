package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AgenteImpresionRepository extends JpaRepository<AgenteImpresion,UUID>{
    @Query("select distinct a from AgenteImpresion a join a.cajas c where a.tenantId=:tenant and a.empresaId=:empresa and a.sucursalId=:sucursal and c.id=:caja and a.estado=:estado")
    List<AgenteImpresion> destinos(@Param("tenant") UUID tenant,@Param("empresa") UUID empresa,@Param("sucursal") UUID sucursal,@Param("caja") UUID caja,@Param("estado") EstadoAgenteImpresion estado);

    interface CajaAsignada {UUID getAgenteId();UUID getCajaId();String getNombre();}
    @Query("select a.id as agenteId,c.id as cajaId,c.nombre as nombre from AgenteImpresion a join a.cajas c where a.tenantId=:tenant and a.empresaId=:empresa and a.id in :ids order by c.nombre,c.id")
    List<CajaAsignada> cajasAsignadas(@Param("tenant") UUID tenant,@Param("empresa") UUID empresa,@Param("ids") Collection<UUID> ids);

    @Query("select count(a) from AgenteImpresion a join a.cajas c where a.tenantId=:tenant and a.empresaId=:empresa and c.id=:caja and a.estado=com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion.ACTIVE and (:agente is null or a.id<>:agente)")
    long otrosDestinosActivos(@Param("tenant") UUID tenant,@Param("empresa") UUID empresa,@Param("caja") UUID caja,@Param("agente") UUID agente);
    @Query("""
      select a from AgenteImpresion a where a.tenantId=:tenant and a.empresaId=:empresa
      and (:buscar='' or lower(a.nombre) like lower(concat('%',:buscar,'%')) or lower(a.codigoVisible) like lower(concat('%',:buscar,'%')))
      and (:estado is null or a.estado=:estado)
      and (:todas=true or a.sucursalId in :sucursales)
      """)
    Page<AgenteImpresion> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,@Param("estado")EstadoAgenteImpresion estado,@Param("todas")boolean todas,@Param("sucursales")Collection<UUID> sucursales,Pageable pageable);
    Optional<AgenteImpresion> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AgenteImpresion a where a.id=:id and a.tenantId=:tenant and a.empresaId=:empresa")
    Optional<AgenteImpresion> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AgenteImpresion a where a.id=:id and a.tenantId=:tenant")
    Optional<AgenteImpresion> bloquearAgente(@Param("id")UUID id,@Param("tenant")UUID tenant);
}
