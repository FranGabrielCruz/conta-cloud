# Notas de débito de ventas

La ruta `notas-debito` permite listar, emitir, consultar, imprimir y anular notas sobre facturas confirmadas. Las consultas y comandos validan permisos, módulo, tenant, empresa y sucursal en backend. El selector incluye facturas con y sin cliente; estas últimas se muestran como Consumidor Final.

## Condición de la nota

La condición es independiente de la factura original:

| Cliente de la factura | Contado | Crédito |
| --- | --- | --- |
| Identificado | Permitido, con cobro completo | Permitido, con saldo pendiente |
| Consumidor Final | Permitido, con cobro completo | Rechazado en backend |

La interfaz inicia en Contado y bloquea el cambio a Crédito para facturas sin cliente. Se usa el enum existente CASH/CREDIT. El backend no crea clientes ni modifica el CustomerId, los importes, el estado histórico o el inventario de la factura.

## Cobro inmediato

El pago reutiliza `AplicacionCobroInput`, el cálculo decimal y la validación extraída del POS a `CobroContadoValidacion`. Los métodos provienen del catálogo empresarial, con sus requisitos de referencia y cuenta destino. Se soportan pagos combinados y un único componente de efectivo; solo el efectivo puede generar cambio.

Cada pago registra exactamente un movimiento en el libro monetario central, vinculado con la nota y su forma de pago. El ingreso corresponde al importe aplicado, nunca al recibido bruto ni al cambio. Contado queda pagado con saldo cero y no crea una cuenta por cobrar pendiente.

El efectivo requiere caja activa de la misma moneda/sucursal y turno abierto; se vuelve a validar todo en backend. Los métodos no efectivos necesitan una cuenta bancaria válida. Se conservan referencias de operación; no se solicitan PAN, CVV o PIN.

## Crédito y cobros posteriores

Cada nueva nota a crédito tiene una cuenta por cobrar independiente identificada con el NDV y enlazada a su factura histórica. El cobro de recibos selecciona esa cuenta y conserva el número/fecha de la nota en el snapshot de aplicación. Un mismo recibo puede aplicarse a la factura original y a sus notas, sin confundir los documentos.

El saldo/pagado actual de la nota se consulta desde su cuenta. Los cobros y créditos propios de la factura original mantienen su cuenta original; no consumen cuentas de notas independientes. Las facturas antiguas a crédito sin cuenta materializada conservan su obligación inicial cuando se emite una nueva nota.

## Caja y anulación

Los resúmenes y cierres utilizan los movimientos centrales y las formas de pago asociadas; no suman otra vez el total documental de la NDV. Los pagos mixtos se distribuyen por método y el efectivo esperado incluye solo el componente de caja.

Anular exige motivo y versión vigente. En Contado se anulan los movimientos originales y se conserva el historial; se rechaza si el turno está cerrado o el movimiento está conciliado. En Crédito se anula la obligación independiente únicamente si sus aplicaciones fueron revertidas. La nota nunca se elimina.

## Atomicidad, migraciones e historial

Emisión, pagos o cuenta por cobrar y auditoría forman una transacción. Idempotencia, huella y bloqueos evitan duplicar documentos, movimientos o reversiones. La numeración NDV es independiente por tenant/empresa.

V114 y V115 conservan su contenido aplicado. V116 incorpora condición, pagos monetarios y cuentas independientes por nota. Las notas anteriores permanecen identificadas como LEGACY_INVOICE y conservan su cuenta financiera original; sus snapshots antiguos se leen con condición Crédito. V117 permite que un recibo aplique, en una transacción, a varias cuentas originadas por la misma factura.

Los permisos se asignan por roles normales, incluida DEMO. Los permisos existentes para cobrar transferencias/cheques siguen aplicándose. Se registran auditoría y logs con TraceId.

## PDF y validación

El PDF grande usa la geometría oficial de Orden de Compra, datos históricos y logo. Muestra separadamente la condición de factura y de nota. Contado incluye formas de pago, recibido, cambio y saldo cero; Crédito no muestra pagos inexistentes. ANULADA se identifica de forma inequívoca.

La apertura automática se ejecuta después del retorno del servicio transaccional, respetando el permiso de PDF. Un fallo o bloqueo del navegador informa que la nota sigue emitida; Imprimir reintenta sobre el mismo ID. No se utilizan agentes, trabajos de impresión ni formatos térmicos.

Las pruebas usan facturas y caja/turno de prueba aislados. Los escenarios con cuentas bancarias o numeración operativa se revierten al terminar. La validación interactiva de UI/E2E permanece pendiente porque el control de Chrome no puede verificar su URL.

## Resultado de verificación

132 casos distintos satisfactorios entre unidades, seguridad, integración con PostgreSQL, componentes de interfaz y regresión de notas de crédito, recibos, POS y cierre de caja. Incluyen 24 escenarios de integración de notas de débito, el cobro conjunto de factura y NDV, su reversión, notas históricas y protección ante turnos cerrados/conciliaciones. Flyway validó y aplicó el esquema hasta V117. Se revisaron visualmente los PDF de contado, crédito y anulación. La UI/E2E interactiva sigue pendiente por el bloqueo de control de Chrome descrito arriba.

## Rediseño de Nueva nota de débito

El formulario reúne Fecha, Motivo y Condición en una fila adaptable. La factura de origen mantiene ocho datos de consulta en cuatro columnas y distingue su condición de la condición de la nota. La justificación es compacta; el detalle conserva altura automática, columnas numéricas acotadas y acciones por icono. Los totales se sitúan inmediatamente debajo del detalle, a la derecha, con moneda dinámica y descuento únicamente cuando existe.

PAGO se oculta completamente para Crédito. Para Contado, las filas muestran recibido y cambio solo con efectivo, y cuenta/referencia según el método. La edición de pagos actualiza su resumen y la disponibilidad de Emitir sin recalcular los cargos en el servidor.

Las cajas candidatas deben estar activas, autorizadas y coincidir con la sucursal y moneda de la factura; solo se ofrecen las que tienen una sesión abierta. Una se selecciona automáticamente y aparece como información; varias requieren selección compacta; ninguna bloquea el efectivo con un mensaje claro. El backend aplica la misma resolución cuando se omite la caja y vuelve a bloquear y validar la sesión al emitir. No se modifica el cálculo financiero, el inventario ni el flujo de PDF posterior al commit. Este rediseño no requiere migración.

Verificación del rediseño: 103 pruebas satisfactorias, sin errores ni omisiones: 50 de servicios, autorización y componentes, más 53 de integración y regresión. Incluyen 26 casos con PostgreSQL y los nuevos escenarios de resolución automática de caja y ausencia de sesión sin efectos parciales. Flyway validó 117 migraciones, sin cambios de esquema. La revisión visual interactiva a distintas resoluciones sigue pendiente: la herramienta de control no pudo verificar la dirección de Chrome y detuvo el acceso.
