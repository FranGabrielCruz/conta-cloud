package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.models.FacturaVenta;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class CobroClienteRepository {
    private final JdbcTemplate jdbc;
    public CobroClienteRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** Serialize customer mutations so the captured balance belongs to this confirmed operation. */
    public void bloquearClienteImpresion(UUID tenant,UUID empresa,UUID cliente) {
        jdbc.queryForObject("SELECT id FROM customer WHERE tenant_id=? AND empresa_id=? AND id=? FOR UPDATE",UUID.class,tenant,empresa,cliente);
    }
    public void conservarImpresion(UUID id,UUID tenant,UUID empresa,UUID caja,BigDecimal anterior,BigDecimal pendiente) {
        new PaymentReceiptSnapshotRepository(jdbc).conservar(detalle(id,tenant,empresa),tenant,empresa,caja,anterior,pendiente);
    }


    public void conservarImpresionVentaPos(UUID id,UUID tenant,UUID empresa,UUID caja) {
        var recibo=detalle(id,tenant,empresa);
        new PaymentReceiptSnapshotRepository(jdbc).conservar(recibo,tenant,empresa,caja,null,null,
            recibo.medios().stream().map(m->new com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument.PaymentMethod(
                m.medio(),m.monto(),m.referencia())).toList());
    }

    public void prepararCuentas(UUID tenant,UUID empresa,UUID cliente){
        jdbc.update("""
            INSERT INTO customer_accounts_receivable(tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,original_amount,applied_amount,due_date,status)
            SELECT f.tenant_id,f.empresa_id,f.id,f.customer_id,f.currency_id,f.total,0,
                   f.invoice_date+coalesce(c.credit_days,0),'PENDING'
              FROM sales_invoice f JOIN customer c ON c.id=f.customer_id AND c.tenant_id=f.tenant_id AND c.empresa_id=f.empresa_id
             WHERE f.tenant_id=? AND f.empresa_id=? AND f.customer_id=? AND f.status='CONFIRMED'
               AND lower(f.payment_condition)<>'contado'
               AND NOT EXISTS(SELECT 1 FROM customer_accounts_receivable a WHERE a.tenant_id=f.tenant_id AND a.empresa_id=f.empresa_id AND a.sales_invoice_id=f.id AND a.sales_debit_note_id IS NULL)
            ON CONFLICT(tenant_id,empresa_id,sales_invoice_id) WHERE sales_debit_note_id IS NULL DO NOTHING
            """,tenant,empresa,cliente);
    }

    public BigDecimal saldoPendiente(UUID tenant,UUID empresa,UUID cliente,UUID moneda){
        BigDecimal saldo=jdbc.queryForObject("""
            SELECT coalesce(sum(original_amount-applied_amount),0)
              FROM customer_accounts_receivable
             WHERE tenant_id=? AND empresa_id=? AND customer_id=? AND currency_id=?
               AND status IN('PENDING','PARTIALLY_PAID')
            """,BigDecimal.class,tenant,empresa,cliente,moneda);
        return saldo==null?BigDecimal.ZERO:saldo;
    }

    public void crearCuenta(UUID tenant,UUID empresa,UUID factura,UUID cliente,UUID moneda,
            BigDecimal total,LocalDate vencimiento){
        jdbc.update("""
            INSERT INTO customer_accounts_receivable(tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,
              original_amount,applied_amount,due_date,status)
            VALUES(?,?,?,?,?,?,0,?,'PENDING')
            """,tenant,empresa,factura,cliente,moneda,total,vencimiento);
    }

    public Page<FacturaPendienteCobroDto> facturas(UUID tenant,UUID empresa,UUID cliente,UUID moneda,int page,int size){
        String where=" WHERE a.tenant_id=? AND a.empresa_id=? AND a.customer_id=? AND a.currency_id=? AND a.status IN('PENDING','PARTIALLY_PAID') AND a.original_amount>a.applied_amount ";
        Long total=jdbc.queryForObject("SELECT count(*) FROM customer_accounts_receivable a"+where,Long.class,tenant,empresa,cliente,moneda);
        List<FacturaPendienteCobroDto> rows=jdbc.query("""
            SELECT a.id,a.sales_invoice_id,coalesce(n.debit_note_number,f.internal_number) internal_number,coalesce(n.issue_date,f.invoice_date) invoice_date,a.due_date,a.currency_id,f.currency_code,
                   a.original_amount-a.applied_amount AS balance
              FROM customer_accounts_receivable a JOIN sales_invoice f ON f.id=a.sales_invoice_id AND f.tenant_id=a.tenant_id AND f.empresa_id=a.empresa_id LEFT JOIN sales_debit_note n ON n.id=a.sales_debit_note_id AND n.tenant_id=a.tenant_id AND n.empresa_id=a.empresa_id
            """+where+" ORDER BY a.due_date,a.sales_invoice_id,a.id LIMIT ? OFFSET ?",this::factura,
            tenant,empresa,cliente,moneda,size,page*size);
        return new PageImpl<>(rows,PageRequest.of(page,size),total==null?0:total);
    }

    public Page<CobroClienteDto> buscar(UUID tenant,UUID empresa,boolean todasSucursales,Set<UUID> sucursales,String texto,String identificacion,
            LocalDate desde,LocalDate hasta,EstadoReciboCliente estado,int page,int size){
        Set<UUID> alcance=sucursales==null||sucursales.isEmpty()?Set.of(new UUID(0,0)):sucursales;
        String filtroSucursal=todasSucursales?"":" AND r.branch_id IN ("+String.join(",",Collections.nCopies(alcance.size(),"?"))+") ";
        String sql="""
            FROM customer_receipt r
            WHERE r.tenant_id=? AND r.empresa_id=?
              AND (CAST(? AS date) IS NULL OR r.receipt_date>=?) AND (CAST(? AS date) IS NULL OR r.receipt_date<=?)
              AND (CAST(? AS varchar) IS NULL OR r.status=?)
              AND (?='' OR lower(r.internal_number) LIKE concat('%',lower(?),'%')
                OR lower(r.customer_name_snapshot) LIKE concat('%',lower(?),'%')
                OR (?<>'' AND r.customer_identification_normalized LIKE concat('%',?,'%'))
                OR EXISTS(SELECT 1 FROM customer_receipt_application a WHERE a.tenant_id=r.tenant_id AND a.empresa_id=r.empresa_id AND a.customer_receipt_id=r.id AND lower(a.invoice_number_snapshot) LIKE concat('%',lower(?),'%'))
                OR EXISTS(SELECT 1 FROM sales_invoice f WHERE f.tenant_id=r.tenant_id AND f.empresa_id=r.empresa_id AND f.id=r.pos_invoice_id AND lower(f.internal_number) LIKE concat('%',lower(?),'%')))
            """+filtroSucursal;
        List<Object> values=new ArrayList<>();Collections.addAll(values,tenant,empresa,desde,desde,hasta,hasta,
            estado==null?null:estado.name(),estado==null?null:estado.name(),texto,texto,texto,identificacion,identificacion,texto,texto);
        if(!todasSucursales)values.addAll(alcance);
        Object[] args=values.toArray();
        Long total=jdbc.queryForObject("SELECT count(*) "+sql,Long.class,args);
        List<CobroClienteDto> rows=jdbc.query("SELECT r.* "+sql+" ORDER BY CASE WHEN lower(r.internal_number)=lower(?) OR EXISTS(SELECT 1 FROM customer_receipt_application a WHERE a.tenant_id=r.tenant_id AND a.empresa_id=r.empresa_id AND a.customer_receipt_id=r.id AND lower(a.invoice_number_snapshot)=lower(?)) THEN 0 ELSE 1 END,r.confirmed_at DESC,r.id DESC LIMIT ? OFFSET ?",
            (rs,n)->resumen(rs),append(args,texto,texto,size,page*size));
        return new PageImpl<>(rows,PageRequest.of(page,size),total==null?0:total);
    }


    public Page<ReciboHistorialDto.Resumen> historial(UUID tenant, UUID empresa, boolean todas, Set<UUID> sucursales,
            String texto, String identificacion, LocalDate desde, LocalDate hasta, Boolean anulados, int page, int size) {
        if (!todas && sucursales.isEmpty()) return Page.empty(PageRequest.of(page,size));
        String where = """
            FROM customer_receipt r
            LEFT JOIN sales_invoice f ON f.id=r.pos_invoice_id AND f.tenant_id=r.tenant_id AND f.empresa_id=r.empresa_id
            JOIN empresas e ON e.id=r.empresa_id AND e.tenant_id=r.tenant_id
            WHERE r.tenant_id=? AND r.empresa_id=?
              AND (CAST(? AS date) IS NULL OR r.receipt_date>=?)
              AND (CAST(? AS date) IS NULL OR r.receipt_date<=?)
              AND (CAST(? AS boolean) IS NULL OR (r.status='VOIDED')=?)
              AND (?='' OR r.internal_number ILIKE ? OR r.customer_name_snapshot ILIKE ? OR f.internal_number ILIKE ?
                OR (?<>'' AND r.customer_identification_normalized LIKE concat('%',?,'%'))
                OR EXISTS(SELECT 1 FROM customer_receipt_application a
                    WHERE a.tenant_id=r.tenant_id AND a.empresa_id=r.empresa_id AND a.customer_receipt_id=r.id
                      AND a.invoice_number_snapshot ILIKE ?)
                OR EXISTS(SELECT 1 FROM customer_receipt_payment p
                    WHERE p.tenant_id=r.tenant_id AND p.empresa_id=r.empresa_id AND p.customer_receipt_id=r.id
                      AND p.method_type IN ('BANK_TRANSFER','CHECK')
                      AND p.reference ~ '^[[:alpha:]]+-[[:alnum:]-]{1,40}$'
                      AND p.reference !~* '(cvv|cvc|pin|token|password|([0-9]-?){13})'
                      AND p.reference ILIKE ?))
            """ + (todas ? "" : " AND r.branch_id IN ("+String.join(",", Collections.nCopies(sucursales.size(),"?"))+") ");
        String match="%"+texto.replace("!", "!!").replace("%","!%").replace("_","!_")+"%";
        where=where.replace("ILIKE ?", "ILIKE ? ESCAPE '!'");
        List<Object> args=new ArrayList<>();
        Collections.addAll(args,tenant,empresa,desde,desde,hasta,hasta,anulados,anulados,texto,match,match,match,identificacion,identificacion,match,match);
        if(!todas)args.addAll(sucursales);
        Long count=jdbc.queryForObject("SELECT count(*) "+where,Long.class,args.toArray());
        List<ReciboHistorialDto.Resumen> data=jdbc.query("""
            SELECT r.id,r.internal_number,r.customer_name_snapshot,r.customer_id,r.currency_code_snapshot,
                   r.received_amount,r.status,r.confirmed_at,e.zona_horaria,f.internal_number pos_number,
                   ARRAY(SELECT DISTINCT a.invoice_number_snapshot FROM customer_receipt_application a
                         WHERE a.tenant_id=r.tenant_id AND a.empresa_id=r.empresa_id AND a.customer_receipt_id=r.id
                         ORDER BY a.invoice_number_snapshot) documentos
            """+where+" ORDER BY r.confirmed_at DESC,r.id DESC LIMIT ? OFFSET ?",(rs,n)->{
            String pos=rs.getString("pos_number");
            List<String> docs=pos==null?List.of((String[])rs.getArray("documentos").getArray()):List.of(pos);
            return new ReciboHistorialDto.Resumen(uuid(rs,"id"),rs.getString("internal_number"),
                rs.getObject("confirmed_at",OffsetDateTime.class).atZoneSameInstant(ZoneId.of(rs.getString("zona_horaria"))).toLocalDateTime(),
                uuid(rs,"customer_id")==null?"Consumidor Final":rs.getString("customer_name_snapshot"),rs.getString("currency_code_snapshot"),
                rs.getBigDecimal("received_amount"),EstadoReciboCliente.valueOf(rs.getString("status")),docs);
        },append(args.toArray(),size,(long)page*size));
        return new PageImpl<>(data,PageRequest.of(page,size),count==null?0:count);
    }

    // La anulación efectiva vuelve a validar bajo bloqueo.
    public Set<UUID> recibosAnulables(UUID tenant, UUID empresa, List<UUID> ids,
            boolean todasSucursales, Set<UUID> sucursales) {
        if (ids.isEmpty()) return Set.of();
        String filtroTurno = todasSucursales ? "" : sucursales.isEmpty() ? " AND FALSE" :
            " AND t.branch_id IN ("+String.join(",",Collections.nCopies(sucursales.size(),"?"))+")";
        String sql = """
            SELECT r.id FROM customer_receipt r
             WHERE r.tenant_id=? AND r.empresa_id=? AND r.id IN (%s)
               AND r.status<>'VOIDED' AND r.pos_invoice_id IS NULL
               AND EXISTS (SELECT 1 FROM customer_receipt_payment p
                            WHERE p.tenant_id=r.tenant_id AND p.empresa_id=r.empresa_id AND p.customer_receipt_id=r.id)
               AND NOT EXISTS (
                    SELECT 1 FROM customer_receipt_payment p
                    LEFT JOIN movimientos_financieros m ON m.id=p.financial_movement_id
                         AND m.tenant_id=p.tenant_id AND m.empresa_id=p.empresa_id
                         AND m.tipo_movimiento='INCOME' AND m.estado='REGISTERED'
                         AND m.tipo_origen='CUSTOMER_RECEIPT' AND m.origen_id=p.id
                    WHERE p.tenant_id=r.tenant_id AND p.empresa_id=r.empresa_id AND p.customer_receipt_id=r.id
                      AND (m.id IS NULL OR EXISTS (
                           SELECT 1 FROM bank_reconciliation_match c WHERE c.tenant_id=m.tenant_id
                               AND c.empresa_id=m.empresa_id AND c.financial_movement_id=m.id)
                           OR EXISTS (
                               SELECT 1 FROM (VALUES(p.cash_register_session_id),(m.cash_register_session_id)) AS vinculados(id)
                               WHERE vinculados.id IS NOT NULL AND NOT EXISTS (
                                   SELECT 1 FROM cash_register_session t WHERE t.id=vinculados.id
                                      AND t.tenant_id=p.tenant_id AND t.empresa_id=p.empresa_id AND t.status='OPEN'%s))))
            """.formatted(String.join(",",Collections.nCopies(ids.size(),"?")),filtroTurno);
        List<Object> args=new ArrayList<>();
        Collections.addAll(args,tenant,empresa);
        args.addAll(ids);
        if(!todasSucursales)args.addAll(sucursales);
        return Set.copyOf(jdbc.queryForList(sql,UUID.class,args.toArray()));
    }

    public boolean esReciboVentaPos(UUID id,UUID tenant,UUID empresa) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM customer_receipt WHERE id=? AND tenant_id=? AND empresa_id=? AND pos_invoice_id IS NOT NULL)
            """,Boolean.class,id,tenant,empresa));
    }

    public record Anulacion(OffsetDateTime fecha,String usuario) {}
    public Anulacion datosAnulacion(UUID id,UUID tenant,UUID empresa) {
        return jdbc.query("""
            SELECT r.voided_at,u.usuario FROM customer_receipt r
            LEFT JOIN usuarios u ON u.id=r.voided_by AND u.tenant_id=r.tenant_id AND u.empresa_id=r.empresa_id
            WHERE r.id=? AND r.tenant_id=? AND r.empresa_id=? AND r.status='VOIDED'
            """,rs->rs.next()?new Anulacion(rs.getObject(1,OffsetDateTime.class),rs.getString(2)):null,id,tenant,empresa);
    }

    public UUID insertarReciboVentaPos(FacturaVenta factura,String sucursal,String numero) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO customer_receipt(id,tenant_id,empresa_id,internal_number,customer_id,customer_name_snapshot,
                customer_identification_snapshot,customer_identification_normalized,branch_id,branch_name_snapshot,
                currency_id,currency_code_snapshot,receipt_date,received_amount,applied_amount,available_amount,notes,
                status,idempotency_key,payload_fingerprint,confirmed_at,confirmed_by,pos_invoice_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,'APPLIED',?,?,?,?,?)
            """,id,factura.getTenantId(),factura.getEmpresaId(),numero,factura.getClienteId(),factura.getClienteNombre(),
            factura.getClienteIdentificacion(),digitos(factura.getClienteIdentificacion()),factura.getSucursalId(),sucursal,
            factura.getMonedaId(),factura.getMonedaCodigo(),factura.getFecha(),factura.getTotal(),factura.getTotal(),factura.getNota(),
            id,factura.getId().toString(),factura.getConfirmadaEn(),factura.getConfirmadaPor(),factura.getId());
        return id;
    }

    public Page<CobroClienteDto> buscarPorSesion(UUID tenant,UUID empresa,UUID sesion,String texto,
            String identificacion,int page,int size){
        String sql="""
            FROM customer_receipt r
            WHERE r.tenant_id=? AND r.empresa_id=?
              AND EXISTS(SELECT 1 FROM customer_receipt_payment p
                WHERE p.tenant_id=r.tenant_id AND p.empresa_id=r.empresa_id
                  AND p.customer_receipt_id=r.id AND p.cash_register_session_id=?)
              AND (?='' OR lower(r.internal_number) LIKE concat('%',lower(?),'%')
                OR lower(r.customer_name_snapshot) LIKE concat('%',lower(?),'%')
                OR (?<>'' AND r.customer_identification_normalized LIKE concat('%',?,'%'))
                OR EXISTS(SELECT 1 FROM customer_receipt_application a
                  WHERE a.tenant_id=r.tenant_id AND a.empresa_id=r.empresa_id
                    AND a.customer_receipt_id=r.id
                    AND lower(a.invoice_number_snapshot) LIKE concat('%',lower(?),'%')))
            """;
        Object[] args={tenant,empresa,sesion,texto,texto,texto,identificacion,identificacion,texto};
        Long total=jdbc.queryForObject("SELECT count(*) "+sql,Long.class,args);
        List<CobroClienteDto> rows=jdbc.query("SELECT r.* "+sql+" ORDER BY r.confirmed_at DESC,r.id DESC LIMIT ? OFFSET ?",
            (rs,n)->resumen(rs),append(args,size,page*size));
        return new PageImpl<>(rows,PageRequest.of(page,size),total==null?0:total);
    }

    public Optional<CobroClienteDto> buscarIdempotencia(UUID tenant,UUID empresa,UUID key){
        return jdbc.query("SELECT * FROM customer_receipt WHERE tenant_id=? AND empresa_id=? AND idempotency_key=?",(rs,n)->resumen(rs),tenant,empresa,key).stream().findFirst();
    }

    public String fingerprint(UUID tenant,UUID empresa,UUID key){
        return jdbc.query("SELECT payload_fingerprint FROM customer_receipt WHERE tenant_id=? AND empresa_id=? AND idempotency_key=?",rs->rs.next()?rs.getString(1):null,tenant,empresa,key);
    }

    public long siguienteNumero(UUID tenant,UUID empresa){
        Long n=jdbc.queryForObject("INSERT INTO customer_receipt_sequence(tenant_id,empresa_id,last_number) VALUES(?,?,1) ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number=customer_receipt_sequence.last_number+1 RETURNING last_number",Long.class,tenant,empresa);
        return n==null?1:n;
    }

    public void insertarRecibo(UUID id,UUID tenant,UUID empresa,String numero,UUID cliente,String clienteNombre,
            String identificacion,UUID sucursal,String sucursalNombre,UUID moneda,String monedaCodigo,LocalDate fecha,BigDecimal recibido,BigDecimal aplicado,
            String referencia,String observacion,UUID key,String fingerprint,UUID usuario){
        BigDecimal disponible=recibido.subtract(aplicado);String estado=estado(aplicado,disponible).name();
        jdbc.update("""
            INSERT INTO customer_receipt(id,tenant_id,empresa_id,internal_number,customer_id,customer_name_snapshot,
              customer_identification_snapshot,customer_identification_normalized,branch_id,branch_name_snapshot,currency_id,currency_code_snapshot,
              receipt_date,received_amount,applied_amount,available_amount,reference,notes,status,idempotency_key,
              payload_fingerprint,confirmed_at,confirmed_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)
            """,id,tenant,empresa,numero,cliente,clienteNombre,identificacion,digitos(identificacion),sucursal,sucursalNombre,moneda,monedaCodigo,
            fecha,recibido,aplicado,disponible,referencia,observacion,estado,key,fingerprint,usuario);
    }

    public CuentaBloqueada bloquearCuenta(UUID id,UUID tenant,UUID empresa){
        return jdbc.query("""
            SELECT a.id,a.sales_invoice_id,a.customer_id,a.currency_id,a.original_amount-a.applied_amount balance,
                   coalesce(n.debit_note_number,f.internal_number) internal_number,coalesce(n.issue_date,f.invoice_date) invoice_date,coalesce(n.amount,f.total) document_total,n.id debit_note_id FROM customer_accounts_receivable a JOIN sales_invoice f ON f.id=a.sales_invoice_id AND f.tenant_id=a.tenant_id AND f.empresa_id=a.empresa_id LEFT JOIN sales_debit_note n ON n.id=a.sales_debit_note_id AND n.tenant_id=a.tenant_id AND n.empresa_id=a.empresa_id
             WHERE a.id=? AND a.tenant_id=? AND a.empresa_id=? FOR UPDATE OF a
            """,rs->{if(!rs.next())return null;return new CuentaBloqueada(uuid(rs,"id"),uuid(rs,"sales_invoice_id"),uuid(rs,"customer_id"),uuid(rs,"currency_id"),rs.getBigDecimal("balance"),rs.getString("internal_number"),rs.getDate("invoice_date").toLocalDate(),rs.getBigDecimal("document_total"),uuid(rs,"debit_note_id"));},id,tenant,empresa);
    }

    public void aplicarCuenta(CuentaBloqueada c,BigDecimal monto){
        jdbc.update("""
            UPDATE customer_accounts_receivable SET applied_amount=applied_amount+?,
              status=CASE WHEN applied_amount+?=original_amount THEN 'PAID' ELSE 'PARTIALLY_PAID' END,
              version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?
            """,monto,monto,c.id());
    }

    public void insertarAplicacion(UUID id,UUID tenant,UUID empresa,UUID recibo,CuentaBloqueada cuenta,
            BigDecimal monto,UUID usuario){
        jdbc.update("""
            INSERT INTO customer_receipt_application(id,tenant_id,empresa_id,customer_receipt_id,accounts_receivable_id,
              sales_invoice_id,invoice_number_snapshot,invoice_date_snapshot,previous_balance,amount,remaining_balance,applied_by,original_total_snapshot,debit_note_id_snapshot)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,tenant,empresa,recibo,cuenta.id(),cuenta.facturaId(),cuenta.numero(),cuenta.fecha(),cuenta.saldo(),monto,cuenta.saldo().subtract(monto),usuario,cuenta.totalDocumento(),cuenta.notaDebitoId());
    }

    public void insertarMedio(UUID id,UUID tenant,UUID empresa,UUID recibo,UUID metodo,String codigo,String nombre,
            String tipo,BigDecimal monto,UUID caja,UUID sesion,UUID banco,String destino,String referencia,int orden){
        jdbc.update("""
            INSERT INTO customer_receipt_payment(id,tenant_id,empresa_id,customer_receipt_id,payment_method_id,method_code,
              method_name,method_type,amount,cash_register_id,cash_register_session_id,bank_account_id,destination_snapshot,reference,line_order)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,tenant,empresa,recibo,metodo,codigo,nombre,tipo,monto,caja,sesion,banco,destino,referencia,orden);
    }

    public void vincularMovimiento(UUID medio,UUID movimiento){jdbc.update("UPDATE customer_receipt_payment SET financial_movement_id=? WHERE id=?",movimiento,medio);}

    public CobroClienteDto detalle(UUID id,UUID tenant,UUID empresa){
        CobroClienteDto base=jdbc.query("""
            SELECT r.*,u.usuario usuario
              FROM customer_receipt r JOIN usuarios u ON u.id=r.confirmed_by AND u.tenant_id=r.tenant_id AND u.empresa_id=r.empresa_id
             WHERE r.id=? AND r.tenant_id=? AND r.empresa_id=?
            """,rs->{if(!rs.next())return null;return detalleBase(rs);},id,tenant,empresa);
        if(base==null)return null;
        List<CobroClienteDto.Aplicacion> apps=jdbc.query("""
            SELECT id,sales_invoice_id,invoice_number_snapshot,invoice_date_snapshot,applied_at,previous_balance,amount,
                   remaining_balance,reversed,original_total_snapshot,debit_note_id_snapshot
              FROM customer_receipt_application WHERE customer_receipt_id=? AND tenant_id=? AND empresa_id=?
            UNION ALL
            SELECT r.id,f.id,f.internal_number,f.invoice_date,r.confirmed_at,r.received_amount,r.applied_amount,0,
                   false,r.received_amount,NULL::uuid
              FROM customer_receipt r JOIN sales_invoice f ON f.id=r.pos_invoice_id AND f.tenant_id=r.tenant_id AND f.empresa_id=r.empresa_id
             WHERE r.id=? AND r.tenant_id=? AND r.empresa_id=?
             ORDER BY applied_at,id
            """,(rs,n)->new CobroClienteDto.Aplicacion(uuid(rs,"id"),uuid(rs,"sales_invoice_id"),rs.getString("invoice_number_snapshot"),
                rs.getDate("invoice_date_snapshot").toLocalDate(),rs.getObject("applied_at",OffsetDateTime.class),rs.getBigDecimal("previous_balance"),
                rs.getBigDecimal("amount"),rs.getBigDecimal("remaining_balance"),rs.getBoolean("reversed"),rs.getBigDecimal("original_total_snapshot"),uuid(rs,"debit_note_id_snapshot")),id,tenant,empresa,id,tenant,empresa);
        List<CobroClienteDto.Medio> medios=jdbc.query("""
            SELECT id,method_name,destination_snapshot,reference,amount,method_type,line_order
              FROM customer_receipt_payment WHERE customer_receipt_id=? AND tenant_id=? AND empresa_id=?
            UNION ALL
            SELECT a.id,a.method_name,a.bank_name,a.reference,a.amount,a.method_type,a.line_order
              FROM customer_receipt r JOIN sales_payment p ON p.sales_invoice_id=r.pos_invoice_id AND p.tenant_id=r.tenant_id AND p.empresa_id=r.empresa_id
              JOIN sales_payment_application a ON a.sales_payment_id=p.id AND a.tenant_id=p.tenant_id AND a.empresa_id=p.empresa_id
             WHERE r.id=? AND r.tenant_id=? AND r.empresa_id=?
             ORDER BY line_order
            """,(rs,n)->new CobroClienteDto.Medio(uuid(rs,"id"),rs.getString("method_name"),rs.getString("destination_snapshot"),PaymentReceiptSnapshotRepository.safeReference(rs.getString("reference"),rs.getString("method_type")),rs.getBigDecimal("amount")),id,tenant,empresa,id,tenant,empresa);
        return new CobroClienteDto(base.id(),base.numero(),base.clienteId(),base.cliente(),base.identificacion(),base.monedaId(),base.moneda(),base.sucursalId(),base.sucursal(),base.fecha(),base.confirmadoEn(),base.recibido(),base.aplicado(),base.disponible(),base.estado(),base.referencia(),base.observacion(),base.usuario(),base.motivoAnulacion(),apps,medios);
    }

    public List<AplicacionReversion> aplicacionesActivas(UUID recibo,UUID tenant,UUID empresa){
        return jdbc.query("SELECT id,accounts_receivable_id,amount FROM customer_receipt_application WHERE customer_receipt_id=? AND tenant_id=? AND empresa_id=? AND reversed=false ORDER BY accounts_receivable_id",
            (rs,n)->new AplicacionReversion(uuid(rs,"id"),uuid(rs,"accounts_receivable_id"),rs.getBigDecimal("amount")),recibo,tenant,empresa);
    }

    public List<UUID> sesionesDelRecibo(UUID recibo,UUID tenant,UUID empresa){
        return jdbc.query("""
            SELECT DISTINCT cash_register_session_id FROM customer_receipt_payment
             WHERE customer_receipt_id=? AND tenant_id=? AND empresa_id=? AND cash_register_session_id IS NOT NULL
             ORDER BY cash_register_session_id
            """,(rs,n)->uuid(rs,"cash_register_session_id"),recibo,tenant,empresa);
    }
    public List<UUID> movimientos(UUID recibo,UUID tenant,UUID empresa){return jdbc.query("SELECT financial_movement_id FROM customer_receipt_payment WHERE customer_receipt_id=? AND tenant_id=? AND empresa_id=? AND financial_movement_id IS NOT NULL ORDER BY line_order",(rs,n)->uuid(rs,"financial_movement_id"),recibo,tenant,empresa);}
    public void revertirAplicacion(AplicacionReversion a,UUID tenant,UUID empresa,String motivo,UUID usuario){
        int saldos=jdbc.update("""
            UPDATE customer_accounts_receivable
               SET applied_amount=applied_amount-?,status=CASE WHEN applied_amount-?=0 THEN 'PENDING' ELSE 'PARTIALLY_PAID' END,
                   version=version+1,updated_at=CURRENT_TIMESTAMP
             WHERE id=? AND tenant_id=? AND empresa_id=? AND applied_amount>=? AND status IN ('PAID','PARTIALLY_PAID')
            """,a.monto(),a.monto(),a.cuentaId(),tenant,empresa,a.monto());
        int aplicaciones=jdbc.update("""
            UPDATE customer_receipt_application SET reversed=true,reversed_at=CURRENT_TIMESTAMP,reversed_by=?,reversal_reason=?
             WHERE id=? AND tenant_id=? AND empresa_id=? AND reversed=false
            """,usuario,motivo,a.id(),tenant,empresa);
        if(saldos!=1||aplicaciones!=1)
            throw new ReglaNegocioException("El recibo no puede anularse porque existen operaciones posteriores que impiden su reversión.");
    }
    public OffsetDateTime anular(UUID recibo,UUID tenant,UUID empresa,String motivo,UUID usuario){
        return jdbc.queryForObject("""
            UPDATE customer_receipt SET status='VOIDED',voided_at=CURRENT_TIMESTAMP,voided_by=?,void_reason=?,version=version+1
             WHERE id=? AND tenant_id=? AND empresa_id=? RETURNING voided_at
            """,OffsetDateTime.class,usuario,motivo,recibo,tenant,empresa);
    }
    public CobroClienteDto bloquearRecibo(UUID recibo,UUID tenant,UUID empresa){return jdbc.query("SELECT * FROM customer_receipt WHERE id=? AND tenant_id=? AND empresa_id=? FOR UPDATE",rs->rs.next()?resumen(rs):null,recibo,tenant,empresa);}
    public void consumirDisponible(UUID recibo,BigDecimal monto){jdbc.update("UPDATE customer_receipt SET applied_amount=applied_amount+?,available_amount=available_amount-?,status=CASE WHEN available_amount-?=0 THEN 'APPLIED' ELSE 'PARTIALLY_APPLIED' END,version=version+1 WHERE id=?",monto,monto,monto,recibo);}

    private FacturaPendienteCobroDto factura(ResultSet rs,int row)throws SQLException{return new FacturaPendienteCobroDto(uuid(rs,"id"),uuid(rs,"sales_invoice_id"),rs.getString("internal_number"),rs.getDate("invoice_date").toLocalDate(),rs.getDate("due_date").toLocalDate(),uuid(rs,"currency_id"),rs.getString("currency_code"),rs.getBigDecimal("balance"));}
    private CobroClienteDto resumen(ResultSet rs)throws SQLException{return new CobroClienteDto(uuid(rs,"id"),rs.getString("internal_number"),uuid(rs,"customer_id"),rs.getString("customer_name_snapshot"),rs.getString("customer_identification_snapshot"),uuid(rs,"currency_id"),rs.getString("currency_code_snapshot"),uuid(rs,"branch_id"),rs.getString("branch_name_snapshot"),rs.getDate("receipt_date").toLocalDate(),rs.getObject("confirmed_at",OffsetDateTime.class),rs.getBigDecimal("received_amount"),rs.getBigDecimal("applied_amount"),rs.getBigDecimal("available_amount"),EstadoReciboCliente.valueOf(rs.getString("status")),rs.getString("reference"),rs.getString("notes"),null,rs.getString("void_reason"),List.of(),List.of());}
    private CobroClienteDto detalleBase(ResultSet rs)throws SQLException{CobroClienteDto x=resumen(rs);return new CobroClienteDto(x.id(),x.numero(),x.clienteId(),x.cliente(),x.identificacion(),x.monedaId(),x.moneda(),x.sucursalId(),x.sucursal(),x.fecha(),x.confirmadoEn(),x.recibido(),x.aplicado(),x.disponible(),x.estado(),x.referencia(),x.observacion(),rs.getString("usuario"),x.motivoAnulacion(),List.of(),List.of());}
    private static EstadoReciboCliente estado(BigDecimal aplicado,BigDecimal disponible){return aplicado.signum()==0?EstadoReciboCliente.AVAILABLE:disponible.signum()==0?EstadoReciboCliente.APPLIED:EstadoReciboCliente.PARTIALLY_APPLIED;}
    private static String digitos(String v){return v==null?null:v.replaceAll("\\D","");}
    private static UUID uuid(ResultSet rs,String name)throws SQLException{return rs.getObject(name,UUID.class);}
    private static Object[] append(Object[] source,Object...tail){Object[] out=Arrays.copyOf(source,source.length+tail.length);System.arraycopy(tail,0,out,source.length,tail.length);return out;}
    public record CuentaBloqueada(UUID id,UUID facturaId,UUID clienteId,UUID monedaId,BigDecimal saldo,String numero,LocalDate fecha,BigDecimal totalDocumento,UUID notaDebitoId){}
    public record AplicacionReversion(UUID id,UUID cuentaId,BigDecimal monto){}
}
