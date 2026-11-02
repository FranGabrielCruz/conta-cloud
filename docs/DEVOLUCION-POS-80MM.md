# Devolución de venta POS de 80 mm

## Documento y presentación

`SalesReturnPrintDocument` es un snapshot inmutable específico de devolución: empresa y cliente históricos, número DEV, factura origen, fecha/hora de la devolución, sucursal/caja/operador, motivo, artículos efectivamente devueltos, subtotal/descuento/recargo/impuestos/total, formas de reembolso y pie. Cada artículo conserva cantidad devuelta, descripción, precio unitario, descuento, impuesto e importe de línea. Las listas se copian y los textos/importes tienen límites estructurales. El modelo no depende de PDF, JavaFX, JSON o comandos de impresora.

`SalesReturn80mmPdfRenderer` compone exclusivamente la devolución. `PosTicket80mmPdfRenderer` proporciona geometría, wrapping, columnas, divisores, altura dinámica, paginación y serialización PDF a los dos documentos. La factura conserva su composición: nueve PDFs anteriores, incluida la muestra técnica y ocho fixtures, se compararon byte por byte después de extraer el motor común sin diferencias.

El papel mide realmente 80 mm, con márgenes de 3,5 mm, fuente española incrustada y pie próximo al contenido. El total devuelto y su moneda se mantienen en una fila. Motivo, empresa, cliente y descripciones largos hacen wrapping sin recortarse. No se ejecutan HTML/JS, rutas o comandos del documento. No se imprime marca de la aplicación, datos de autenticación ni TraceId interno.

El renderer formatea los valores recibidos; no recalcula importes ni relaciones de venta. Las nueve muestras visuales están en `target/sales-return-review`: 1/5/20 artículos, empresa/cliente/descripción/motivo largos, importe grande y reembolso múltiple. El PDF técnico está en `output/pdf/devolucion-venta-pos-80mm-prueba.pdf`, marcado DOCUMENTO DE PRUEBA.

## Integración y orden transaccional

1. `DevolucionVentaService.confirmar` ejecuta la operación de negocio existente y captura únicamente la identidad histórica y la caja de origen. No consulta agentes o impresoras ni genera PDF o Print Jobs.
2. La confirmación termina su transacción antes de presentar las acciones al usuario. La pantalla no abre PDF ni imprime automáticamente.
3. **Imprimir devolución** llama a `DevolucionVentaPrintService.solicitar` en una transacción nueva. Valida permisos, devolución confirmada y origen persistido, y delega a BillingPrintRouter. Con Impresión por pantalla (PDF) marcado devuelve la salida PDF normal sin generar ticket ni trabajo. Desmarcado genera el ticket específico e inserta un trabajo `SALES_RETURN`.
4. El agente reserva y confirma ese trabajo mediante los endpoints existentes. El PDF se imprime o guarda con el destino configurado. No reconstruye productos ni la relación con la factura origen.

El PDF del trabajo se genera en ContaCloud utilizando el mismo núcleo disponible en contaCloudPrint, conforme al transporte PDF ya existente. No se introduce un segundo contrato de snapshots JSON para reemplazar ese transporte. Ambas aplicaciones mantienen ocho archivos del núcleo equivalentes; comprobar con `scripts/check-pos-renderer-parity.ps1` del repositorio contaCloudPrint.

Un fallo de renderer, impresora o agente posterior a la confirmación no revierte la devolución. Una nueva copia solo crea otro intento de impresión; no invoca confirmación, inventario, contabilidad, impuestos ni crédito. El agente conserva la protección existente que reintenta una confirmación HTTP sin imprimir otra vez el documento ya enviado/guardado.

## Caja, historial e idempotencia

La vista proporciona el token de terminal como contexto de confirmación, separado del DTO del comando. Se valida su hash dentro del tenant/empresa y se conserva la caja de esa terminal, incluso si difiere de la caja de la factura original. El token no se almacena en el snapshot, no forma parte del documento ni modifica la huella del comando de negocio. Los llamadores anteriores sin contexto de terminal mantienen el origen de la factura como compatibilidad explícita.

La migración **V101__impresion_pos_devoluciones_ventas.sql** añade el emisor, sucursal, caja y operador históricos; nombre histórico de impuesto en líneas; y tipo de Print Job. La clave foránea de origen impide cruces de tenant/empresa/sucursal/caja. Los trabajos existentes se clasifican como `SALES_INVOICE`. No se modifican migraciones anteriores ni se hace backfill de identidades históricas inexistentes.

Al reimprimir se usa la caja guardada en la devolución y su asignación actual de agente. El mismo agente puede atender varias cajas de su ámbito. Una asignación retirada, ambigua, de otra sucursal o de otro tenant no provoca un envío a otra caja. Una caída de heartbeat o una impresora desconectada no invalida una devolución confirmada; el procesamiento de trabajos mantiene los estados y reglas existentes.

El historial se pagina a cinco filas, filtrando tenant, empresa, factura, estado y sucursales autorizadas en la consulta. La tabla inicial conserva una sola acción compacta de 48 px. Las facturas ya devueltas conducen al historial; en las demás, el detalle ofrece acceso a las devoluciones anteriores. Usuarios con permiso de PDF pueden acceder al historial sin permiso para crear otra devolución.

