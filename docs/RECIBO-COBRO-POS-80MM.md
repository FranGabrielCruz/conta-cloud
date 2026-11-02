# Recibo de cobro POS de 80 mm

`PAYMENT_RECEIPT` identifica el recibo en el contrato compartido de trabajos. `PaymentReceiptPrintDocument` contiene los datos confirmados e inmutables; `PaymentReceipt80mmPdfRenderer` compone el recibo sobre `PosTicket80mmPdfRenderer`, con ancho real de 80 mm, márgenes de 3,5 mm, altura dinámica y ajuste de texto. El renderer no calcula cobros, cuentas por cobrar ni balances.

## Confirmación e historial

La migración nueva `V103__impresion_pos_recibos_cobro.sql` añade el snapshot y la caja de origen al recibo, y amplía los tipos permitidos de la cola. No modifica migraciones aplicadas. La identidad de empresa, cliente, sucursal, caja y operador; la hora confirmada convertida a la zona de empresa; las aplicaciones iniciales y los métodos de pago se conservan dentro de la transacción del cobro, como datos, sin renderizar PDF ni consultar impresoras o agentes.

El balance anterior y el pendiente se leen de las cuentas por cobrar antes y después de la operación y se conservan. Las operaciones de recibos del mismo cliente se serializan con un bloqueo de su fila. Una aplicación posterior de saldo disponible no altera el comprobante inicial. Los adelantos sin facturas conservan el importe realmente aplicado, que puede ser cero: no se transforma un adelanto disponible en un abono aplicado ficticio.

La reimpresión lee el snapshot; no ejecuta nuevamente confirmación, aplicaciones, movimientos ni contabilidad. Las referencias de tarjetas se admiten solamente como últimos cuatro dígitos ya enmascarados. Se excluyen referencias con PAN completo o campos de secretos; los datos originales del negocio no se modifican. El PDF contiene texto, sin HTML activo, acciones o rutas ejecutables.

## Salida y enrutamiento

`PaymentReceiptPrintService` usa `BillingPrintRouter` después de que la confirmación regresa y se confirma su transacción. La opción del agente **Impresión por pantalla (PDF)** determina una sola salida:

- Activada: se abre el PDF del recibo, sin crear trabajo ni contactar al agente.
- Desactivada: se crea un trabajo tipado con la caja, sucursal, empresa, tenant y agente autorizados. No se abre un visor ni hay conversión automática a PDF por fallos de conexión.

La clave del intento se conserva en los reintentos del diálogo. Cerrar y volver a abrir el detalle permite una nueva reimpresión explícita. El agente reutiliza su entrega silenciosa y su confirmación de trabajos, incluida la protección contra repetir la impresión al reintentar un acuse fallido. Con Microsoft Print to PDF, el archivo del trabajo remoto se guarda silenciosamente en la carpeta PDF del agente.

Un fallo de salida se muestra aparte y no revierte el cobro confirmado. La caja de origen se valida contra tenant, empresa y sucursal; la asignación al agente se resuelve con las mismas reglas que facturas y devoluciones.

## Datos anteriores

Los recibos creados antes de esta migración no tienen necesariamente snapshot ni caja de origen. No se rellena esa información con el balance actual, nombres actuales o una caja elegida arbitrariamente. Una solicitud POS incompleta devuelve un error controlado. El servicio anterior de PDF compacto se conserva, pero esos documentos no se consideran snapshots históricos de este nuevo flujo. Los recibos anulados no generan nuevos tickets de cobro.

## Verificación

Las pruebas del agente cubren recibos con cero, uno, varios y muchos documentos; moneda distinta, abonos parciales, métodos múltiples y referencias, balances opcionales, textos largos, importes grandes, datos sensibles, ausencia de acciones PDF, límites físicos y superposición. Las pruebas de entrega tipada cubren recibos y devoluciones con reintento del acuse sin repetir la impresión.

Las pruebas del backend cubren la migración en H2, serialización y lectura del snapshot con zona horaria, inmutabilidad ante cambios posteriores, tenant/empresa/caja/sucursal, referencias de tarjeta y secretos, las dos salidas del router, tipo y origen del trabajo, reintentos y errores sin fallback.

El script `scripts/check-pos-renderer-parity.ps1` comprueba los diez archivos del núcleo compartido. Los ejemplos visuales están en `target/payment-receipt-review/`; el PDF de demostración contiene datos ficticios y número `REC-PRUEBA-000015`.

La aceptación con backend en ejecución, PostgreSQL real, dos solicitudes concurrentes de confirmación y una impresora física permanece pendiente. Las pruebas PostgreSQL opcionales requieren habilitación y credenciales de pruebas; no se activan ni se modifica la base operativa automáticamente. Reiniciar el backend para aplicar V103 y usar el agente actualizado antes de probar la integración real.

### Resultado de la verificación local

- Agente: 138 pruebas unitarias y 37 de integración, sin fallos; `verify` y análisis de dependencias satisfactorios. Los diez escenarios finales de formato también se verificaron por separado.
- Backend: regresión completa de 687 pruebas, 676 satisfactorias y 11 pruebas PostgreSQL opcionales omitidas. Compilación final aislada con 47 pruebas específicas de recibos, reintentos, cobros, router y devoluciones, todas satisfactorias.
- Snapshot: se comprueba rollback de captura, permanencia del cobro ante fallo posterior de salida y conservación del primer snapshot frente a capturas concurrentes.
- Recorrido autenticado simulado: `PAYMENT_RECEIPT` se reclama, se guarda silenciosamente como PDF y se confirma sin abrir visor ni usar una impresora real.
- Diez archivos compartidos idénticos; ejemplos PDF revisados visualmente; comprobaciones geométricas sin recorte ni superposición.
- Paquete Windows: `target/payment-receipt-build/dist/contaCloudPrint/contaCloudPrint.exe`, con runtime incluido; el JAR empaquetado coincide por SHA-256 con el verificado. Es una imagen de verificación del empaquetado; requiere el origen de producción aprobado antes de distribuirse. El origen de producción continúa vacío y el ejecutable jpackage rechaza el override de desarrollo. Para probar con localhost se mantiene la configuración de IntelliJ **ContaCloud Print - Local**. La firma y distribución siguen el proceso habitual del proyecto.
