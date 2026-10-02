package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Proveedor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SupplierRepository extends JpaRepository<Proveedor,UUID>{
    @EntityGraph(attributePaths={"condicionPago","moneda"})
    @Query("""
      select p from Proveedor p where p.tenantId=:tenant and p.empresaId=:empresa
      and (:buscar='' or lower(p.nombreComercial) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(p.razonSocial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(p.identificacionFiscal,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(p.identificacionFiscalNormalizada,'')) like lower(concat('%',:identificacion,'%'))
        or lower(coalesce(p.correo,'')) like lower(concat('%',:buscar,'%')))
      and (:activo is null or p.activo=:activo)
      """)
    Page<Proveedor> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("identificacion")String identificacion,@Param("activo")Boolean activo,Pageable pageable);
    @EntityGraph(attributePaths={"condicionPago","moneda"})
    Optional<Proveedor> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    boolean existsByTenantIdAndEmpresaIdAndIdentificacionFiscalNormalizada(UUID tenant,UUID empresa,String identificacion);
    boolean existsByTenantIdAndEmpresaIdAndIdentificacionFiscalNormalizadaAndIdNot(UUID tenant,UUID empresa,String identificacion,UUID id);
}