Cada botón de impresión mantiene un UUID de intento durante reintentos y se deshabilita al solicitar. La restricción de idempotencia del servidor evita duplicados aun si el cliente repite la petición. Navegar nuevamente al historial y solicitar otra copia genera un intento nuevo, conservando el ReturnId. Un intento ajeno a otro documento o tipo no se acepta como reimpresión.

## Reembolso y compatibilidad

El negocio actual crea una nota de crédito y, cuando corresponde, saldo a favor del cliente. El adaptador muestra esos importes guardados; no utiliza el medio de pago de la factura para inventar un reembolso en efectivo o tarjeta. El modelo y renderer admiten efectivo, tarjeta u otras combinaciones cuando una fuente confirmada las proporcione, sin campos de PAN/CVV/PIN.

Las devoluciones anteriores sin emisor propio usan el snapshot de la factura origen cuando está disponible, identificado en el pie. Solo si tampoco existe, se mantiene la compatibilidad con datos actuales y una advertencia visible. Los nombres de impuesto históricos ausentes se presentan como Impuestos, sin asumir ITBIS por una tasa.

**Impresión por pantalla (PDF)** aplica la política compartida de Facturación a factura y devolución: marcado muestra únicamente el PDF normal; desmarcado envía únicamente el ticket 80 mm a contaCloudPrint. **Ver PDF de devolución** siempre utiliza el PDF normal sin encolar trabajos. Microsoft Print to PDF guarda silenciosamente los trabajos recibidos, sin abrir un visor.

## Seguridad y verificación

La solicitud usa el permiso existente `devoluciones_venta.pdf`, ya disponible mediante los roles normales del módulo. No acepta AgentId ni tenant/empresa proporcionados por el cliente. La reserva y confirmación del agente conservan su validación de credencial, caja asignada, AgentId y ClaimId. Los registros nuevos contienen IDs de trabajo/devolución/agente/caja, tipo y correlación; no contenido completo del documento ni credenciales.

Pruebas incluidas:

- Formato, ancho, altura, geometría, ausencia de acciones PDF, caracteres españoles, cantidades, monedas, descuento/recargo, impuestos y reembolsos.
- Conservación de snapshots en JPA/H2; migración V101 exacta en H2 con modo PostgreSQL; valores por defecto y restricciones de ámbito/tipo.
- Contexto de caja distinto a la venta, compatibilidad de huellas de idempotencia, intentos repetidos, reimpresión y asignaciones retiradas.
- Fallo de renderer en una transacción separada después de confirmar datos de prueba; conservación de la devolución confirmada.
- Doble clic, permisos y visualización manual independientes en Vaadin.
- Recorrido del agente con API HTTP de pruebas autenticada: reserva `SALES_RETURN`, PDF real de devolución, guardado en archivo y confirmación después de guardar; reintento de ACK sin repetir salida.

Los tests locales no crean ni modifican ventas de la base de desarrollo. Nueve pruebas existentes de PostgreSQL requieren habilitar explícitamente `CONTACLOUD_POSTGRES_TESTS`; quedan omitidas si esa variable no existe. La migración se aplica a PostgreSQL mediante Flyway al reiniciar ContaCloud, antes de habilitar esas pruebas. La aceptación final con una venta/devolución de la caja real y una impresora física requiere ese entorno; no se presenta como ejecutada por las pruebas HTTP/H2.

Para reproducir se usa `mvn verify dependency:analyze -Dcontacloud.buildDirectory=target/sales-return-build` en el agente y `mvn test -Dvaadin.skip=true -Dcontacloud.buildDirectory=target/sales-return-build` en ContaCloud. Los logs de esta tarea están en `target/sales-return-agent-verify.log` y `target/sales-return-backend-final.log` del agente.

### Resultado de la validación

- Agente: **162 pruebas aprobadas**, sin fallos, errores ni omisiones; `verify`, PMD y análisis de dependencias correctos.
- ContaCloud: **650 pruebas ejecutadas y aprobadas**, sin fallos ni errores; **9 pruebas PostgreSQL omitidas** por la condición de entorno descrita arriba (659 casos contabilizados).
- Migración V101, persistencia histórica, idempotencia compatible, transacción posterior a confirmación, permisos y doble clic verificados en los entornos de prueba aislados.
- Nueve fixtures de devolución idénticos entre ambas aplicaciones y nueve PDFs de factura anteriores conservados byte por byte. Núcleo compartido verificado con el script de paridad.
- Nueve escenarios visuales revisados y muestra técnica final comprobada con Poppler: ancho 80 mm, una página de altura ajustada y ningún recorte o solapamiento.
- Paquete Windows generado con runtime incluido; su JAR coincide con el JAR verificado y contiene el nuevo modelo, renderer y tipo de documento.

Recargar Maven y reiniciar ContaCloud y el agente. Para una devolución nueva, confirmar y pulsar Imprimir; para una existente, acceder al historial de la factura y solicitar una copia. La confirmación de disponibilidad del agente y del dispositivo pertenece al intento de impresión, no a la existencia de la devolución.
