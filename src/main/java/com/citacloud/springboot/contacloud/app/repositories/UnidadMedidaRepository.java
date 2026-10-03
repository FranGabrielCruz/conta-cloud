package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.UnidadMedida;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida,UUID> {
    @Query("""
        select u from UnidadMedida u where u.tenantId=:tenant and u.empresaId=:empresa
        and (:buscar='' or u.nombreNormalizado like concat('%',:buscar,'%')
            or u.abreviaturaNormalizada like concat('%',:buscar,'%'))
        and (:activo is null or u.activo=:activo)
        """)
    Page<UnidadMedida> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("buscar")String buscar,@Param("activo")Boolean activo,Pageable pageable);
    List<UnidadMedida> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenant,UUID empresa);
    Optional<UnidadMedida> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    boolean existsByTenantIdAndEmpresaIdAndNombreNormalizado(UUID tenant,UUID empresa,String nombre);
    boolean existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(UUID tenant,UUID empresa,String nombre,UUID id);
    boolean existsByTenantIdAndEmpresaIdAndAbreviaturaNormalizada(UUID tenant,UUID empresa,String abreviatura);
    boolean existsByTenantIdAndEmpresaIdAndAbreviaturaNormalizadaAndIdNot(UUID tenant,UUID empresa,String abreviatura,UUID id);
}
