package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface FinancialTransferRepository extends JpaRepository<TransferenciaFinanciera,UUID> {
    @EntityGraph(attributePaths={"cajaOrigen","cuentaBancariaOrigen","cajaDestino","cuentaBancariaDestino",
        "monedaOrigen","monedaDestino","usuarioAnulacion"})
    @Query("""
      select t from TransferenciaFinanciera t
      left join t.cajaOrigen co left join t.cuentaBancariaOrigen bo
      left join t.cajaDestino cd left join t.cuentaBancariaDestino bd
      where t.tenantId=:tenantId and t.empresaId=:empresaId
        and (:buscar='' or lower(coalesce(t.referencia,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(t.descripcion,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(co.nombre,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(bo.nombreCuenta,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(bo.bancoNombre,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(cd.nombre,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(bd.nombreCuenta,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(bd.bancoNombre,'')) like lower(concat('%',:buscar,'%')))
        and (:desde is null or t.fecha>=:desde) and (:hasta is null or t.fecha<=:hasta)
        and (:estado is null or t.estado=:estado)
        and (:todasSucursales=true or
          (t.cajaOrigenId is null or t.cajaOrigenId in (select c.id from Caja c where c.tenantId=:tenantId
            and c.empresaId=:empresaId and c.sucursalId in :sucursales)) and
          (t.cajaDestinoId is null or t.cajaDestinoId in (select c.id from Caja c where c.tenantId=:tenantId
            and c.empresaId=:empresaId and c.sucursalId in :sucursales)))
      """)
    Page<TransferenciaFinanciera> buscar(@Param("tenantId")UUID tenantId,@Param("empresaId")UUID empresaId,
        @Param("buscar")String buscar,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,
        @Param("estado")EstadoMovimientoFinanciero estado,@Param("todasSucursales")boolean todasSucursales,
        @Param("sucursales")Set<UUID> sucursales,Pageable pageable);

    @EntityGraph(attributePaths={"cajaOrigen","cuentaBancariaOrigen","cajaDestino","cuentaBancariaDestino",
        "monedaOrigen","monedaDestino","usuarioAnulacion"})
    Optional<TransferenciaFinanciera> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferenciaFinanciera t where t.id=:id and t.tenantId=:tenantId and t.empresaId=:empresaId")
    Optional<TransferenciaFinanciera> buscarParaAnular(@Param("id")UUID id,@Param("tenantId")UUID tenantId,
        @Param("empresaId")UUID empresaId);
}
