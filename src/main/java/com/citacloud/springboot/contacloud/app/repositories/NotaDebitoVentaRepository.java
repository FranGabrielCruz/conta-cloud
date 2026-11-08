package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.FacturaVenta;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.services.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;import java.time.*;import java.util.*;

@Repository
public class NotaDebitoVentaRepository {
 private final JdbcTemplate jdbc;private static final JsonMapper JSON=JsonMapper.builder().build();
 public NotaDebitoVentaRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Resumen(UUID id,String numero,LocalDate fecha,String factura,String cliente,String moneda,BigDecimal total,String estado){}
 public record FacturaOpcion(UUID id,String numero,String cliente,LocalDate fecha,String moneda,BigDecimal total){}
 private Object[] scope(){var p=TenantContext.principalActual();return new Object[]{p.tenantId(),p.empresaId(),p.accesoTodasSucursales(),"{"+p.sucursalIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))+"}"};}
 public Page<Resumen> buscar(String texto,String estado,LocalDate desde,LocalDate hasta,int page,int size){
  String where=" FROM sales_debit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id WHERE n.tenant_id=? AND n.empresa_id=? AND (? OR f.branch_id=ANY(?::uuid[])) AND (?='' OR lower(n.debit_note_number||' '||(n.document_snapshot::jsonb->>'factura')||' '||(n.document_snapshot::jsonb->>'cliente')) LIKE ?) AND (?='' OR n.status=?) AND (?::date IS NULL OR n.issue_date>=?::date) AND (?::date IS NULL OR n.issue_date<=?::date)";
  // Search snapshot values rather than mutable customer data.
  var args=new ArrayList<Object>(Arrays.asList(scope()));args.addAll(Arrays.asList(texto,"%"+texto.toLowerCase(Locale.ROOT)+"%",estado,estado,desde,desde,hasta,hasta));
  long count=jdbc.queryForObject("SELECT count(*)"+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
  var rows=jdbc.query("SELECT n.id,n.debit_note_number,n.issue_date,n.document_snapshot::jsonb->>'factura',n.document_snapshot::jsonb->>'cliente',n.document_snapshot::jsonb->>'moneda',n.amount,n.status"+where+" ORDER BY n.confirmed_at DESC,n.id DESC LIMIT ? OFFSET ?",(r,i)->new Resumen(r.getObject(1,UUID.class),r.getString(2),r.getDate(3).toLocalDate(),r.getString(4),r.getString(5),r.getString(6),r.getBigDecimal(7),r.getString(8)),args.toArray());
  return new PageImpl<>(rows,PageRequest.of(page,size),count);
 }
 public Page<FacturaOpcion> facturas(String texto,int page,int size){
  String where=" FROM sales_invoice f WHERE f.tenant_id=? AND f.empresa_id=? AND (? OR f.branch_id=ANY(?::uuid[])) AND f.status='CONFIRMED' AND (?='' OR lower(f.internal_number||' '||CASE WHEN f.customer_id IS NULL THEN 'Consumidor Final' ELSE coalesce(nullif(trim(f.customer_name),''),'Consumidor Final') END||' '||coalesce(f.customer_identification,'')) LIKE ?)";
  var args=new ArrayList<Object>(Arrays.asList(scope()));args.add(texto);args.add("%"+texto.toLowerCase(Locale.ROOT)+"%");
  long count=jdbc.queryForObject("SELECT count(*)"+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
  var rows=jdbc.query("SELECT f.id,f.internal_number,CASE WHEN f.customer_id IS NULL THEN 'Consumidor Final' ELSE coalesce(nullif(trim(f.customer_name),''),'Consumidor Final') END,f.invoice_date,f.currency_code,f.total"+where+" ORDER BY f.confirmed_at DESC,f.id DESC LIMIT ? OFFSET ?",(r,i)->new FacturaOpcion(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getDate(4).toLocalDate(),r.getString(5),r.getBigDecimal(6)),args.toArray());
  return new PageImpl<>(rows,PageRequest.of(page,size),count);
 }
 public List<PreparacionNotaDebitoVentaDto.Opcion> motivos(){var p=TenantContext.principalActual();return jdbc.query("SELECT id,name FROM sales_debit_note_reason WHERE tenant_id=? AND empresa_id=? AND active ORDER BY name",(r,i)->new PreparacionNotaDebitoVentaDto.Opcion(r.getObject(1,UUID.class),r.getString(2)),p.tenantId(),p.empresaId());}
 public String motivo(UUID id){return motivos().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(()->new ReglaNegocioException("Seleccione un motivo disponible.")).nombre();}
 public void validarMoneda(UUID id){var p=TenantContext.principalActual();if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM monedas WHERE empresa_id=? AND id=? AND activo)",Boolean.class,p.empresaId(),id)))throw new ReglaNegocioException("La moneda de la factura no está disponible.");}
 public void cliente(UUID id,boolean lock){var p=TenantContext.principalActual();var ids=jdbc.queryForList("SELECT id FROM customer WHERE tenant_id=? AND empresa_id=? AND id=?"+(lock?" FOR UPDATE":""),UUID.class,p.tenantId(),p.empresaId(),id);if(ids.isEmpty())throw new RecursoNoEncontradoException("Cliente no encontrado.");}
 public BigDecimal saldo(UUID factura){
  var p=TenantContext.principalActual();
  return jdbc.queryForObject("""
   SELECT coalesce((SELECT sum(CASE WHEN status='VOIDED' THEN 0 ELSE original_amount-applied_amount END)
      FROM customer_accounts_receivable WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=?),0)
    +coalesce((SELECT f.total FROM sales_invoice f WHERE f.tenant_id=? AND f.empresa_id=? AND f.id=? AND f.payment_condition='Crédito' AND f.status='CONFIRMED'
      AND NOT EXISTS(SELECT 1 FROM customer_accounts_receivable a WHERE a.tenant_id=f.tenant_id AND a.empresa_id=f.empresa_id AND a.sales_invoice_id=f.id AND a.sales_debit_note_id IS NULL)),0)
   """,BigDecimal.class,p.tenantId(),p.empresaId(),factura,p.tenantId(),p.empresaId(),factura);
 }
 public void incrementar(FacturaVenta f,NotaDebitoVentaDocumento d){
  var p=TenantContext.principalActual();
  if("Crédito".equals(f.getCondicionPago()))jdbc.update("""
   INSERT INTO customer_accounts_receivable(tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,original_amount,due_date)
   VALUES(?,?,?,?,?,?,?) ON CONFLICT(tenant_id,empresa_id,sales_invoice_id) WHERE sales_debit_note_id IS NULL DO NOTHING
   """,p.tenantId(),p.empresaId(),f.getId(),f.getClienteId(),f.getMonedaId(),f.getTotal(),f.getVencimiento());
  jdbc.update("INSERT INTO customer_accounts_receivable(tenant_id,empresa_id,sales_invoice_id,sales_debit_note_id,customer_id,currency_id,original_amount,due_date,debit_note_origin) VALUES(?,?,?,?,?,?,?,?,true)",p.tenantId(),p.empresaId(),f.getId(),d.id(),f.getClienteId(),f.getMonedaId(),d.total(),d.fecha());
 }
 public String siguiente(){var p=TenantContext.principalActual();Long n=jdbc.queryForObject("INSERT INTO sales_debit_note_sequence(tenant_id,empresa_id,last_number) VALUES(?,?,1) ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number=sales_debit_note_sequence.last_number+1 RETURNING last_number",Long.class,p.tenantId(),p.empresaId());return "NDV-"+String.format(Locale.ROOT,"%06d",n);}
 public void insertar(NotaDebitoVentaDocumento d){
  var p=TenantContext.principalActual();jdbc.update("INSERT INTO sales_debit_note(id,tenant_id,empresa_id,debit_note_number,sales_invoice_id,customer_id,currency_id,reason_id,issue_date,amount,document_snapshot,confirmed_by,payment_condition,receivable_mode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",d.id(),p.tenantId(),p.empresaId(),d.numero(),d.facturaId(),d.clienteId(),d.monedaId(),d.motivoId(),d.fecha(),d.total(),JSON.writeValueAsString(d),p.usuarioId(),d.condicionNota().name(),d.condicionNota()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH?"NONE":"DOCUMENT");
  int order=0;for(var l:d.lineas())jdbc.update("INSERT INTO sales_debit_note_line(tenant_id,empresa_id,sales_debit_note_id,line_order,description,quantity,unit_price,gross_amount,discount_amount,tax_id,tax_name,tax_rate,tax_amount,total) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",p.tenantId(),p.empresaId(),d.id(),++order,l.descripcion(),l.cantidad(),l.precio(),l.bruto(),l.descuento(),l.impuestoId(),l.impuestoNombre(),l.tasa(),l.impuesto(),l.total());
 }
 public NotaDebitoVentaDocumento documento(UUID id){var p=TenantContext.principalActual();return jdbc.query("SELECT n.document_snapshot,f.branch_id FROM sales_debit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id WHERE n.tenant_id=? AND n.empresa_id=? AND n.id=?",r->{if(!r.next()||!EmpresaContext.permiteSucursal(r.getObject(2,UUID.class)))throw new RecursoNoEncontradoException("Nota de débito no encontrada.");return JSON.readValue(r.getString(1),NotaDebitoVentaDocumento.class);},p.tenantId(),p.empresaId(),id);}
 public NotaDebitoVentaCicloDto ciclo(UUID id,boolean lock){
  var p=TenantContext.principalActual();return jdbc.query("""
   SELECT n.status,n.version,n.annulment_reason,n.annulled_by_name,n.annulled_at,n.annulled_zone,f.branch_id,
     (n.status='CONFIRMED' AND f.status='CONFIRMED' AND
       (n.payment_condition='CASH' OR EXISTS(SELECT 1 FROM customer_accounts_receivable a WHERE a.tenant_id=n.tenant_id AND a.empresa_id=n.empresa_id
         AND a.sales_invoice_id=n.sales_invoice_id AND a.status<>'VOIDED'
         AND ((n.receivable_mode='DOCUMENT' AND a.sales_debit_note_id=n.id AND a.applied_amount=0)
           OR(n.receivable_mode='LEGACY_INVOICE' AND a.sales_debit_note_id IS NULL AND a.original_amount-a.applied_amount>=n.amount))))),
     CASE WHEN n.status='VOIDED' THEN 0 WHEN n.payment_condition='CASH' THEN n.amount
       ELSE coalesce((SELECT a.applied_amount FROM customer_accounts_receivable a WHERE a.tenant_id=n.tenant_id AND a.empresa_id=n.empresa_id AND a.sales_debit_note_id=n.id AND a.status<>'VOIDED'),0) END paid,
     CASE WHEN n.status='VOIDED' OR n.payment_condition='CASH' THEN 0 ELSE coalesce(
       (SELECT a.original_amount-a.applied_amount FROM customer_accounts_receivable a WHERE a.tenant_id=n.tenant_id AND a.empresa_id=n.empresa_id AND a.sales_debit_note_id=n.id AND a.status<>'VOIDED'),n.amount) END balance
   FROM sales_debit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id
   WHERE n.tenant_id=? AND n.empresa_id=? AND n.id=?
   """+(lock?" FOR UPDATE OF n":""),r->{if(!r.next()||!EmpresaContext.permiteSucursal(r.getObject(7,UUID.class)))throw new RecursoNoEncontradoException("Nota de débito no encontrada.");var at=r.getObject(5,OffsetDateTime.class);return new NotaDebitoVentaCicloDto(r.getString(1),r.getLong(2),r.getBoolean(8),r.getString(3),r.getString(4),at==null?null:at.atZoneSameInstant(ZoneId.of(r.getString(6))).toLocalDateTime(),r.getBigDecimal(9),r.getBigDecimal(10));},p.tenantId(),p.empresaId(),id);
 }
 public void revertir(NotaDebitoVentaDocumento d){
  var p=TenantContext.principalActual();
  String mode=jdbc.queryForObject("SELECT receivable_mode FROM sales_debit_note WHERE tenant_id=? AND empresa_id=? AND id=?",String.class,p.tenantId(),p.empresaId(),d.id());
  if("DOCUMENT".equals(mode)){
   var rows=jdbc.queryForList("SELECT id,status,applied_amount FROM customer_accounts_receivable WHERE tenant_id=? AND empresa_id=? AND sales_debit_note_id=? FOR UPDATE",p.tenantId(),p.empresaId(),d.id());
   if(rows.size()!=1||"VOIDED".equals(rows.getFirst().get("status"))||((BigDecimal)rows.getFirst().get("applied_amount")).signum()!=0)throw new ReglaNegocioException("La nota tiene cobros aplicados. Revierta sus aplicaciones antes de anular.");
   jdbc.update("UPDATE customer_accounts_receivable SET status='VOIDED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND empresa_id=? AND sales_debit_note_id=?",p.tenantId(),p.empresaId(),d.id());return;
  }
var rows=jdbc.queryForList("SELECT original_amount,applied_amount,status FROM customer_accounts_receivable WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? AND sales_debit_note_id IS NULL FOR UPDATE",p.tenantId(),p.empresaId(),d.facturaId());
  if(rows.size()!=1||"VOIDED".equals(rows.getFirst().get("status")))throw new ReglaNegocioException("La cuenta por cobrar cambió.");
  BigDecimal original=(BigDecimal)rows.getFirst().get("original_amount"),applied=(BigDecimal)rows.getFirst().get("applied_amount");
  BigDecimal remaining=original.subtract(d.total());if(remaining.compareTo(applied)<0)throw new ReglaNegocioException("El cargo ya tiene cobros o créditos aplicados. Revierta sus aplicaciones antes de anular.");
  if(remaining.signum()==0)jdbc.update("UPDATE customer_accounts_receivable SET status='VOIDED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? AND sales_debit_note_id IS NULL",p.tenantId(),p.empresaId(),d.facturaId());
  else jdbc.update("UPDATE customer_accounts_receivable SET original_amount=?,status=CASE WHEN applied_amount=? THEN 'PAID' WHEN applied_amount=0 THEN 'PENDING' ELSE 'PARTIALLY_PAID' END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? AND sales_debit_note_id IS NULL",remaining,remaining,p.tenantId(),p.empresaId(),d.facturaId());
 }
 public void insertarPago(UUID nota,NotaDebitoVentaDocumento.Pago pago,UUID movimiento){
  var p=TenantContext.principalActual();jdbc.update("INSERT INTO sales_debit_note_payment(id,tenant_id,empresa_id,sales_debit_note_id,payment_method_id,method_name,method_type,amount,received_amount,change_amount,reference,cash_register_session_id,financial_movement_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",pago.id(),p.tenantId(),p.empresaId(),nota,pago.metodoPagoId(),pago.metodo(),pago.tipo(),pago.monto(),pago.recibido(),pago.cambio(),pago.referencia(),pago.sesionId(),movimiento);
 }
 public void anular(UUID id,long version,String reason,UUID key,String zone){var p=TenantContext.principalActual();int changed=jdbc.update("UPDATE sales_debit_note SET status='VOIDED',version=version+1,annulled_at=CURRENT_TIMESTAMP,annulled_by=?,annulled_by_name=?,annulment_reason=?,annulment_key=?,annulled_zone=? WHERE tenant_id=? AND empresa_id=? AND id=? AND status='CONFIRMED' AND version=?",p.usuarioId(),p.nombre(),reason,key,zone,p.tenantId(),p.empresaId(),id,version);if(changed!=1)throw new ReglaNegocioException("La nota cambió o ya fue anulada.");}
}
