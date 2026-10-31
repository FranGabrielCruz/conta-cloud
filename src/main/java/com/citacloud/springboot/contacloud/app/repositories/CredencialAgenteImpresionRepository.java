package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.CredencialAgenteImpresion;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CredencialAgenteImpresionRepository extends JpaRepository<CredencialAgenteImpresion,UUID>{
    Optional<CredencialAgenteImpresion> findByHashAcceso(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CredencialAgenteImpresion c where c.hashRenovacion=:hash")
    Optional<CredencialAgenteImpresion> bloquearPorRenovacion(@Param("hash")String hash);
    @Modifying @Query("update CredencialAgenteImpresion c set c.revocadaEn=:ahora where c.tenantId=:tenant and c.empresaId=:empresa and c.agenteId=:agente and c.revocadaEn is null")
    int revocarActivas(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("agente")UUID agente,@Param("ahora")OffsetDateTime ahora);
}
