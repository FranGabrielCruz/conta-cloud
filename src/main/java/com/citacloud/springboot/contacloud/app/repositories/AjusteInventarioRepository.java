package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;import jakarta.persistence.LockModeType;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.time.LocalDate;import java.util.*;
public interface AjusteInventarioRepository extends JpaRepository<AjusteInventario,UUID>{
 @EntityGraph(attributePaths={"almacen","almacen.sucursal","motivo"}) @Query("""
 select a from AjusteInventario a where a.tenantId=:t and a.empresaId=:e
 and (:q='' or lower(a.numero) like concat('%',:q,'%') or lower(coalesce(a.referencia,'')) like concat('%',:q,'%') or exists(select l.id from LineaAjusteInventario l where l.ajusteId=a.id and (lower(l.nombreProducto) like concat('%',:q,'%') or lower(l.codigoProducto) like concat('%',:q,'%'))))
 and (:desde is null or a.fecha>=:desde) and (:hasta is null or a.fecha<=:hasta) and (:estado is null or a.status=:estado) and (:almacen is null or a.almacenId=:almacen) and (:tipo is null or a.tipo=:tipo) and (:motivo is null or a.motivoId=:motivo)
 """) Page<AjusteInventario> buscar(@Param("t")UUID t,@Param("e")UUID e,@Param("q")String q,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,@Param("estado")EstadoAjusteInventario estado,@Param("almacen")UUID almacen,@Param("tipo")TipoAjusteInventario tipo,@Param("motivo")UUID motivo,Pageable p);
 @EntityGraph(attributePaths={"almacen","almacen.sucursal","motivo"})Optional<AjusteInventario> findByIdAndTenantIdAndEmpresaId(UUID id,UUID t,UUID e);
 @Lock(LockModeType.PESSIMISTIC_WRITE)@EntityGraph(attributePaths={"almacen","almacen.sucursal","motivo"})@Query("select a from AjusteInventario a where a.id=:id and a.tenantId=:t and a.empresaId=:e")Optional<AjusteInventario> bloquear(@Param("id")UUID id,@Param("t")UUID t,@Param("e")UUID e);
}
