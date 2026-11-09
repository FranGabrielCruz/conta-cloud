# Ventas > Recibos

El historial consulta `customer_receipt` por tenant, empresa y sucursales autorizadas. No ofrece alta de cobros. La búsqueda y la paginación se ejecutan en base de datos, con filtros de fecha y estado. El detalle diferencia el total original de cada documento del importe aplicado.

El diálogo de cobros de Facturación y las aplicaciones de cuentas por cobrar siguen usando el REC existente. En una nueva venta al contado, Facturación numera y conserva el REC dentro de la misma transacción de confirmación. El vínculo único al ID de la factura permite presentar su pago y medios ya registrados sin crear una segunda aplicación contable, cuenta por cobrar ni movimiento. Consumidor Final conserva `customer_id` nulo. Las facturas históricas que no generaron un REC no se convierten retroactivamente.

El PDF grande se abre desde Ver recibo y usa la plantilla corporativa de Orden de Compra. Comparte el snapshot de emisión con el flujo térmico existente (50/58/80 mm). El historial puede mostrar las aplicaciones posteriores de un anticipo; el documento de emisión conserva su distribución original. Si el recibo es anterior al snapshot térmico, el PDF formal usa sus importes y etiquetas persistidos, y los datos de la empresa actual solo para completar el encabezado ausente. La impresión formal es de consulta: no crea cobros, movimientos ni trabajos del agente.

Los cobros de cuentas por cobrar se anulan mediante la reversión financiera existente, preservando el recibo y su motivo. Al revertir se bloquean los turnos antes de cambiar saldos; un turno cerrado o un movimiento conciliado impide la operación. Los recibos de ventas al contado no se anulan por separado, porque la factura seguiría pagada; la corrección corresponde al flujo de devolución. Los permisos de consulta, impresión y anulación se evalúan en servidor.

V118 agrega el vínculo de venta POS y conserva los datos originales de las aplicaciones (incluidas notas de débito), sin modificar las migraciones previas.

Verificación: 64 pruebas satisfactorias de permisos, impresión, Facturación y persistencia, incluidas dos con PostgreSQL. Flyway validó 118 migraciones. Se revisaron visualmente los PDF grande emitido y anulado con datos de prueba.
