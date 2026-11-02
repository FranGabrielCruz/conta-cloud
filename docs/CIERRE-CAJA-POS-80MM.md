# Cierre de caja POS de 80 mm

`CASH_CLOSING` utiliza `CashClosingPrintDocument` y `CashClosing80mmPdfRenderer`, compartidos con ContaCloud y comprobados por paridad. El rediseño separa la composición de cobros, el dinero físico y la evidencia del conteo sin cambiar el cálculo ni la confirmación del cierre.

## Organización del ticket

El documento tiene ancho físico de 80 mm, márgenes de 3,5 mm y altura dependiente del contenido. El orden visual es:

1. Empresa y CIERRE DE CAJA: documento, fecha, sucursal, caja y usuario.
2. DATOS DEL TURNO: identificador histórico y apertura. Los identificadores largos se ajustan completos a varias líneas.
3. RESUMEN DE EFECTIVO: fondo inicial, entradas, salidas y EFECTIVO ESPERADO destacado.
4. FORMAS DE COBRO: nombres e importes históricos, con TOTAL COBRADO confirmado.
5. CUADRE DE CAJA: esperado, contado, DIFERENCIA con moneda dinámica y estado confirmado.
6. DESGLOSE DEL EFECTIVO CONTADO: DENOMINACIÓN / CANT. / IMPORTE y TOTAL CONTADO.
7. OBSERVACIÓN opcional, Cerrado por, firma opcional y pie.

El cuadre aparece antes de las denominaciones. Las filas con cantidad cero se omiten siempre, incluso si un snapshot anterior conservaba la antigua opción de incluirlas. Se respeta el orden histórico explícito; sin él se ordenan de mayor a menor. Las cantidades son enteras y los importes se alinean a la derecha. No se muestra la marca del producto ni se reservan líneas para datos opcionales inexistentes.

El renderer no suma cobros, multiplica denominaciones, calcula saldo esperado ni reclasifica diferencias. TOTAL CONTADO y Efectivo contado imprimen el mismo valor confirmado. Los signos y CUADRADO/FALTANTE/SOBRANTE provienen del backend. Una discrepancia de subtotales se registra como diagnóstico sin corregir datos. Los textos se procesan literalmente y no crean acciones PDF ni ejecutan HTML, rutas, comandos o ESC/POS.

## Formas de cobro y efectivo

Al confirmar un cierre nuevo se reutiliza la agregación oficial `FinancialMovementRepository.formasPagoSesion`, ya usada en la consulta de movimientos. Las aplicaciones de pago de facturas se vinculan por el turno de la factura, incluyendo destinos bancarios. Los recibos se incluyen cuando el modelo conserva su asociación explícita con ese turno, y se mantienen las reglas oficiales para documentos anulados y movimientos heredados. No se infiere una sesión por fechas ni por una cuenta bancaria compartida.

Los nombres e importes resultantes, su orden y el total se guardan dentro del snapshot. El total es la suma de esa agregación en el backend, no en contaCloudPrint. Una entrada manual de caja afecta al resumen de efectivo según las reglas existentes, pero no se incorpora como cobro. Tarjeta, transferencia y otros medios forman parte de TOTAL COBRADO sin incrementar por ello el efectivo esperado.

Si una operación antigua no conserva relación con un turno, no se asigna arbitrariamente a uno. En particular, los recibos totalmente bancarios sin asociación de turno no se reconstruyen mediante coincidencias temporales. Las formas con importe cero se omiten visualmente.

## Historial y compatibilidad

La migración V108 existente conserva el documento lógico en `cash_closures.pos_print_snapshot`. Este rediseño amplía su JSON con turno, resumen de efectivo tipado, formas de cobro y total cobrado. No necesita otra modificación de tablas ni altera migraciones anteriores.

La captura ocurre en la misma transacción del cierre, después de guardar denominaciones/medios y cerrar el turno, antes de completar la idempotencia. Guarda identidad, país, dirección/teléfono disponibles, nombres históricos, fechas convertidas una sola vez a la zona empresarial, moneda, cantidades e importes. No renderiza PDF ni contacta con el agente durante la confirmación.

