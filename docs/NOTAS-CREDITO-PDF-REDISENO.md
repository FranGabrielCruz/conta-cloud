# Rediseño del PDF A4 de notas de crédito

Este documento registra una etapa anterior. El formato vigente horizontal y la verificación actual están documentados en [Formato oficial y consumidor final](NOTAS-CREDITO-FORMATO-OFICIAL-Y-CONSUMIDOR-FINAL.md).

## Presentación

Se revisaron las plantillas oficiales `reports/purchase-order/purchase-order.jrxml` y `reports/supplier-credit-note/supplier-credit-note.jrxml`. El renderer PDFBox conserva A4 vertical y adopta su familia visual: tipografía sans serif de 9 puntos para contenido, encabezados de mayor jerarquía, títulos de sección pequeños, paleta `#334155` / `#CBD5E1`, bandas, separadores finos y cabeceras de tabla oscuras con texto blanco. Se reutilizan también los formateadores de identificación fiscal y teléfono de `PurchaseOrderPdfService`.

El encabezado corporativo usa dos zonas, con empresa a la izquierda y documento/número/fecha/moneda a la derecha. Los nombres extensos conservan su tamaño y hacen wrap. Cliente y documento afectado tienen columnas independientes dentro de un panel delimitado. Los campos opcionales ausentes se omiten.

La información de la nota separa NCF propio, motivo y devolución origen cuando están registrados en el snapshot. El NCF afectado permanece en el bloque de la factura. Los datos de prueba que contienen NCF no habilitan ni simulan emisión fiscal en el sistema.

La tabla mantiene seis columnas, mayor espacio para descripción, importes a la derecha, filas de altura variable, filas alternadas y encabezados repetidos. Conserva la identificación de la nota y «Página X de Y» en las páginas adicionales.

Los totales forman un bloque compacto a la derecha, con separación y fondo para el total de mayor tamaño. Los nombres de impuestos se normalizan para no repetir una tasa ya incluida en el nombre, conservando el tipo y la tasa históricos; no se limita el documento a ITBIS.

La aplicación del crédito tiene una sección independiente. Muestra saldo anterior, crédito aplicado y saldo pendiente cuando existe aplicación, y saldo a favor con moneda cuando existe crédito al cliente. Puede mostrar ambos efectos si el snapshot contiene ambos. No reconstruye ni recalcula los saldos financieros.

El pie incluye usuario y fecha de emisión disponibles. No se añadieron firmas inexistentes en la plantilla oficial de referencia ni espacio reservado para ellas.

## Error del selector de facturas

El callback anterior consultaba siempre 25 registros y no llamaba `Query.getLimit()` ni `getPageSize()`. Vaadin rechazaba ese proveedor al validar su contrato. El selector ahora usa `query.getPage()` y `query.getPageSize()` y respeta el filtro solicitado. La prueba de regresión ejecuta el proveedor real del ComboBox con páginas de 25 y 50 registros y verifica la lectura del tamaño y su envío al backend.

## Alcance preservado

No se modificaron servicios de emisión, lógica financiera/fiscal, repositorios, esquema, migraciones ni política de permisos. El PDF se genera desde el snapshot confirmado. No crea PrintJob, no usa contaCloudPrint y no admite formatos térmicos. La generación posterior al COMMIT y la regeneración no vuelven a emitir ni aplicar crédito.

## Verificación

Los tres ejemplos de `output/pdf/NotaCredito-*-PRUEBA.pdf` cubren:

- Una línea y crédito parcial: DOP 14.16 aplicado a saldo DOP 1,000.00, dejando DOP 985.84.
- Setenta líneas, descripciones extensas, importes grandes y documento de siete páginas.
- Nombres extensos de empresa/cliente, ausencia de dirección/teléfono/identificación de cliente, NCF propio y afectado distintos, varios impuestos y saldo a favor en USD.

Las pruebas comprueban campos opcionales, etiquetas de impuestos, contenido histórico, totales, márgenes de los glifos, autorización, tenant/empresa/sucursal, nombres de archivo y ausencia de acciones ejecutables. Cada página de los ejemplos se renderiza a PNG para revisión visual.

La prueba E2E completa en navegador permanece pendiente: el acceso de la comprobación anterior llegó al login y la conexión se perdió durante el inicio de sesión. No se presenta una prueba de componentes o servicios como equivalente a ese recorrido visual.

Registro de esta verificación: `target/credit-note-pdf-redesign-verification.log`.

Resultado final: **33 PASS / 0 FAIL / 0 omitidas**. Incluye 21 pruebas unitarias y de documento/regresión, 3 de componentes/selector, 4 de autorización y 5 de PostgreSQL (una de concurrencia). Se inspeccionaron las nueve páginas renderizadas de los tres ejemplos: sin clipping, superposición ni texto fuera de márgenes. E2E de navegador: **PENDIENTE**.
