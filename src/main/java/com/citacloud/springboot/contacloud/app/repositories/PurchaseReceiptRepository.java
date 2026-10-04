package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface PurchaseReceiptRepository extends JpaRepository<RecepcionCompra,UUID> {
    @EntityGraph(attributePaths={"proveedor","almacen","ordenCompra"})
    @Query("""
      select r from RecepcionCompra r where r.tenantId=:tenant and r.empresaId=:empresa
      and (:buscar='' or lower(r.numero) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(r.reference,'')) like lower(concat('%',:buscar,'%'))
        or lower(r.proveedor.nombreComercial) like lower(concat('%',:buscar,'%')))
      and (:desde is null or r.fecha>=:desde) and (:hasta is null or r.fecha<=:hasta)
      and (:estado is null or r.status=:estado)
      """)
    Page<RecepcionCompra> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("buscar")String buscar,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,
        @Param("estado")EstadoRecepcionCompra estado,Pageable pageable);
    @EntityGraph(attributePaths={"lineas","proveedor","almacen","ordenCompra"})
    Optional<RecepcionCompra> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
    @EntityGraph(attributePaths={"lineas","proveedor","almacen","ordenCompra"})
    Optional<RecepcionCompra> findByTenantIdAndEmpresaIdAndClaveIdempotencia(UUID tenantId,UUID empresaId,UUID claveIdempotencia);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths={"lineas","proveedor","almacen","ordenCompra"})
    @Query("select r from RecepcionCompra r where r.id=:id and r.tenantId=:tenant and r.empresaId=:empresa")
    Optional<RecepcionCompra> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
