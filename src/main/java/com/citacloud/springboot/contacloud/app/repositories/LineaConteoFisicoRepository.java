package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaConteoFisico;import jakarta.persistence.LockModeType;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface LineaConteoFisicoRepository extends JpaRepository<LineaConteoFisico,UUID>{
 @Query("select l from LineaConteoFisico l where l.conteoId=:id order by l.orden")List<LineaConteoFisico> findAllByConteoIdOrderByOrden(@Param("id")UUID id);
 @Query("select l from LineaConteoFisico l where l.conteoId=:id and (:q='' or lower(l.codigo) like concat('%',:q,'%') or lower(l.producto) like concat('%',:q,'%'))")Page<LineaConteoFisico> buscar(@Param("id")UUID id,@Param("q")String q,Pageable pageable);
 long countByConteoId(UUID id);long countByConteoIdAndCantidadContadaIsNotNull(UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select l from LineaConteoFisico l where l.conteoId=:id order by l.orden")List<LineaConteoFisico> bloquearPorConteo(@Param("id")UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select l from LineaConteoFisico l where l.conteoId=:id and l.id in :ids order by l.orden")List<LineaConteoFisico> bloquearLineas(@Param("id")UUID id,@Param("ids")Collection<UUID> ids);
 @Query("select l.conteoId,count(l),sum(case when l.diferencia is not null and l.diferencia<>0 then 1 else 0 end) from LineaConteoFisico l where l.conteoId in :ids group by l.conteoId")List<Object[]> resumen(@Param("ids")Collection<UUID> ids);
 void deleteAllByConteoId(UUID id);
}
