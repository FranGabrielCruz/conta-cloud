# Agentes de impresión: varias cajas y preferencia de PDF

## Configuración

Nuevo y Editar agente permiten seleccionar una o varias cajas de la misma sucursal mediante un MultiSelectComboBox. Debe seleccionarse al menos una caja activa de una sucursal autorizada. Cambiar la sucursal limpia las cajas incompatibles. Todas las cajas de un agente comparten la impresora física configurada localmente en contaCloudPrint.

Impresión por pantalla (PDF) es una propiedad persistente de cada agente. El checkbox aparece en Nuevo/Editar y como dato de solo lectura en el detalle. Cambiar la preferencia requiere facturas.configurar_impresion; editar nombre y cajas requiere agentes_impresion.editar. El formulario conserva el valor anterior si el usuario solo tiene permiso de consulta. No existe una pantalla independiente de impresión de Facturación.

El listado muestra las dos primeras cajas y +N para las restantes, con el listado completo en un tooltip. Nombre, Sucursal y Cajas son flexibles; las acciones se dimensionan por contenido y quedan fijadas al borde derecho.

## Integridad y migraciones

V99 crea print_agent_cash_registers y migra cada asignación anterior. Las claves compuestas validan tenant, empresa, sucursal, agente y caja. Una caja no puede tener dos destinos activos de Facturación: un índice único parcial lo impide incluso frente a escrituras concurrentes. El estado de la asociación se deriva del agente mediante un trigger y una FK con ON UPDATE CASCADE; no puede ser elegido libremente por un cliente. Los agentes pendientes se validan de nuevo al completar el pairing antes de activar su destino.

El backend bloquea primero el agente y después las cajas en un orden determinista. La edición tiene control de versión para evitar sobrescribir cambios de otro administrador. La paginación del listado permanece en base de datos; las cajas se consultan por lote únicamente para los agentes de la página, evitando N+1 y paginación de colecciones en memoria.

V99 copia la preferencia de empresa anterior a cada agente y utiliza true cuando no había configuración, conservando el comportamiento existente. V100 verifica que las relaciones anteriores estén copiadas y retira print_agents.cash_register_id. La tabla anterior billing_print_settings se conserva como historial de migración; el código ya no la consulta ni la modifica.

El permiso agentes_impresion.editar se asigna a ADMINISTRADOR y a los roles que ya podían crear agentes. Las sesiones que conservan permisos antiguos necesitan iniciar sesión de nuevo para cargar ese permiso.

## Autorización y trabajos

La caja se obtiene de la factura confirmada. El backend resuelve su agente activo a partir de las asociaciones y aplica la preferencia de ese mismo agente. La solicitud resuelve el modo bajo bloqueo del agente antes de generar el ticket o insertar un trabajo. En modo PDF devuelve printOnScreen=true sin ID de trabajo. En modo impresora devuelve el ID del único trabajo y printOnScreen=false. Un reintento de un trabajo existente conserva el destino físico incluso si cambia la configuración; los trabajos históricos nunca provocan también apertura de PDF. Una nueva impresión explícita consulta la configuración actual.

POST /api/print-agents/jobs/claim obtiene la identidad del Bearer autenticado. Su consulta exige una asociación actual con la caja y no admite un agentId o cashRegisterId arbitrario para seleccionar trabajos. La reserva usa FOR UPDATE SKIP LOCKED. Descargar un documento no lo marca como impreso.

POST /api/print-agents/jobs/{id}/complete exige el mismo agente, tenant, empresa y claimId. Es idempotente. Un trabajo que ya estaba PROCESSING puede registrar su resultado después de retirar una caja: conserva el destino original y no se entrega a otro agente. La edición cancela los trabajos PENDING de las cajas retiradas dentro de la misma transacción.

No hay fallback entre agentes, redundancia ni balanceo. Revocar un agente invalida sus credenciales; el diálogo advierte cuántas cajas perderán ese destino. Agregar o quitar cajas no cambia las credenciales y no requiere pairing.

