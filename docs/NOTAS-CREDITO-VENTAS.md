# Ventas — Notas de crédito

## Comportamiento

La ruta `/notas-credito` sustituye el placeholder de Ventas. Incluye listado paginado desde backend (10 registros iniciales), búsqueda por nota/factura/cliente, filtro de cliente y fechas, selector paginado de facturas, preparación, cálculo backend, confirmación de emisión, consulta histórica y PDF A4.

La nota independiente parte de una factura confirmada elegible, con cliente registrado o de Consumidor Final. La pantalla permite elegir cantidades parciales o todas las cantidades disponibles; precios, descuentos, recargos, impuestos y moneda proceden de la factura histórica. El catálogo de motivos reutiliza `sales_return_reason` de la misma empresa.

Los estados son Emitida (`CONFIRMED`) y Anulada (`VOIDED`). La anulación conserva el documento, revierte solo sus efectos y registra auditoría e idempotencia; no incorpora edición de notas emitidas ni borradores. Véase [Anulación y origen](NOTAS-CREDITO-ANULACION-Y-ORIGEN.md).

## Emisión e integridad

`NotaCreditoVentaService.emitir` se ejecuta en una transacción con idempotencia PostgreSQL, bloqueo de factura y líneas, revalidación de cantidades e importes, aplicación financiera, numeración, persistencia del documento y auditoría. El número reutiliza `sales_credit_note_sequence`. Una excepción revierte todas esas escrituras.

La disponibilidad suma devoluciones físicas anteriores y notas financieras independientes. El consumo de todas las líneas se obtiene en una consulta agrupada por factura, evitando una consulta adicional por producto. Las notas originadas por devoluciones se contabilizan a través de sus líneas de devolución, una sola vez. El flujo existente de devoluciones también descuenta las cantidades ya acreditadas financieramente; un crédito financiero no se presenta como retorno físico ni modifica inventario.

Los importes usan `BigDecimal` y cuatro decimales internos. La última cantidad disponible recoge el residuo de redondeo de cada componente de la factura. El backend rechaza sobreacreditación, líneas ajenas/repetidas, cantidades negativas o con precisión inválida y períodos existentes cerrados o bloqueados. Si no existe un período configurado para esa fecha, mantiene el comportamiento operativo existente; no inventa un calendario fiscal.

## Efecto financiero

Cuando existe cliente, se bloquea también el cliente y la cuenta por cobrar de la factura en su moneda. El crédito reduce el saldo pendiente hasta agotarlo, aumentando el importe liquidado (`applied_amount`) y conservando en el documento cuánto se aplicó. El excedente genera saldo a favor en `customer_credit_ledger`. Una factura ya pagada genera íntegramente saldo a favor.

Sin cliente registrado se confirma el crédito contra la factura y se consume su disponibilidad, con aplicación a CxC y saldo a favor en cero. No se crea cliente ficticio, cuenta de crédito ni reembolso automático.

No se generan reembolsos en efectivo, movimientos de caja, vales, recibos ni movimientos de inventario. Los documentos anteriores originados por devolución se consultan sin reaplicar ni corregir retroactivamente sus efectos financieros.

## Datos históricos y PDF

La migración nueva `V112__notas_credito_ventas_independientes.sql` conserva las notas de devolución existentes, permite el nuevo origen `FINANCIAL` y agrega líneas con claves foráneas compuestas que incluyen empresa, tenant y factura. No modifica migraciones anteriores.

Las notas nuevas guardan `document_snapshot` con identidad empresarial al emitir, nombre legal, correo y logo disponibles, identidad del cliente de la factura, líneas, valores y aplicación financiera. El logo utiliza el mismo cargador y límite de los documentos oficiales. Los snapshots anteriores sin logo no inventan retroactivamente una imagen histórica. `NotaCreditoVentaPdfService` lee ese documento: generar o regenerar el PDF no vuelve a emitir la nota. Un fallo posterior del PDF mantiene el documento y sus efectos ya confirmados.

El renderer utiliza la base Jasper oficial de Orden de Compra, A4 horizontal (842 × 595), márgenes laterales de 30 puntos y ancho útil de 782 puntos. Reutiliza encabezado, logo, estilos, paginación y composición de totales; mantiene encabezados repetidos, ajuste de texto e impuestos históricos. Omite campos opcionales ausentes y no incluye marca ContaCloud, vínculos activos, JavaScript ni acciones ejecutables. Los nombres de archivo pasan por la validación de nombres de documentos existente.

Las notas antiguas sin snapshot propio se reconstruyen exclusivamente desde la devolución y la factura persistidas, sin consultar datos maestros actuales ni escribir saldos. La zona histórica de la factura se usa cuando existe; para registros anteriores a ese dato se mantiene la zona predeterminada del modelo (`America/Santo_Domingo`). No se inventa usuario emisor ni identidad que no quedó registrada.

## Límites fiscales explícitos

La factura POS actual no persiste NCF propio y no existe un flujo fiscal operativo de emisión de NCF para estas notas. Por eso no se genera ni se simula un NCF; los campos ausentes se omiten en PDF y se representan vacíos en consulta. La implementación no debe presentarse como un emisor de comprobantes fiscales electrónicos ni un motor de asientos contables.

El cliente registrado es opcional. La denominación se obtiene de la factura histórica y, si está vacía, se muestra Consumidor Final. Las notas antiguas de devolución de cliente genérico siguen siendo consultables. No se captura una dirección actual del cliente para simular una dirección histórica que la factura no conservó.

## Autorización e impresión

El servicio exige módulo `VENTAS` y permisos específicos `notas_credito.ver`, `.crear`, `.emitir` y `.pdf`; emitir requiere crear y emitir. Los filtros de tenant y empresa provienen de autenticación y el acceso respeta las sucursales autorizadas. Ocultar controles no sustituye estas verificaciones backend.

La nota de crédito independiente no utiliza `BillingPrintRouter`, `PrintJob`, PrintAgent, PrintOnScreen ni formatos térmicos de 50/58/80 mm. Su salida es exclusivamente el PDF A4. El sistema de impresión de devoluciones existente conserva su documento independiente.

## Verificación reproducible

Pruebas del cambio: `NotaCreditoVentaServiceTest`, `NotaCreditoVentaPdfServiceTest`, `NotaCreditoVentaAuthorizationTest`, `NotaCreditoVentaPostgresTest` y `NotasCreditoVentaViewTest`. Regresión relacionada: `DevolucionVentaServiceTest` y `SalesReturnPostCommitTest`.

Las pruebas PostgreSQL se habilitan con `CONTACLOUD_POSTGRES_TESTS=true` y `DB_PASSWORD` de la configuración local. Validan el esquema con Hibernate, crean facturas/líneas aleatorias, usan las referencias existentes de empresa/cliente/catálogos solo para lectura y eliminan las operaciones de prueba al terminar. La numeración de prueba usa un prefijo aislado para no consumir números comerciales reales.

Los ejemplos en `output/pdf/NotaCredito-*-PRUEBA.pdf` son datos sintéticos sin efecto financiero real. La prueba PDF comprueba tamaño, contenido, ausencia de acciones y límites de todos los glifos, y renderiza cada página a PNG para revisión visual.

El flujo completo en navegador requiere una sesión iniciada en una empresa de pruebas. No debe confundirse la prueba de componentes y servicios con una prueba E2E del navegador.
