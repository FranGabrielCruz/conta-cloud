# Verificación — Notas de crédito de ventas

Fecha: 08/11/2026. Base local: PostgreSQL 17, `conta_cloud`. Flyway: V112 aplicada y validada. Hibernate: validación del esquema, sin creación ni actualización automática.

## Módulo

| Elemento | Resultado | Alcance de la comprobación |
|---|---|---|
| Listado | PASS | Ruta real, consulta paginada PostgreSQL, filtros y permisos |
| Nueva nota | PASS | Componentes y servicio; factura obligatoria, cantidades y motivo |
| Selector de factura | PASS | Consulta real paginada, disponibilidad y exclusión de factura agotada |
| Detalle | PASS | Cantidades e importes calculados por backend, lectura histórica |
| Confirmación | PASS | Emisión transaccional y solicitud idempotente |
| Ver nota | PASS | Documento emitido de solo consulta; controles sin edición |
| Flujo visual completo en navegador | PENDIENTE | El acceso llegó al login; se perdió la conexión del navegador durante el inicio de sesión |

Los PASS de implementación y pruebas de componentes/servicios no equivalen a una prueba E2E del navegador.

## Negocio

| Elemento | Resultado | Evidencia |
|---|---|---|
| Crédito parcial | PASS | Cálculo con descuentos, recargos, impuestos y moneda históricos; liquidación parcial real |
| Crédito total | PASS | Agotamiento de cantidades e importes |
| Múltiples notas | PASS | Tres notas reales de prueba: 0.25 + 0.50 + 0.25 unidades |
| Sobreacreditación | PASS | Rechazo del exceso en cantidades e importe; no se persiste otra nota |
| Concurrencia | PASS | Dos transacciones, una unidad: un éxito y un rechazo, saldo aplicado una sola vez |
| Idempotencia | PASS | La misma solicitud devuelve el mismo ID y conserva un solo efecto financiero |
| Efecto financiero | PASS | Reducción de CxC pendiente y saldo a favor de factura pagada |
| Relación con devolución | PASS | Disponibilidad compartida; la cantidad acreditada no se presenta como devolución física |
| Rollback | PASS | Fallo después de calcular/aplicar saldo revierte nota, saldo e idempotencia |
| Aislamiento | PASS | Rechazo por tenant, empresa y sucursal ajenos en consultas reales |
| Permisos y módulo | PASS | Seguridad Spring ejecutada antes de consultas o escrituras |
| Período cerrado | PASS | Rechazo sin escrituras |

## PDF

Generador: `com.citacloud.springboot.contacloud.app.services.NotaCreditoVentaPdfService`.

| Elemento | Resultado |
|---|---|
| Tamaño normal/grande A4 vertical | PASS |
| Encabezado y cliente | PASS |
| Documento afectado | PASS |
| NCF de nota | NO APLICA: el modelo POS actual no soporta emisión fiscal de NCF para esta operación |
| NCF afectado | NO APLICA: la factura POS actual no conserva NCF; se omite sin inventarlo |
| Detalle, descuentos e impuestos históricos | PASS |
| Totales y TOTAL destacado | PASS |
| Aplicación de crédito | PASS |
| Multipágina con encabezados repetidos | PASS |
| Snapshot histórico | PASS |
| Fallo PDF posterior al COMMIT | PASS: la nota y su liquidación permanecen |
| Regeneración sin reemisión | PASS |
| No muestra ContaCloud | PASS |
| Sin acciones ejecutables ni JavaScript | PASS |
| Nombre de archivo seguro | PASS |
| Revisión visual | PASS: un PDF de una página y otro de siete páginas, todas renderizadas e inspeccionadas |

Los PDF de ejemplo contienen datos sintéticos y no representan documentos fiscales reales.

## contaCloudPrint

| Comportamiento de la nota independiente | Resultado |
|---|---|
| Crea PrintJob | NO |
| Usa PrintAgent | NO |
| Usa 50 mm | NO |
| Usa 58 mm | NO |
| Usa 80 mm | NO |
| Depende de PrintOnScreen | NO |

## Pruebas

Clasificación sin contar una prueba dos veces:

| Grupo | PASS | FAIL |
|---|---:|---:|
| Unitarias, cálculo y documento PDF | 18 | 0 |
| Componentes de la pantalla | 2 | 0 |
| Autorización backend Spring | 4 | 0 |
| Integración PostgreSQL, sin concurrencia | 4 | 0 |
| Concurrencia PostgreSQL | 1 | 0 |
| Total automatizado | 29 | 0 |
| E2E del navegador | 0 | 0; PENDIENTE |

La integración incluye el aislamiento de tenant/empresa/sucursal. Los 18 casos unitarios incluyen ocho de notas de crédito, cuatro del PDF, cinco de devoluciones y uno de comportamiento post-COMMIT de devoluciones.

Registro de ejecución: `target/sales-credit-note-verification.log`. Las facturas y operaciones temporales se eliminan al finalizar. No se modifican saldos de facturas operativas ni se consumen números comerciales reales.

## Límites funcionales

La nota independiente requiere cliente registrado. No implementa una anulación sin reversión soportada, una devolución de dinero, un asiento contable operativo ni un NCF ficticio. Las notas anteriores de devolución se consultan sin volver a ejecutar sus efectos. Los campos históricos ausentes no se rellenan con información actual para simular datos que no fueron capturados.