## Identidad de contaCloudPrint

GET /api/print-agents/me devuelve cashRegisters con objetos {id, name}. Esa colección es la identidad autorizada y se actualiza con la sincronización del cliente. Los alias cashRegisterId (solo si hay una caja) y cashRegister (texto de las cajas) se mantienen por compatibilidad de lectura con clientes instalados; no se utilizan para autorizar ni enrutar.

contaCloudPrint muestra Cajas como una lista de solo lectura con wrapping. No existe selector local de cajas. Printer-config.txt y SecretStore conservan sus responsabilidades anteriores.

## Política global de salida de Facturación

El checkbox define dos salidas mutuamente excluyentes para los documentos soportados de Facturación: factura de venta y devolución de venta, incluidos sus historiales.

- Marcado: abre el PDF normal mediante FacturaVentaPdfService.generar. La devolución abre DevolucionVentaPdfService.generar. No genera ticket POS de 80 mm, no crea trabajo y no envía nada a contaCloudPrint. La falta de heartbeat del agente no bloquea esta salida.
- Desmarcado: genera el ticket POS y crea un único trabajo para contaCloudPrint; no abre PDF automáticamente. Se mantienen las reglas de cola cuando el agente está desconectado, sin fallback a PDF.

Ver PDF siempre utiliza el generador normal y nunca crea trabajos, independientemente del checkbox. Imprimir se deshabilita en el cliente durante la solicitud; la cola conserva su idempotencia backend. La configuración permanece en el agente, sin cambios en printer-config.txt. Un fallo de salida no revierte la venta.

La solicitud de cola se realiza desde la interfaz después de confirmar la transacción de venta. Una caída entre confirmar y solicitar requiere una impresión explícita desde el historial; no se implementa un outbox financiero en esta versión. Las reservas PROCESSING con resultado físico incierto requieren revisión antes de reimprimir.

## Validación

Las pruebas de PostgreSQL son opt-in mediante CONTACLOUD_POSTGRES_TESTS=true y las variables DB_URL/DB_USERNAME/DB_PASSWORD del entorno; usan la base existente conta_cloud y registros temporales con IDs aleatorios, que se eliminan al finalizar. Hibernate valida el esquema; estas pruebas no crean tablas ni bases de datos. Cubren integridad compuesta, activación y asignación concurrentes, JPA, cambios de sucursal, consumo concurrente y autorización de cajas. Las pruebas de cliente cubren la identidad de varias cajas y el reintento de confirmación sin doble impresión.

La verificación de componentes Vaadin cubre las resoluciones 1366×768, 1600×900 y 1920×1080 con escalados de 100 %, 125 % y 150 %. No se ejecutan impresiones físicas como parte de esas pruebas.

## Extensión de documentos y agente revocado

BillingPrintRouter concentra la resolución, comprobación bajo bloqueo, elección PDF/impresora, límites, idempotencia y creación del trabajo tipado. BillingPrintService y DevolucionVentaPrintService son adaptadores autorizados: obtienen el origen persistido y aportan el renderer 80 mm correspondiente. El router no ejecuta comandos de venta, inventario o reembolso. Las solicitudes se ejecutan en una transacción nueva después de confirmar el negocio.

Para incorporar otro documento de Facturación, crear su adaptador con autorización y origen persistido y delegar al mismo router. No insertar trabajos ni interpretar el checkbox por separado. Los tipos semánticos existentes son SALES_INVOICE y SALES_RETURN; PDF es el formato, no un tipo de documento. No se aplica esta política automáticamente a otros módulos o documentos no integrados.

Se prioriza el único agente activo asignado a la caja. Si no existe, una única asociación revocada puede aportar su preferencia exclusivamente si está en modo PDF. Varias asociaciones históricas revocadas se consideran ambiguas: requieren corregir la asignación. Nunca se envía un trabajo a un agente revocado ni se elige una caja o agente alternativo. La conectividad/heartbeat no interviene en la salida PDF.

