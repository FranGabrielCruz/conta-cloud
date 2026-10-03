package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface PurchaseOrderRepository extends JpaRepository<OrdenCompra,UUID> {
    @EntityGraph(attributePaths={"sucursal","moneda","condicionPago"})
    @Query("""
      select o from OrdenCompra o where o.tenantId=:tenant and o.empresaId=:empresa
      and (:buscar='' or lower(o.numero) like lower(concat('%',:buscar,'%'))
        or lower(o.proveedorNombre) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(o.reference,'')) like lower(concat('%',:buscar,'%')))
      and (:desde is null or o.fecha>=:desde) and (:hasta is null or o.fecha<=:hasta)
      and (:estado is null or o.status=:estado)
      """)
    Page<OrdenCompra> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,@Param("estado")EstadoOrdenCompra estado,Pageable pageable);

    @EntityGraph(attributePaths={"lineas","sucursal","moneda","condicionPago"})
    Optional<OrdenCompra> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths={"lineas","sucursal","moneda","condicionPago"})
    @Query("select o from OrdenCompra o where o.id=:id and o.tenantId=:tenant and o.empresaId=:empresa")
    Optional<OrdenCompra> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
