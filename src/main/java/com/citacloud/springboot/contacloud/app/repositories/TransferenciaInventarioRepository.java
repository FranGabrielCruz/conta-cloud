package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;import jakarta.persistence.LockModeType;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.time.LocalDate;import java.util.*;

public interface TransferenciaInventarioRepository extends JpaRepository<TransferenciaInventario,UUID>{
 @EntityGraph(attributePaths={"origen","origen.sucursal","destino","destino.sucursal"})
 @Query("""
 select t from TransferenciaInventario t join t.origen o join t.destino d join o.sucursal os join d.sucursal ds
 where t.tenantId=:tenant and t.empresaId=:empresa
 and (:q='' or lower(t.numero) like concat('%',:q,'%') or lower(coalesce(t.reference,'')) like concat('%',:q,'%')
      or lower(o.nombre) like concat('%',:q,'%') or lower(d.nombre) like concat('%',:q,'%'))
 and (:desde is null or t.fecha>=:desde) and (:hasta is null or t.fecha<=:hasta)
 and (:estado is null or t.status=:estado)
 and (:so is null or os.id=:so) and (:ao is null or o.id=:ao)
 and (:sd is null or ds.id=:sd) and (:ad is null or d.id=:ad)
 """) Page<TransferenciaInventario> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("q")String q,
 @Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,@Param("estado")EstadoTransferenciaInventario estado,
 @Param("so")UUID sucursalOrigen,@Param("ao")UUID almacenOrigen,@Param("sd")UUID sucursalDestino,@Param("ad")UUID almacenDestino,Pageable pageable);
 @EntityGraph(attributePaths={"origen","origen.sucursal","destino","destino.sucursal"}) Optional<TransferenciaInventario> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TransferenciaInventario t where t.id=:id and t.tenantId=:tenant and t.empresaId=:empresa") Optional<TransferenciaInventario> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
