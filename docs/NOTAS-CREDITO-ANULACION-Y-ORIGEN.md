# Notas de crédito: listado, detalle, origen y anulación

## Interfaz

El listado ofrece únicamente «Ver nota de crédito», con el botón compacto global de ojo. Imprimir permanece dentro del detalle como única acción para abrir el PDF grande. Se elimina «Ver PDF» por duplicar ese flujo. El listado conserva notas emitidas y anuladas y permite filtrar ambos estados.

El bloque «INFORMACIÓN / DOCUMENTO ORIGEN» utiliza la cuadrícula responsive compartida de cuatro columnas, sin altura fija. Orden:

| Fila | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| 1 | Fecha | Estado | Cliente | RNC/Cédula |
| 2 | Factura | Fecha factura | NCF afectado | NCF de la nota |
| 3 | Moneda | Origen | Documento origen | Estado origen |

Los valores inexistentes se muestran como «—». Cliente usa la identidad histórica y el fallback Consumidor Final. Una nota directa muestra «Crédito financiero» sin devolución inventada. Una nota vinculada muestra «Devolución», su número y el estado real de esa devolución.

El pie del detalle mantiene el orden Imprimir / Anular / Cerrar, según permisos y elegibilidad. Una nota anulada ofrece Imprimir / Cerrar. Cerrar queda al extremo derecho y solo cierra la pantalla. Anular utiliza la acción global roja con icono Ban, requiere confirmación, motivo entre 3 y 1,000 caracteres y permiso backend `notas_credito.anular`. Cancelar el diálogo no anula el documento.

El permiso técnico `notas_credito.pdf`, el servicio de generación y el único handler que abre el documento se conservan porque Imprimir los utiliza. Esta simplificación no modifica el generador aprobado, los snapshots ni la lógica de anulación. Las pruebas específicas de acciones vigentes están en `NotasCreditoVentaViewTest`; registro: `target/credit-note-single-print-action-tests.log`.

El estado anulado incorpora un bloque con motivo, usuario y fecha. El botón Anular deja de estar disponible. Los botones principales conservan las variables globales de 40 × 40 e iconos de 22 × 22. La cuadrícula pasa a dos y una columnas en los mismos puntos de corte ya utilizados para el formulario.

## Persistencia y seguridad

Flyway V113 agrega estado VOIDED, versión y metadatos de anulación. Fue aplicada y validada en `conta_cloud`. No se modifican migraciones ejecutadas. El permiso se asigna a los roles ADMINISTRADOR mediante el mecanismo habitual. Una sesión anterior a la migración debe volver a iniciarse para obtener el permiso nuevo.

El snapshot de emisión no se modifica. El lector combina sus datos con el estado actual del registro. La anulación conserva número, fecha, líneas, importes, impuestos y origen; no borra la nota.

La operación exige módulo VENTAS y permiso de anulación; valida tenant, empresa, sucursal, estado, versión, períodos fiscales y efectos financieros. Si el snapshot contiene un comprobante fiscal cuya anulación requiere otro procedimiento, la operación no se ofrece ni se ejecuta como una simple reversión interna.

## Reversión transaccional

1. Se valida el alcance de la nota y se obtiene el control de idempotencia.
2. Se bloquean la factura y la nota, en un orden compatible con emisión y devoluciones.
3. Se comprueban de nuevo estado, versión y elegibilidad.
4. Se bloquean los registros financieros relacionados.
5. Se resta únicamente la aplicación a CxC registrada por esta nota, conservando pagos previos y otras aplicaciones.
6. Se retira el saldo a favor no utilizado, conservando su registro histórico con disponibilidad cero.
7. Se registra estado anulado, motivo, usuario, zona horaria, fecha y versión.
8. Se escribe auditoría estructurada con identificadores, origen, motivo, estados y TraceId, y se completa la idempotencia.

Todo se confirma o revierte junto. El evento estructurado de éxito se registra después del COMMIT. Si un saldo a favor ya se consumió, se bloquea la anulación hasta resolver sus aplicaciones. También se bloquea una reversión incompatible con el estado actual de CxC.

La misma solicitud no revierte dos veces. Dos solicitudes distintas sobre la misma versión producen un único éxito. La otra recibe un error controlado de estado/concurrencia.

Para Consumidor Final sin cliente no se crea ni revierte una cuenta inexistente. Se anula el crédito del documento y se restaura su disponibilidad financiera, sin crear cliente, saldo, reembolso, caja ni inventario.

## Devoluciones independientes

El listado consulta `sales_credit_note`; una devolución sin nota no se transforma visualmente en NCV. Cuando existe una nota real, se conserva su FK a la devolución y la factura.

Anular la nota de una devolución revierte únicamente sus efectos financieros. No actualiza el estado de `sales_return`, no elimina sus líneas ni modifica inventario. La disponibilidad financiera toma solo notas vigentes. La disponibilidad de devolución física conserva las cantidades efectivamente devueltas, aunque se haya anulado su nota financiera.

La implementación no añade una navegación artificial a una pantalla genérica de «Ver devolución» inexistente en el módulo: el flujo actual ofrece historial/impresión de devoluciones. La referencia y el estado reales sí quedan visibles en el detalle de la nota.

## PDF anulado

El PDF sigue utilizando el formato grande oficial. Se identifica como ANULADA y aclara que los importes y la aplicación mostrados corresponden a la emisión original. El original se conserva; imprimir o regenerar no reanula ni vuelve a aplicar saldos. No se utiliza PrintAgent, PrintOnScreen ni impresión térmica.

## Verificación

Suite: **64 PASS / 0 FAIL / 0 omitidas**, registrada en `target/credit-note-annulment-verification.log`.

| Grupo | PASS |
|---|---:|
| Servicio de notas y anulación | 10 |
| PDF y documento histórico | 10 |
| PostgreSQL: emisión, reversión, devolución, aislamiento y concurrencia | 17 |
| Autorización Spring | 5 |
| Componentes y orden de campos/acciones | 8 |
| Regresión de Orden de Compra | 8 |
| Regresión de devoluciones y post-COMMIT | 6 |

Se validan reversión de CxC, saldo a favor, nota mixta sin deshacer pagos anteriores, saldo consumido bloqueado, rollback tras fallo, replay idempotente, dos anulaciones concurrentes, nota de Consumidor Final y nota vinculada a devolución que conserva su estado y cantidades físicas. El fixture de devolución se crea con su snapshot obligatorio de número de factura; todos los registros temporales se limpian al finalizar.

Las pruebas de componentes no equivalen a E2E. El recorrido completo y la revisión de pantalla a 1366×768, 1600×900 y 1920×1080 quedan **PENDIENTES** por falta de acceso verificable al navegador. No se presentan como aprobados.
