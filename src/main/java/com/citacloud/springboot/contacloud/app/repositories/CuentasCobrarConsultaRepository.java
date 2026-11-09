package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.CarteraCobrarDto.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CuentasCobrarConsultaRepository {
    private static final UUID SIN_SUCURSAL = new UUID(0,0);
    private final NamedParameterJdbcTemplate jdbc;

    public CuentasCobrarConsultaRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Alcance(UUID tenant, UUID empresa, boolean todasSucursales, Set<UUID> sucursales, ZoneId zona) {}
    public record Consulta(Alcance alcance, Filtro filtro, LocalDate corte) {}

    private static final String ORIGEN = """
        WITH origen AS (
            SELECT a.id, a.customer_id cliente_id, a.sales_invoice_id factura_id, a.sales_debit_note_id nota_id,
                   coalesce(n.debit_note_number,f.internal_number) documento, coalesce(n.issue_date,f.invoice_date) fecha,
                   a.due_date vencimiento, coalesce(nullif(f.customer_name,''),'Consumidor Final') cliente,
                   f.customer_identification identificacion, f.currency_code moneda, s.nombre sucursal,
                   CASE WHEN n.id IS NOT NULL OR a.debit_note_origin THEN 'Crédito' ELSE f.payment_condition END condicion,
                   CASE WHEN n.id IS NOT NULL THEN f.internal_number END referencia,
                   CASE WHEN a.debit_note_origin AND n.id IS NULL AND f.payment_condition<>'Crédito'
                        THEN a.original_amount ELSE coalesce(n.amount,f.total) END total,
                   a.original_amount deuda, a.applied_amount aplicado_actual,
                   a.debit_note_origin AND n.id IS NULL legado,
                   (a.status='VOIDED' OR f.status='VOIDED' OR coalesce(n.status='VOIDED',false)) anulado,
                   coalesce(n.confirmed_at,f.confirmed_at) confirmado, n.annulled_at anulado_en
              FROM customer_accounts_receivable a
              JOIN sales_invoice f ON f.tenant_id=a.tenant_id AND f.empresa_id=a.empresa_id AND f.id=a.sales_invoice_id
              LEFT JOIN sales_debit_note n ON n.tenant_id=a.tenant_id AND n.empresa_id=a.empresa_id AND n.id=a.sales_debit_note_id
              JOIN sucursales s ON s.tenant_id=f.tenant_id AND s.empresa_id=f.empresa_id AND s.id=f.branch_id
             WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa
               AND (:todas OR f.branch_id IN (:sucursales))
               AND (f.payment_condition='Crédito' OR n.id IS NOT NULL OR a.debit_note_origin)
               AND (cast(:cuenta AS uuid) IS NULL OR a.id=:cuenta)
               AND (cast(:cliente AS uuid) IS NULL OR a.customer_id=:cliente)
               AND (cast(:desde AS date) IS NULL OR a.due_date>=:desde)
               AND (cast(:hasta AS date) IS NULL OR a.due_date<=:hasta)
               AND (:sinBusqueda OR coalesce(n.debit_note_number,f.internal_number) ILIKE :texto ESCAPE '!'
                    OR f.customer_name ILIKE :texto ESCAPE '!' OR f.customer_identification ILIKE :texto ESCAPE '!'
                    OR (:tieneDigitos AND f.customer_identification_normalized LIKE :identificacion))
        )
        """;

    private static final String MOVIMIENTOS = """
        , movimientos AS (
            SELECT o.id cuenta_id, r.internal_number documento, 'Recibo' tipo, a.applied_at fecha, a.amount importe,
                   least(a.reversed_at,r.voided_at) reversion, (a.reversed OR r.status='VOIDED') revertida
              FROM origen o JOIN customer_receipt_application a ON a.accounts_receivable_id=o.id
                   AND a.tenant_id=:tenant AND a.empresa_id=:empresa
              JOIN customer_receipt r ON r.id=a.customer_receipt_id AND r.tenant_id=a.tenant_id AND r.empresa_id=a.empresa_id
            UNION ALL
            SELECT o.id, n.credit_note_number, 'Nota de crédito', n.confirmed_at, n.applied_receivable_amount,
                   n.annulled_at, n.status='VOIDED'
              FROM origen o JOIN sales_credit_note n ON o.nota_id IS NULL AND n.sales_invoice_id=o.factura_id
                   AND n.tenant_id=:tenant AND n.empresa_id=:empresa
             WHERE n.applied_receivable_amount>0
        )
        """;

    private static final String ACTUAL = """
        , saldos AS (
            SELECT o.*, aplicado_actual aplicado, CASE WHEN anulado THEN 0 ELSE deuda-aplicado_actual END saldo,
                   anulado baja FROM origen o
        )
        """;

    private static final String HISTORICO = """
        , aplicaciones_corte AS (
            SELECT cuenta_id,
                   coalesce(sum(importe) FILTER (WHERE NOT revertida),0) aplicado_hoy,
                   coalesce(sum(importe) FILTER (WHERE fecha<:fin AND (NOT revertida OR reversion>=:fin)),0) aplicado_corte,
                   bool_or(revertida AND reversion IS NULL) historial_incompleto
              FROM movimientos GROUP BY cuenta_id
        ), historico AS (
            SELECT o.*, coalesce(m.aplicado_corte,0) aplicado,
                   (o.anulado AND (o.anulado_en IS NULL OR o.anulado_en<:fin)) baja,
                   (o.legado OR o.deuda<>o.total OR o.aplicado_actual<>coalesce(m.aplicado_hoy,0)
                    OR (o.anulado AND o.anulado_en IS NULL) OR coalesce(m.historial_incompleto,false)) incompleto
              FROM origen o LEFT JOIN aplicaciones_corte m ON m.cuenta_id=o.id
             WHERE o.confirmado<:fin AND o.fecha<=:corte
        ), saldos AS (
            SELECT h.*, CASE WHEN baja THEN 0 ELSE deuda-aplicado END saldo FROM historico h
        )
        """;

    private static final String ESTADOS = """
        , estados AS (
            SELECT s.*, CASE WHEN baja THEN 'ANULADO' WHEN saldo=0 THEN 'PAGADO'
                    WHEN vencimiento<:corte THEN 'VENCIDO' WHEN aplicado>0 THEN 'PARCIAL'
                    ELSE 'PENDIENTE' END estado FROM saldos s
        ), cartera AS (
            SELECT * FROM estados WHERE :estado='TODOS' OR (:estado='ACTIVA' AND saldo>0) OR estado=:estado
        )
        """;

    public Page<Cuenta> cuentas(Consulta consulta, Pageable pagina) {
        var p=paginar(parametros(consulta,null),pagina);
        var datos=jdbc.query(ORIGEN+ACTUAL+ESTADOS+
            " SELECT * FROM cartera ORDER BY vencimiento,documento,id LIMIT :limite OFFSET :inicio",p,this::cuenta);
        long total=jdbc.queryForObject(ORIGEN+ACTUAL+ESTADOS+" SELECT count(*) FROM cartera",p,Long.class);
        return new PageImpl<>(datos,pagina,total);
    }

    public List<Cuenta> carteraCompleta(Consulta consulta) {
        return jdbc.query(ORIGEN+ACTUAL+ESTADOS+" SELECT * FROM cartera ORDER BY moneda,vencimiento,documento,id",
            parametros(consulta,null),this::cuenta);
    }

    public Indicadores indicadores(Consulta consulta) {
        var p=parametros(consulta,null);
        var monedas=jdbc.query(ORIGEN+ACTUAL+ESTADOS+"""
            SELECT moneda, sum(saldo) saldo, sum(total) total_documentos,
                   coalesce(sum(saldo) FILTER (WHERE vencimiento>=:corte),0) corriente,
                   coalesce(sum(saldo) FILTER (WHERE vencimiento<:corte),0) vencido
              FROM cartera GROUP BY moneda ORDER BY moneda
            """,p,(rs,n)->new Moneda(rs.getString("moneda"),rs.getBigDecimal("saldo"),rs.getBigDecimal("corriente"),
                rs.getBigDecimal("vencido"),rs.getBigDecimal("total_documentos")));
        long clientes=jdbc.queryForObject(ORIGEN+ACTUAL+ESTADOS+
            " SELECT count(DISTINCT cliente_id) FROM cartera WHERE saldo>0",p,Long.class);
        return new Indicadores(monedas,clientes);
    }

    public Page<ClienteCartera> clientes(Consulta consulta, Pageable pagina) {
        var p=paginar(parametros(consulta,null),pagina);
        var filas=jdbc.query(ORIGEN+ACTUAL+ESTADOS+"""
            SELECT cliente_id,max(cliente) cliente,moneda,sum(saldo) saldo,
                   coalesce(sum(saldo) FILTER (WHERE vencimiento>=:corte),0) corriente,
                   coalesce(sum(saldo) FILTER (WHERE vencimiento<:corte),0) vencido
              FROM cartera WHERE saldo>0 GROUP BY cliente_id,moneda
             ORDER BY max(cliente),cliente_id,moneda LIMIT :limite OFFSET :inicio
            """,p,(rs,n)->new ClienteCartera(cliente(rs),rs.getString("moneda"),rs.getBigDecimal("corriente"),
                rs.getBigDecimal("vencido"),rs.getBigDecimal("saldo")));
        long total=jdbc.queryForObject(ORIGEN+ACTUAL+ESTADOS+
            " SELECT count(*) FROM (SELECT cliente_id,moneda FROM cartera WHERE saldo>0 GROUP BY cliente_id,moneda) x",p,Long.class);
        return new PageImpl<>(filas,pagina,total);
    }

    public List<Cliente> opciones(Consulta consulta,int inicio,int limite) {
        return jdbc.query(ORIGEN+"""
            SELECT cliente_id,max(cliente) cliente FROM origen WHERE cliente_id IS NOT NULL
             GROUP BY cliente_id ORDER BY max(cliente),cliente_id LIMIT :limite OFFSET :inicio
            """,parametros(consulta,null).addValue("inicio",inicio).addValue("limite",limite),(rs,n)->cliente(rs));
    }

    public Optional<Cuenta> cuenta(Consulta consulta,UUID id) {
        return jdbc.query(ORIGEN+ACTUAL+ESTADOS+" SELECT * FROM cartera",parametros(consulta,id),this::cuenta)
            .stream().findFirst();
    }

    public List<Aplicacion> aplicaciones(Consulta consulta,UUID id) {
        return jdbc.query(ORIGEN+MOVIMIENTOS+" SELECT * FROM movimientos ORDER BY fecha,documento,tipo",
            parametros(consulta,id),(rs,n)->new Aplicacion(rs.getString("documento"),rs.getString("tipo"),
                rs.getObject("fecha",OffsetDateTime.class).atZoneSameInstant(consulta.alcance().zona()).toLocalDateTime(),
                rs.getBigDecimal("importe"),rs.getBoolean("revertida")));
    }

    public boolean corteReconstruible(Consulta consulta) {
        return !Boolean.TRUE.equals(jdbc.queryForObject(ORIGEN+MOVIMIENTOS+HISTORICO+
            " SELECT EXISTS(SELECT 1 FROM saldos WHERE incompleto OR saldo<0)",parametros(consulta,null),Boolean.class));
    }

    public List<Edad> antiguedad(Consulta consulta,boolean historico) {
        String base=historico?ORIGEN+MOVIMIENTOS+HISTORICO:ORIGEN+ACTUAL;
        return jdbc.query(base+ESTADOS+"""
            SELECT cliente_id,max(cliente) cliente,moneda,sum(saldo) total,
                   coalesce(sum(saldo) FILTER (WHERE cast(:corte AS date)-vencimiento<=0),0) corriente,
                   coalesce(sum(saldo) FILTER (WHERE cast(:corte AS date)-vencimiento BETWEEN 1 AND 30),0) hasta30,
                   coalesce(sum(saldo) FILTER (WHERE cast(:corte AS date)-vencimiento BETWEEN 31 AND 60),0) hasta60,
                   coalesce(sum(saldo) FILTER (WHERE cast(:corte AS date)-vencimiento BETWEEN 61 AND 90),0) hasta90,
                   coalesce(sum(saldo) FILTER (WHERE cast(:corte AS date)-vencimiento>90),0) mas90
              FROM cartera WHERE saldo>0 GROUP BY cliente_id,moneda ORDER BY moneda,max(cliente),cliente_id
            """,parametros(consulta,null),(rs,n)->new Edad(cliente(rs),rs.getString("moneda"),rs.getBigDecimal("corriente"),
                rs.getBigDecimal("hasta30"),rs.getBigDecimal("hasta60"),rs.getBigDecimal("hasta90"),
                rs.getBigDecimal("mas90"),rs.getBigDecimal("total")));
    }

    private static MapSqlParameterSource parametros(Consulta consulta,UUID cuenta) {
        var a=consulta.alcance();
        var f=consulta.filtro();
        String texto=f.buscar();
        String patron="%"+texto.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String digitos=texto.matches("[0-9 .-]+")?texto.replaceAll("[^0-9]",""):"";
        return new MapSqlParameterSource().addValue("tenant",a.tenant()).addValue("empresa",a.empresa())
            .addValue("todas",a.todasSucursales()).addValue("sucursales",a.sucursales().isEmpty()?Set.of(SIN_SUCURSAL):a.sucursales())
            .addValue("cuenta",cuenta,Types.OTHER).addValue("cliente",f.cliente(),Types.OTHER)
            .addValue("desde",f.venceDesde(),Types.DATE).addValue("hasta",f.venceHasta(),Types.DATE)
            .addValue("sinBusqueda",texto.isEmpty()).addValue("texto",patron)
            .addValue("tieneDigitos",!digitos.isEmpty()).addValue("identificacion","%"+digitos+"%")
            .addValue("estado",f.estado().name()).addValue("corte",consulta.corte(),Types.DATE)
            .addValue("fin",consulta.corte().plusDays(1).atStartOfDay(a.zona()).toOffsetDateTime());
    }

    private static MapSqlParameterSource paginar(MapSqlParameterSource p,Pageable pagina) {
        return p.addValue("limite",pagina.getPageSize()).addValue("inicio",pagina.getOffset());
    }

    private static Cliente cliente(ResultSet rs) throws SQLException {
        return new Cliente(rs.getObject("cliente_id",UUID.class),rs.getString("cliente"));
    }

    private Cuenta cuenta(ResultSet rs,int fila) throws SQLException {
        return new Cuenta(rs.getObject("id",UUID.class),rs.getString("documento"),rs.getObject("fecha",LocalDate.class),
            rs.getObject("vencimiento",LocalDate.class),cliente(rs),rs.getString("identificacion"),rs.getString("moneda"),
            rs.getString("sucursal"),rs.getString("condicion"),rs.getString("referencia"),rs.getBigDecimal("total"),
            rs.getBigDecimal("deuda"),rs.getBigDecimal("aplicado"),rs.getBigDecimal("saldo"),Estado.valueOf(rs.getString("estado")));
    }
}
