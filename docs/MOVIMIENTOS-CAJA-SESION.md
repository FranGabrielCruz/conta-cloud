# Consulta de la sesión en Movimientos de caja

Integración en Caja: `/operaciones-caja?tab=movements`, segunda pestaña de Operaciones de caja. Comparte `MovimientosCajaPanel` con el diálogo del POS. El acceso administrativo usa `MovimientoCajaService.consultarCaja` con los permisos `operaciones_caja.ver` y `movimientos_caja.ver`; resuelve la sesión abierta por caja y permite consulta administrativa dentro de las sucursales autorizadas. El acceso POS conserva la validación del cajero. Ver `VERIFICACION-MOVIMIENTOS-CAJA.md` para la evidencia y la verificación visual pendiente.

La pantalla existente incorpora sesión, resumen, formas de pago y detalle paginado. No añade menú ni proceso financiero. Las acciones autorizadas Nuevo/Revertir siguen siendo comandos explícitos separados; abrir, filtrar, paginar o actualizar solo consulta información.

`MovimientoCajaService.consultar` valida módulo, permiso, tenant, empresa, sucursal y cajero como la consulta POS existente. Ejecuta las consultas en una transacción nueva de solo lectura con aislamiento REPEATABLE_READ. No toma bloqueos de escritura, modifica saldos, audita operaciones financieras ni genera trabajos de impresión. Cada actualización obtiene una nueva fotografía; los filtros solo afectan la página de movimientos.

## Significado de los valores

- Fondo inicial: importe persistido de apertura, una sola vez.
- Entradas de caja: entradas en efectivo distintas de ventas y cobros, siguiendo la clasificación de otras entradas del cierre.
- Salidas de caja: todas las salidas en efectivo registradas de la sesión, incluidos vales y devoluciones/reversiones válidas.
- Efectivo esperado: fondo inicial más todas las entradas CASH registradas menos todas las salidas CASH registradas. Coincide con la regla del recuento/cierre; no se deriva de las tarjetas de formas de pago.
- Formas de pago: importes aplicados de ventas confirmadas y recibos no anulados asociados explícitamente a la sesión. Se conservan identificadores y nombres históricos de los métodos, incluidos métodos personalizados. No se suma efectivo recibido antes del cambio, ni se cuentan dos veces documentos y movimientos financieros.
- Total operaciones: suma backend de esos importes de ventas/cobros por forma de pago. Excluye fondo, reposiciones, vales y ajustes; las devoluciones siguen siendo salidas de efectivo, sin reescribir el importe histórico del cobro.

El detalle conserva el alcance previo de movimientos financieros de efectivo de la sesión, orden descendente, filtros y consulta individual. No se inventa un movimiento de apertura cuando el modelo guarda el fondo únicamente en la sesión. No se muestran importes contados, diferencias ni denominaciones.

## Datos históricos sin sesión

El modelo actual solo conserva `cash_register_session_id` en las líneas en efectivo de recibos. Un recibo mixto se vincula mediante esa línea y muestra todos sus medios; un recibo exclusivamente bancario sin vínculo de sesión no se atribuye por fecha, usuario o caja de impresión, porque podría pertenecer a otro turno. Esta consulta no cambia la captura de recibos ni crea asociaciones históricas. Las ventas POS sí conservan su sesión para todos sus medios. Los movimientos comerciales antiguos sin líneas de pago utilizan su medio financiero como respaldo.

Sin ID de sesión se muestra el estado vacío. Si una sesión consultada se cierra, el siguiente refresco muestra CERRADA y desactiva Nuevo. Un error elimina los valores anteriores y permite reintentar. Las fechas siguen la conversión local ya utilizada por Movimientos de caja y el formato DD/MM/YYYY HH:mm.

## Rendimiento y migración

Los agregados se calculan en PostgreSQL y solo se carga la página solicitada (10 filas por defecto; 5/10/20/50). Se reutilizan los índices de sesión de movimientos y de facturas. V109 añade el índice de búsqueda de recibos por sesión; no cambia datos financieros ni migraciones anteriores.

## Verificación

Pruebas de servicio: importes, medios dinámicos, filtros, ausencia de efectos, falta de sesión, cierre y seguridad real de método. Pruebas Vaadin: presentación, actualización, error sin datos obsoletos, estado vacío y paginación. Pruebas PostgreSQL habilitadas con CONTACLOUD_POSTGRES_TESTS: consultas y repositorios reales, separación de sesiones/tenants, snapshot consistente y escritura concurrente sin bloquear. Sus fixtures aleatorios se eliminan al terminar.

Validación posterior de integración en Caja: 90 pruebas aprobadas, incluidas seis pruebas PostgreSQL, seguridad, navegación de componentes, UI y regresión de impresión. Migración V109 validada sin cambios. La verificación visual y E2E en navegador siguen pendientes: Computer Use se detuvo al no poder confirmar la URL actual del navegador.
