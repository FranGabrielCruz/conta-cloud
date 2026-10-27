package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
public interface FinancialMovementRepository extends JpaRepository<MovimientoFinanciero,UUID> {
    String CASH_MOVEMENT_SELECT="""
      with ordered as (
        select m.id,coalesce(m.cash_movement_number,nullif(m.referencia,''),'MOVIMIENTO') as numero,
          m.creado_en as "fechaHora",c.nombre as caja,s.nombre as sucursal,u.usuario as usuario,
          m.tipo_movimiento as tipo,m.tipo_origen as origen,m.concepto,coalesce(m.referencia,'—') as referencia,
          m.monto as importe,mo.codigo_iso as "monedaCodigo",m.descripcion as observacion,m.estado,
          m.reversal_of_movement_id as "reversionDeId",
          case when m.tipo_origen='MANUAL' and m.estado='REGISTERED' and not exists(
            select 1 from movimientos_financieros rv where rv.tenant_id=m.tenant_id and rv.empresa_id=m.empresa_id
              and rv.reversal_of_movement_id=m.id) then true else false end as reversible,
          cs.opening_amount + sum(case when m.estado='REGISTERED' then
            case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end)
            over(order by m.creado_en,m.id) as saldo
        from movimientos_financieros m
        join cash_register_session cs on cs.id=m.cash_register_session_id and cs.tenant_id=m.tenant_id and cs.empresa_id=m.empresa_id
        join cajas c on c.id=m.caja_id and c.tenant_id=m.tenant_id and c.empresa_id=m.empresa_id
        join sucursales s on s.id=cs.branch_id and s.empresa_id=m.empresa_id
        join monedas mo on mo.id=m.moneda_id and mo.empresa_id=m.empresa_id
        join usuarios u on u.id=m.creado_por and u.tenant_id=m.tenant_id and u.empresa_id=m.empresa_id
        where m.tenant_id=:tenant and m.empresa_id=:empresa and m.cash_register_session_id=:sesion
          and m.tipo_cuenta='CASH_REGISTER' and m.payment_method='CASH'
      )
      """;

    @Query(value=CASH_MOVEMENT_SELECT+"""
      select * from ordered where (:buscar='' or lower(concepto) like lower(concat('%',:buscar,'%'))
        or lower(referencia) like lower(concat('%',:buscar,'%')) or lower(numero) like lower(concat('%',:buscar,'%')))
        and (:tipo='ALL' or tipo=:tipo) and (:origen='ALL' or origen=:origen)
      order by "fechaHora" desc,id desc
      """,countQuery="""
      select count(*) from movimientos_financieros m
      where m.tenant_id=:tenant and m.empresa_id=:empresa and m.cash_register_session_id=:sesion
        and m.tipo_cuenta='CASH_REGISTER' and m.payment_method='CASH'
        and (:buscar='' or lower(m.concepto) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(m.referencia,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(m.cash_movement_number,'')) like lower(concat('%',:buscar,'%')))
        and (:tipo='ALL' or m.tipo_movimiento=:tipo) and (:origen='ALL' or m.tipo_origen=:origen)
      """,nativeQuery=true)
    Page<MovimientoCajaProjection> buscarMovimientosCaja(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("sesion")UUID sesion,@Param("buscar")String buscar,@Param("tipo")String tipo,
        @Param("origen")String origen,Pageable pageable);

