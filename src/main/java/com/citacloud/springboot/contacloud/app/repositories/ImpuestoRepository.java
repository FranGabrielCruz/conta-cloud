package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Impuesto;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface ImpuestoRepository extends JpaRepository<Impuesto,UUID>{
 Optional<Impuesto> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
 List<Impuesto> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenantId,UUID empresaId);
 boolean existsByTenantIdAndEmpresaIdAndNombreNormalizado(UUID tenantId,UUID empresaId,String nombreNormalizado);
 boolean existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(UUID tenantId,UUID empresaId,String nombreNormalizado,UUID id);
 @Query("select i from Impuesto i where i.tenantId=:t and i.empresaId=:e and (:a is null or i.activo=:a) and (:q='' or i.nombreNormalizado like concat('%',:q,'%') or lower(coalesce(i.descripcion,'')) like concat('%',:q,'%'))") Page<Impuesto> buscar(@Param("t")UUID t,@Param("e")UUID e,@Param("q")String q,@Param("a")Boolean a,Pageable p);
}