Los snapshots originales siguen siendo legibles. Si faltan turno o resumen tipado, se toman exclusivamente de los campos históricos inmutables de ese mismo cierre, sin cálculos financieros ni cambios del JSON guardado. Las fechas ya capturadas no se convierten de nuevo.

Los cierres anteriores no conservan la nueva agregación de cobros. Se mantienen sus métodos e importes históricos y su total con la etiqueta original (por ejemplo, TOTAL ENTRADAS), identificando ese resumen histórico, sin presentarlo como un TOTAL COBRADO nuevo. La reimpresión nunca consulta cobros actuales para rellenarlo. Los cierres sin JSON usan únicamente sus campos y filas históricas guardadas, con ID genérico y omisión de país/dirección/teléfono desconocidos. Un JSON dañado produce un error controlado y no se sustituye por datos actuales.

Los campos del modelo anterior de ventas y operaciones se conservan para leer historia; el nuevo diseño no los mezcla con los cuatro bloques aprobados. No se añaden métricas financieras ausentes ni se cambia su clasificación de negocio.

## Enrutamiento, seguridad y PDF manual

`BillingPrintApplicationService` y `BillingPrintRouter` conservan la única decisión sobre el destino:

- `PrintOnScreen=true`: PDF POS visible y cero trabajos para el agente.
- `PrintOnScreen=false`: trabajo CASH_CLOSING para el agente asignado a la caja cerrada; el backend no abre PDF ni cambia de destino ante fallos.

Se conserva la preferencia confirmada del usuario para Microsoft Print to PDF: guardar y solicitar abrir el archivo en el visor predeterminado. Con una impresora física se mantiene el controlador Windows sin visor. Un fallo del visor conserva el archivo y no repite la impresión.

Ver PDF sigue siendo una acción manual independiente y mantiene la plantilla normal de CierreCajaPdfService. No pasa por la cola ni vuelve a confirmar operaciones. La salida tiene transacción independiente posterior al COMMIT del cierre. Reimprimir no consume secuencias, cierra otra vez, registra movimientos, reabre el turno ni modifica saldos. Los botones comunes conservan la protección frente a clics simultáneos y reintentos.

Las consultas aplican tenant y empresa y comprueban sucursal y origen guardado. Se exigen imprimir y consultar cierres/historial en backend. El frontend no puede proporcionar AgentId. Se preservan asignación única, varias cajas por agente, tratamiento de agentes revocados/desconectados y correlación. Los registros no incluyen credenciales ni contenido de medios de pago.

## Verificación y uso

Las pruebas cubren efectivo, tarjeta, transferencia, medios personalizados, ceros, total suministrado sin recalcular, orden visual, cantidades/diferencias/estados, turnos largos, textos españoles, altura, ancho, márgenes y solapamientos. La prueba SQL ejecuta la agregación oficial en una base aislada con varios turnos, empresas, tenants, documentos anulados, pagos bancarios y entradas manuales. Las pruebas de historia comprueban que reimprimir no consulta fuentes actuales y que la ampliación lee el JSON anterior. Se conserva la recepción HTTP autenticada simulada con guardado de bytes originales y confirmación del trabajo.

Recompilar y reiniciar ContaCloud y el agente con su configuración habitual. Si aún no se aplicó V108, el reinicio del backend la aplicará. Los trabajos ya encolados conservan su PDF original: solicitar otra impresión para obtener el diseño nuevo. La aceptación con PostgreSQL en ejecución e impresora física requiere la comprobación en el entorno real; las pruebas automáticas no modifican la base operativa.

Validación del rediseño, 02/11/2026: agente con 169 pruebas unitarias y 44 de integración aprobadas, análisis estático y de dependencias correctos. Backend con 836 pruebas: 822 aprobadas y 14 opcionales omitidas, sin fallos ni errores. Se conservaron los cambios paralelos de la consulta oficial de movimientos. Los 16 archivos del núcleo POS coinciden. El PDF final tiene una página de ancho 80 mm y altura dinámica de 710,34 pt; se revisó visualmente el nuevo orden, la separación entre bloques y la alineación sin recortes ni solapamientos.
