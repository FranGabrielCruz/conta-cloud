# Recuento de caja POS de 80 mm

`CASH_COUNT` identifica un documento distinto de factura, devolución, recibo, vale y cierre. `CashCountPrintDocument` recibe los importes, cantidades, diferencia y estado confirmados; `CashCount80mmPdfRenderer` solamente los presenta. No consulta ventas, cobros, movimientos ni saldos actuales. La numeración se mantiene como la genera ContaCloud (`RCJ-…`); el agente no la genera ni la modifica.

## Historial y confirmación

La migración nueva V104 añade `cash_counts.pos_print_snapshot` y amplía los tipos permitidos de la cola sin modificar las migraciones anteriores. Los nuevos recuentos conservan empresa, identificación, dirección, teléfono, zona horaria aplicada a las fechas, caja, sucursal, responsable, apertura, moneda, denominaciones, orden, subtotales, importes, resultado y observación.

La captura ocurre dentro de la transacción existente, después de registrar y confirmar las denominaciones y antes de completar la idempotencia. No genera PDF, consulta agentes ni imprime. El snapshot se escribe una vez; las reimpresiones no cambian si se modifica el catálogo de denominaciones, la identidad de la empresa o del cajero, o el saldo de la caja. Se preservan el bloqueo de sesión, la secuencia y la idempotencia del negocio.

El recuento continúa siendo independiente del cierre: no se agregan ventas, estadísticas del turno, movimientos ni efectos contables.

## Salidas

Los botones **Imprimir recuento** de Facturación y Operaciones de caja utilizan `CashCountPrintService` y `BillingPrintRouter` con la caja y sucursal inmutables del recuento. Cada diálogo conserva la clave de su intento de impresión; al volver a abrirlo se permite una nueva reimpresión explícita.

- **Impresión por pantalla (PDF)** activada: se muestra el ticket de 80 mm de los nuevos recuentos, con cero trabajos y sin contactar al agente.
- Desactivada: se crea un trabajo `CASH_COUNT` dirigido al agente autorizado de esa caja. La generación interna del PDF no abre pestañas ni visores. Un error, falta de agente o revocación no produce un PDF de respaldo ni cambia de agente.
- **Ver PDF** es una acción manual separada. Conserva el formato normal existente; para nuevos recuentos sus datos de presentación proceden del snapshot. No crea trabajos.

Las solicitudes de impresión tienen transacción propia, posterior al guardado. Un error de impresión no vuelve a ejecutar el conteo ni altera su apertura, denominaciones, saldo esperado, diferencia o estado. El agente reutiliza la entrega silenciosa y los acuses existentes, incluida la protección contra imprimir de nuevo por un acuse fallido.

## Formato y seguridad

Ancho físico de 80 mm, márgenes de 3,5 mm y altura dinámica sobre el motor compartido. La tabla numérica mide sus tres columnas y alinea denominación, cantidad entera e importe a la derecha. Respeta el orden histórico cuando existe; en su ausencia ordena de mayor a menor. Omite cantidades cero por defecto; el modelo admite conservarlas explícitamente cuando lo exige el snapshot.

La moneda es dinámica. `TOTAL EFECTIVO`, `DIFERENCIA` y el estado tienen jerarquía visual. Se conservan el signo negativo, el cero numérico y el estado confirmado, sin derivar uno del otro en el renderer. Observación y responsable son opcionales y admiten ajuste de líneas. El comprobante no lleva marca de la aplicación.

El backend exige `recuentos_caja.imprimir`, consulta por tenant y empresa y valida sucursal, caja y asignación al agente. El contenido se representa como texto PDF, sin HTML activo, comandos, acciones ni rutas ejecutables. Los errores de render registran identificador y TraceId, sin contenido financiero ni secretos.

## Recuentos anteriores

Los recuentos previos sin snapshot conservan su PDF normal y pueden visualizarlo por decisión explícita o con el modo de pantalla activado. No se inventa identidad histórica usando nombres o saldos actuales para producir un ticket POS. Su impresión física exige los datos históricos que no almacenaban; la aplicación devuelve un error controlado.

## Comprobaciones

Las pruebas cubren ancho y márgenes, superposición, altura, cantidades, diferencias y estados, denominaciones cero, orden histórico, monedas, importes grandes, caracteres españoles, nombres y observaciones largas, contenido literal, ausencia de acciones PDF, reconocimiento del tipo y recepción autenticada con guardado silencioso y acuse.

En el backend se verifican las dos salidas del router, origen y tipo del trabajo, idempotencia, permisos, aislamiento, asignación retirada y revocación sin fallback, captura y lectura histórica, errores de render sin modificación, migración compatible y persistencia del snapshot después de la inserción inicial. La reimpresión no ejecuta comandos financieros.

Los ejemplos están en `target/cash-count-review/`; el PDF de demostración usa `RCJ-PRUEBA-000012` y datos ficticios. `scripts/check-pos-renderer-parity.ps1` compara los doce archivos compartidos. Reiniciar el backend para aplicar V104 y recompilar/reiniciar el agente desde la configuración local habitual.

La aceptación con PostgreSQL, backend en ejecución e impresora física continúa pendiente. Las pruebas PostgreSQL opcionales no se habilitan automáticamente ni se modifica la base operativa durante esta verificación.

## Resultado local

- Agente: 147 pruebas unitarias y 38 de integración satisfactorias; `verify` y análisis de dependencias sin errores.
- Reportes finales del backend: 726 pruebas, 715 satisfactorias y 11 PostgreSQL opcionales omitidas. La corrección de la prueba de los botones se verificó por separado; no requirió cambios al código de producción.
- Persistencia JPA: el snapshot añadido después de la primera inserción sobrevive al flush y a la recarga y rechaza una segunda captura.
- La recepción autenticada simulada de `CASH_COUNT` guarda el PDF, confirma el trabajo y no abre visores ni envía a una impresora real.
- Doce archivos del núcleo compartido idénticos; ticket de demostración revisado visualmente y verificaciones geométricas sin recorte ni superposición.
- Imagen Windows de verificación: `target/cash-count-build/dist/contaCloudPrint/contaCloudPrint.exe`, con runtime incluido; SHA-256 del JAR empaquetado idéntico al verificado. El origen de producción sigue pendiente de aprobación; para localhost se utiliza la configuración habitual de IntelliJ. Esta imagen no reemplaza una distribución firmada con origen aprobado.