El cliente recibe los bytes PDF y su tipo semántico, utilizando el perfil y ancho locales existentes. printPdf utiliza una salida silenciosa: ni una impresora física ni el destino virtual Microsoft Print to PDF abren un visor para trabajos recibidos. La prueba local manual puede seguir mostrando su PDF. La cola mantiene la confirmación y el reintento de ACK sin repetir la salida.

## Nombre de configuración y despliegue (V102)

La interfaz usa **Impresión por pantalla (PDF)** y la ayuda «Al imprimir, muestra el PDF en pantalla en lugar de enviarlo a la impresora.» El modelo, DTOs y auditoría usan printOnScreen; la columna activa es print_on_screen. V102 renombra las columnas de print_agents y billing_print_jobs sin UPDATE, sin invertir valores y conservando el default true. La antigua tabla billing_print_settings sigue siendo únicamente historial retirado, sin lecturas ni escrituras de la aplicación; sus nombres históricos y las migraciones ya aplicadas se conservan.

Los DTOs aceptan showPdfOnPrint y mostrarPdfAlImprimir exclusivamente como alias JSON de entrada para el mismo valor; solo emiten printOnScreen. No existen propiedades o columnas activas duplicadas. El backend y la interfaz Vaadin se distribuyen en el mismo ejecutable: aplicar Flyway y reiniciar ContaCloud con la versión actualizada, sin mantener instancias antiguas consultando el esquema renombrado. Recargar las pantallas tras el reinicio. contaCloudPrint no recibe ni interpreta esta propiedad y no requiere actualización por este renombrado.

## Enrutamiento obligatorio de Facturación: factura, devolución y recibo

La matriz de salida es común para SALES_INVOICE, SALES_RETURN y PAYMENT_RECEIPT: printOnScreen=true devuelve SCREEN sin generar ticket ni Print Job; false devuelve PRINT_AGENT con un único trabajo tipado y no abre PDF. SolicitudImpresionResultadoDto.outputMode deriva ese modo de la decisión del backend, manteniendo el contrato printOnScreen existente. FacturacionPosView.procesarSalidaImpresion es el único manejador de salida de las tres acciones Imprimir.

Auditoría de entradas:

| Acción | Adaptador autorizado | Impresión física |
| --- | --- | --- |
| Imprimir/Reimprimir factura | BillingPrintService → BillingPrintRouter | Factura 80 mm |
| Imprimir/Reimprimir devolución | DevolucionVentaPrintService → BillingPrintRouter | Devolución 80 mm |
| Imprimir/Reimprimir recibo | PaymentReceiptPrintService → BillingPrintRouter | Recibo de cobro 80 mm |
| Ver PDF explícito | Generador del documento, sin router ni cola | Ninguna |

RecibosCobrosDialog separa los callbacks Imprimir (documento + intento) y Ver PDF; se retira el constructor ambiguo que aceptaba un Consumer de un solo ID como impresión. Su botón deshabilita clics en el cliente y conserva el intento para reintentos. Los fallos de salida después de confirmar se informan como fallos de impresión y no vuelven a ejecutar el cobro.

La política incluye también recuento, vale y cierre de caja. Los seis tipos están registrados obligatoriamente en BillingPrintDocumentRegistry y las acciones Imprimir de POS y Operaciones de caja usan BillingPrintUi. No se extiende a Compras, Inventario o Contabilidad.

### Distribución verificada

Los binarios anteriores encontrados no incluían PAYMENT_RECEIPT en el cliente ni el adaptador/reporte de recibo en ContaCloud, pese a existir en el código fuente. Deben desplegarse ambos ejecutables reconstruidos, con V103 aplicada mediante Flyway. El cliente procesa el PDF recibido mediante printSilently, sin abrir visores ni decidir printOnScreen. El diseño de los tres reportes de 80 mm permanece intacto.


La especificación vigente de los seis documentos y el contrato de extensión están en [POLITICA-GLOBAL-IMPRESION.md](POLITICA-GLOBAL-IMPRESION.md); reemplaza las listas parciales anteriores de este documento.
