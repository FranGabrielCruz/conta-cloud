package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
public interface FinancialMovementRepository extends JpaRepository<MovimientoFinanciero,UUID> {
    @EntityGraph(attributePaths={"caja","cuentaBancaria","moneda","usuarioAnulacion"})
    @Query("""
      select m from MovimientoFinanciero m
      where m.tenantId=:tenantId and m.empresaId=:empresaId and m.tipoMovimiento=:tipo
        and m.tipoOrigen=com.citacloud.springboot.contacloud.app.models.TipoOrigenMovimiento.MANUAL
        and (:buscar='' or lower(m.concepto) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(m.referencia,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(m.descripcion,'')) like lower(concat('%',:buscar,'%')))
        and (:desde is null or m.fecha>=:desde) and (:hasta is null or m.fecha<=:hasta)
        and (:tipoCuenta is null or m.tipoCuenta=:tipoCuenta)
        and (:cuentaId is null or m.cajaId=:cuentaId or m.cuentaBancariaId=:cuentaId)
        and (:estado is null or m.estado=:estado)
        and (:todasSucursales=true or m.tipoCuenta=com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero.BANK_ACCOUNT
          or m.cajaId in (select c.id from Caja c where c.tenantId=:tenantId and c.empresaId=:empresaId
            and c.sucursalId in :sucursales))
      """)
    Page<MovimientoFinanciero> buscar(@Param("tenantId") UUID tenantId,@Param("empresaId") UUID empresaId,
        @Param("tipo") TipoMovimientoFinanciero tipo,@Param("buscar") String buscar,
        @Param("desde") LocalDate desde,@Param("hasta") LocalDate hasta,
        @Param("tipoCuenta") TipoCuentaDinero tipoCuenta,@Param("cuentaId") UUID cuentaId,
        @Param("estado") EstadoMovimientoFinanciero estado,@Param("todasSucursales") boolean todasSucursales,
        @Param("sucursales") Set<UUID> sucursales,Pageable pageable);
    @EntityGraph(attributePaths={"caja","cuentaBancaria","moneda","usuarioAnulacion"})
    Optional<MovimientoFinanciero> findByIdAndTenantIdAndEmpresaIdAndTipoMovimiento(
        UUID id,UUID tenantId,UUID empresaId,TipoMovimientoFinanciero tipo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      select m from MovimientoFinanciero m where m.id=:id and m.tenantId=:tenantId
        and m.empresaId=:empresaId and m.tipoMovimiento=:tipo
      """)
    Optional<MovimientoFinanciero> buscarParaActualizar(@Param("id") UUID id,
        @Param("tenantId") UUID tenantId,@Param("empresaId") UUID empresaId,
        @Param("tipo") TipoMovimientoFinanciero tipo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      select m from MovimientoFinanciero m where m.tenantId=:tenantId and m.empresaId=:empresaId
        and m.tipoOrigen=com.citacloud.springboot.contacloud.app.models.TipoOrigenMovimiento.TRANSFER
        and m.origenId=:transferenciaId
      """)
    List<MovimientoFinanciero> buscarTransferenciaParaAnular(@Param("tenantId")UUID tenantId,
        @Param("empresaId")UUID empresaId,@Param("transferenciaId")UUID transferenciaId);

    @Query("""
      select m from MovimientoFinanciero m where m.tenantId=:tenantId and m.empresaId=:empresaId
        and m.tipoCuenta=com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero.BANK_ACCOUNT
        and m.cuentaBancariaId=:cuentaId and m.monedaId=:monedaId
        and m.estado=com.citacloud.springboot.contacloud.app.models.EstadoMovimientoFinanciero.REGISTERED
        and m.fecha<=:hasta
        and not exists(select a.id from AsociacionConciliacionBancaria a where a.movimientoFinancieroId=m.id)
      """)
    Page<MovimientoFinanciero> candidatosConciliacion(@Param("tenantId")UUID tenantId,
        @Param("empresaId")UUID empresaId,@Param("cuentaId")UUID cuentaId,
        @Param("monedaId")UUID monedaId,@Param("hasta")LocalDate hasta,Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MovimientoFinanciero> findByIdAndTenantIdAndEmpresaIdAndTipoCuentaAndCuentaBancariaId(
        UUID id,UUID tenantId,UUID empresaId,TipoCuentaDinero tipoCuenta,UUID cuentaBancariaId);
}
