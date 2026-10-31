package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AgenteImpresionRepository extends JpaRepository<AgenteImpresion,UUID>{
    @Query("""
      select a from AgenteImpresion a where a.tenantId=:tenant and a.empresaId=:empresa
      and (:buscar='' or lower(a.nombre) like lower(concat('%',:buscar,'%')) or lower(a.codigoVisible) like lower(concat('%',:buscar,'%')))
      and (:estado is null or a.estado=:estado)
      """)
    Page<AgenteImpresion> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,@Param("estado")EstadoAgenteImpresion estado,Pageable pageable);
    Optional<AgenteImpresion> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AgenteImpresion a where a.id=:id and a.tenantId=:tenant and a.empresaId=:empresa")
    Optional<AgenteImpresion> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AgenteImpresion a where a.id=:id and a.tenantId=:tenant")
    Optional<AgenteImpresion> bloquearAgente(@Param("id")UUID id,@Param("tenant")UUID tenant);
}
