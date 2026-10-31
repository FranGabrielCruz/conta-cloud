package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.VinculacionAgenteImpresion;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VinculacionAgenteImpresionRepository extends JpaRepository<VinculacionAgenteImpresion,UUID>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from VinculacionAgenteImpresion p where p.hashCodigo=:hash")
    Optional<VinculacionAgenteImpresion> bloquearPorHash(@Param("hash")String hash);
    @Modifying @Query("update VinculacionAgenteImpresion p set p.invalidadoEn=:ahora where p.tenantId=:tenant and p.empresaId=:empresa and p.agenteId=:agente and p.consumidoEn is null and p.invalidadoEn is null")
    int invalidarActivas(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("agente")UUID agente,@Param("ahora")OffsetDateTime ahora);
}
