package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProductRepository extends JpaRepository<Producto,UUID> {
    @EntityGraph(attributePaths={"categoria","unidadMedida","moneda","impuestoCompra","impuestoVenta"})
    @Query("""
      select p from Producto p left join p.categoria c where p.tenantId=:tenant and p.empresaId=:empresa
      and (:buscar='' or lower(p.codigo) like lower(concat('%',:buscar,'%'))
        or lower(p.nombre) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(p.codigoBarras,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombre,'')) like lower(concat('%',:buscar,'%')))
      and (:categoria is null or p.categoriaId=:categoria)
      and (:tipo is null or p.tipo=:tipo)
      and (:activo is null or p.activo=:activo)
      """)
    Page<Producto> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("categoria")UUID categoria,@Param("tipo")TipoProducto tipo,@Param("activo")Boolean activo,Pageable pageable);
    @EntityGraph(attributePaths={"categoria","unidadMedida","moneda","impuestoCompra","impuestoVenta"})
    Optional<Producto> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    boolean existsByTenantIdAndEmpresaIdAndCodigoBarras(UUID tenant,UUID empresa,String barcode);
    boolean existsByTenantIdAndEmpresaIdAndCodigoBarrasAndIdNot(UUID tenant,UUID empresa,String barcode,UUID id);
    boolean existsByTenantIdAndEmpresaIdAndUnidadMedidaIdAndActivoTrue(UUID tenant,UUID empresa,UUID unidadMedidaId);
    long countByTenantIdAndEmpresaIdAndCategoriaId(UUID tenant,UUID empresa,UUID categoriaId);
    @Query("select p.categoriaId,count(p) from Producto p where p.tenantId=:tenant and p.empresaId=:empresa and p.categoriaId in :categorias group by p.categoriaId")
    List<Object[]> contarPorCategorias(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("categorias")Collection<UUID> categorias);
}
