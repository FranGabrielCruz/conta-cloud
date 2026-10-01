package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface BankReconciliationRepository extends JpaRepository<ConciliacionBancaria,UUID>{
 @EntityGraph(attributePaths={"cuentaBancaria","moneda","usuarioFinalizacion","usuarioAnulacion"})
 @Query("""
   select r from ConciliacionBancaria r where r.tenantId=:tenant and r.empresaId=:empresa
   and (:cuenta is null or r.cuentaBancariaId=:cuenta)
   and (:desde is null or r.fechaFinal>=:desde) and (:hasta is null or r.fechaInicial<=:hasta)
   and (:estado is null or r.status=:estado)
   """)
 Page<ConciliacionBancaria> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
   @Param("cuenta")UUID cuenta,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,
   @Param("estado")EstadoConciliacionBancaria estado,Pageable pageable);
 @EntityGraph(attributePaths={"cuentaBancaria","moneda","usuarioFinalizacion","usuarioAnulacion"})
 Optional<ConciliacionBancaria> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
 @Query("""
   select count(r)>0 from ConciliacionBancaria r where r.tenantId=:tenant and r.empresaId=:empresa
   and r.cuentaBancariaId=:cuenta and r.status<>com.citacloud.springboot.contacloud.app.models.EstadoConciliacionBancaria.VOIDED
   and r.fechaInicial<=:hasta and r.fechaFinal>=:desde
   """)
 boolean existeSolapamiento(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("cuenta")UUID cuenta,
   @Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from ConciliacionBancaria r where r.id=:id and r.tenantId=:tenant and r.empresaId=:empresa")
 Optional<ConciliacionBancaria> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
