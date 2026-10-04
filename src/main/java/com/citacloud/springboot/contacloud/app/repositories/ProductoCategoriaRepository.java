package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.ProductoCategoria;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProductoCategoriaRepository extends JpaRepository<ProductoCategoria,UUID> {
    @Query("""
        select c from ProductoCategoria c where c.tenantId=:tenant and c.empresaId=:empresa
        and (:buscar='' or c.nombreNormalizado like concat('%',:buscar,'%')
            or lower(coalesce(c.descripcion,'')) like concat('%',:buscar,'%'))
        and (:activo is null or c.activo=:activo)
        """)
    Page<ProductoCategoria> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("buscar")String buscar,@Param("activo")Boolean activo,Pageable pageable);
    List<ProductoCategoria> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenant,UUID empresa);
    Optional<ProductoCategoria> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    boolean existsByTenantIdAndEmpresaIdAndNombreNormalizado(UUID tenant,UUID empresa,String nombre);
    boolean existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(UUID tenant,UUID empresa,String nombre,UUID id);
}