    @Query(value=CASH_MOVEMENT_SELECT+"select * from ordered where id=:movimiento",nativeQuery=true)
    Optional<MovimientoCajaProjection> detalleMovimientoCaja(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("sesion")UUID sesion,@Param("movimiento")UUID movimiento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimientoFinanciero m where m.id=:id and m.tenantId=:tenant and m.empresaId=:empresa and m.sesionCajaId=:sesion")
    Optional<MovimientoFinanciero> bloquearMovimientoCaja(@Param("id")UUID id,@Param("tenant")UUID tenant,
        @Param("empresa")UUID empresa,@Param("sesion")UUID sesion);

    boolean existsByTenantIdAndEmpresaIdAndReversionDeMovimientoId(UUID tenant,UUID empresa,UUID original);
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

    @Query("""
      select m from MovimientoFinanciero m where m.tenantId=:tenant and m.empresaId=:empresa
        and m.sesionCajaId=:sesion and m.estado=com.citacloud.springboot.contacloud.app.models.EstadoMovimientoFinanciero.REGISTERED
      """)
    List<MovimientoFinanciero> movimientosValidosSesion(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("sesion")UUID sesion);

    @Query("""
      select m from MovimientoFinanciero m where m.tenantId=:tenant and m.empresaId=:empresa
        and m.sesionCajaId=:sesion
        and m.estado=com.citacloud.springboot.contacloud.app.models.EstadoMovimientoFinanciero.REGISTERED
        and m.creadoEn<=:corte order by m.creadoEn,m.id
      """)
    List<MovimientoFinanciero> movimientosValidosSesionHasta(@Param("tenant")UUID tenant,
        @Param("empresa")UUID empresa,@Param("sesion")UUID sesion,@Param("corte")OffsetDateTime corte);

    @Query("""
      select m from MovimientoFinanciero m where m.tenantId=:tenant and m.empresaId=:empresa
        and m.sesionCajaId=:sesion
      """)
    Page<MovimientoFinanciero> movimientosSesion(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("sesion")UUID sesion,Pageable pageable);

    Optional<MovimientoFinanciero> findByTenantIdAndEmpresaIdAndTipoOrigenAndOrigenId(UUID tenant,UUID empresa,
        TipoOrigenMovimiento tipoOrigen,UUID origenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimientoFinanciero m where m.tenantId=:tenant and m.empresaId=:empresa and m.tipoOrigen=:origen and m.origenId=:origenId")
    Optional<MovimientoFinanciero> bloquearPorOrigen(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("origen")TipoOrigenMovimiento origen,@Param("origenId")UUID origenId);

    @Query("""
      select coalesce(sum(case when m.tipoMovimiento=com.citacloud.springboot.contacloud.app.models.TipoMovimientoFinanciero.INCOME
        then m.monto else (0-m.monto) end),0) from MovimientoFinanciero m
      where m.tenantId=:tenant and m.empresaId=:empresa and m.tipoCuenta=com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero.BANK_ACCOUNT
        and m.cuentaBancariaId=:cuenta and m.estado=com.citacloud.springboot.contacloud.app.models.EstadoMovimientoFinanciero.REGISTERED
      """)
    java.math.BigDecimal saldoCuenta(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("cuenta")UUID cuenta);

    @Query(value="""
      select b.id as "cuentaId", concat(b.banco_nombre,' · ',b.nombre_cuenta,' · •••• ',b.numero_cuenta_ultimos4) as cuenta,
        b.moneda_id as "monedaId", mo.codigo_iso as moneda,mo.simbolo as simbolo,null::uuid as "sucursalId",null::varchar as sucursal,b.activo as activo,
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta and m.tipo_origen='OPENING_BALANCE' then case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end),0) as "saldoInicial",
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta and m.tipo_origen<>'OPENING_BALANCE' and m.tipo_movimiento='INCOME' then m.monto else 0 end),0) as entradas,
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta and m.tipo_origen<>'OPENING_BALANCE' and m.tipo_movimiento='EXPENSE' then m.monto else 0 end),0) as salidas,
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta then case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end),0) as "saldoActual"
      from cuentas_bancarias b join monedas mo on mo.id=b.moneda_id and mo.empresa_id=b.empresa_id
      left join movimientos_financieros m on m.tenant_id=b.tenant_id and m.empresa_id=b.empresa_id and m.cuenta_bancaria_id=b.id
      where b.tenant_id=:tenant and b.empresa_id=:empresa and (:moneda is null or b.moneda_id=:moneda) and (:activo is null or b.activo=:activo)
        and (:buscar='' or lower(b.banco_nombre) like lower(concat('%',:buscar,'%')) or lower(b.nombre_cuenta) like lower(concat('%',:buscar,'%')) or b.numero_cuenta_ultimos4 like concat('%',:buscar,'%') or lower(b.codigo) like lower(concat('%',:buscar,'%')))
      group by b.id,b.banco_nombre,b.nombre_cuenta,b.numero_cuenta_ultimos4,b.moneda_id,mo.codigo_iso,mo.simbolo,b.activo
      order by b.banco_nombre,b.nombre_cuenta,b.id
      """,countQuery="""
      select count(*) from cuentas_bancarias b where b.tenant_id=:tenant and b.empresa_id=:empresa
        and (:moneda is null or b.moneda_id=:moneda) and (:activo is null or b.activo=:activo)
        and (:buscar='' or lower(b.banco_nombre) like lower(concat('%',:buscar,'%')) or lower(b.nombre_cuenta) like lower(concat('%',:buscar,'%')) or b.numero_cuenta_ultimos4 like concat('%',:buscar,'%') or lower(b.codigo) like lower(concat('%',:buscar,'%')))
      """,nativeQuery=true)
    Page<SaldoCuentaProjection> saldosBancarios(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("hasta")LocalDate hasta,
        @Param("buscar")String buscar,@Param("moneda")UUID moneda,@Param("activo")Boolean activo,Pageable pageable);

    @Query(value="""
      select c.id as "cuentaId",c.nombre as cuenta,c.moneda_id as "monedaId",mo.codigo_iso as moneda,mo.simbolo as simbolo,
        c.sucursal_id as "sucursalId",s.nombre as sucursal,c.activo as activo,
        coalesce((select cs.opening_amount from cash_register_session cs where cs.tenant_id=c.tenant_id and cs.empresa_id=c.empresa_id and cs.cash_register_id=c.id and cs.business_date<=:hasta order by cs.business_date,cs.opened_at,cs.id limit 1),0) as "saldoInicial",
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta and m.tipo_movimiento='INCOME' then m.monto else 0 end),0) as entradas,
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta and m.tipo_movimiento='EXPENSE' then m.monto else 0 end),0) as salidas,
        coalesce((select cs.opening_amount from cash_register_session cs where cs.tenant_id=c.tenant_id and cs.empresa_id=c.empresa_id and cs.cash_register_id=c.id and cs.business_date<=:hasta order by cs.business_date,cs.opened_at,cs.id limit 1),0)
          +coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta then case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end),0) as "saldoActual"
      from cajas c join monedas mo on mo.id=c.moneda_id and mo.empresa_id=c.empresa_id join sucursales s on s.id=c.sucursal_id and s.empresa_id=c.empresa_id
      left join movimientos_financieros m on m.tenant_id=c.tenant_id and m.empresa_id=c.empresa_id and m.caja_id=c.id
      where c.tenant_id=:tenant and c.empresa_id=:empresa and (:moneda is null or c.moneda_id=:moneda) and (:activo is null or c.activo=:activo)
        and (:sucursal is null or c.sucursal_id=:sucursal) and (:todas=true or c.sucursal_id in (:sucursales))
        and (:buscar='' or lower(c.nombre) like lower(concat('%',:buscar,'%')) or lower(c.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(c.descripcion,'')) like lower(concat('%',:buscar,'%')))
      group by c.id,c.nombre,c.moneda_id,mo.codigo_iso,mo.simbolo,c.sucursal_id,s.nombre,c.activo
      order by c.nombre,c.id
      """,countQuery="""
      select count(*) from cajas c where c.tenant_id=:tenant and c.empresa_id=:empresa and (:moneda is null or c.moneda_id=:moneda) and (:activo is null or c.activo=:activo)
        and (:sucursal is null or c.sucursal_id=:sucursal) and (:todas=true or c.sucursal_id in (:sucursales))
        and (:buscar='' or lower(c.nombre) like lower(concat('%',:buscar,'%')) or lower(c.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(c.descripcion,'')) like lower(concat('%',:buscar,'%')))
      """,nativeQuery=true)
    Page<SaldoCuentaProjection> saldosCajas(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("hasta")LocalDate hasta,
        @Param("buscar")String buscar,@Param("moneda")UUID moneda,@Param("activo")Boolean activo,@Param("sucursal")UUID sucursal,
        @Param("todas")boolean todas,@Param("sucursales")Set<UUID> sucursales,Pageable pageable);

    @Query(value="""
      select b.moneda_id as "monedaId",mo.codigo_iso as moneda,
        coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta then case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end),0) as total
      from cuentas_bancarias b join monedas mo on mo.id=b.moneda_id and mo.empresa_id=b.empresa_id
      left join movimientos_financieros m on m.tenant_id=b.tenant_id and m.empresa_id=b.empresa_id and m.cuenta_bancaria_id=b.id
      where b.tenant_id=:tenant and b.empresa_id=:empresa and (:moneda is null or b.moneda_id=:moneda) and (:activo is null or b.activo=:activo)
        and (:buscar='' or lower(b.banco_nombre) like lower(concat('%',:buscar,'%')) or lower(b.nombre_cuenta) like lower(concat('%',:buscar,'%')) or b.numero_cuenta_ultimos4 like concat('%',:buscar,'%') or lower(b.codigo) like lower(concat('%',:buscar,'%')))
      group by b.moneda_id,mo.codigo_iso
      """,nativeQuery=true)
    List<TotalSaldoMonedaProjection> totalesBancarios(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("hasta")LocalDate hasta,@Param("buscar")String buscar,@Param("moneda")UUID moneda,@Param("activo")Boolean activo);

    @Query(value="""
      select x."monedaId",x.moneda,sum(x.saldo) as total from (
        select c.id,c.moneda_id as "monedaId",mo.codigo_iso as moneda,
          coalesce((select cs.opening_amount from cash_register_session cs where cs.tenant_id=c.tenant_id and cs.empresa_id=c.empresa_id and cs.cash_register_id=c.id and cs.business_date<=:hasta order by cs.business_date,cs.opened_at,cs.id limit 1),0)
          +coalesce(sum(case when m.estado='REGISTERED' and m.fecha<=:hasta then case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end),0) as saldo
        from cajas c join monedas mo on mo.id=c.moneda_id and mo.empresa_id=c.empresa_id
        left join movimientos_financieros m on m.tenant_id=c.tenant_id and m.empresa_id=c.empresa_id and m.caja_id=c.id
        where c.tenant_id=:tenant and c.empresa_id=:empresa and (:moneda is null or c.moneda_id=:moneda) and (:activo is null or c.activo=:activo)
          and (:sucursal is null or c.sucursal_id=:sucursal) and (:todas=true or c.sucursal_id in (:sucursales))
          and (:buscar='' or lower(c.nombre) like lower(concat('%',:buscar,'%')) or lower(c.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(c.descripcion,'')) like lower(concat('%',:buscar,'%')))
        group by c.id,c.moneda_id,mo.codigo_iso
      ) x group by x."monedaId",x.moneda
      """,nativeQuery=true)
    List<TotalSaldoMonedaProjection> totalesCajas(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("hasta")LocalDate hasta,@Param("buscar")String buscar,@Param("moneda")UUID moneda,@Param("activo")Boolean activo,
        @Param("sucursal")UUID sucursal,@Param("todas")boolean todas,@Param("sucursales")Set<UUID> sucursales);

    @Query(value="""
      with ordered as (
        select m.id,m.fecha,m.creado_en as "registradoEn",
          case when m.tipo_origen='SUPPLIER_PAYMENT' then coalesce((select p.internal_number from supplier_payment p where p.id=m.origen_id),'—')
               when m.tipo_origen='OPENING_BALANCE' then 'SALDO INICIAL' else coalesce(m.referencia,'—') end as documento,
          m.concepto,m.tipo_movimiento as tipo,m.monto,m.estado,m.tipo_origen as origen,m.origen_id as "origenId",
          :saldoInicial + sum(case when m.estado='REGISTERED' then case when m.tipo_movimiento='INCOME' then m.monto else -m.monto end else 0 end)
            over(order by m.fecha,m.creado_en,m.id) as saldo
        from movimientos_financieros m where m.tenant_id=:tenant and m.empresa_id=:empresa and m.tipo_cuenta=:tipo
          and ((:tipo='BANK_ACCOUNT' and m.cuenta_bancaria_id=:cuenta) or (:tipo='CASH_REGISTER' and m.caja_id=:cuenta)) and m.fecha<=:hasta
      ) select * from ordered where (:desde is null or fecha>=:desde) and (:buscar='' or lower(concepto) like lower(concat('%',:buscar,'%')) or lower(documento) like lower(concat('%',:buscar,'%')))
        and (:direccion is null or tipo=:direccion) order by fecha desc,"registradoEn" desc,id desc
      """,countQuery="""
      select count(*) from movimientos_financieros m where m.tenant_id=:tenant and m.empresa_id=:empresa and m.tipo_cuenta=:tipo
        and ((:tipo='BANK_ACCOUNT' and m.cuenta_bancaria_id=:cuenta) or (:tipo='CASH_REGISTER' and m.caja_id=:cuenta))
        and m.fecha<=:hasta and (:desde is null or m.fecha>=:desde) and (:buscar='' or lower(m.concepto) like lower(concat('%',:buscar,'%'))
          or lower(case when m.tipo_origen='SUPPLIER_PAYMENT' then coalesce((select p.internal_number from supplier_payment p where p.id=m.origen_id),'—')
            when m.tipo_origen='OPENING_BALANCE' then 'SALDO INICIAL' else coalesce(m.referencia,'—') end) like lower(concat('%',:buscar,'%')))
        and (:direccion is null or m.tipo_movimiento=:direccion)
      """,nativeQuery=true)
    Page<MovimientoSaldoProjection> detalleSaldo(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("tipo")String tipo,
        @Param("cuenta")UUID cuenta,@Param("saldoInicial")java.math.BigDecimal saldoInicial,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,
        @Param("buscar")String buscar,@Param("direccion")String direccion,Pageable pageable);
}
