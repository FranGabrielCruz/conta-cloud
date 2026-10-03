package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Impuesto;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface ImpuestoRepository extends JpaRepository<Impuesto,UUID>{
 Optional<Impuesto> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
 List<Impuesto> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenantId,UUID empresaId);
 @Query("select i from Impuesto i where i.tenantId=:t and i.empresaId=:e and (:a is null or i.activo=:a) and (:q='' or lower(i.nombre) like lower(concat('%',:q,'%')) or lower(coalesce(i.descripcion,'')) like lower(concat('%',:q,'%'))) order by i.nombre") Page<Impuesto> buscar(@Param("t")UUID t,@Param("e")UUID e,@Param("q")String q,@Param("a")Boolean a,Pageable p);
}
