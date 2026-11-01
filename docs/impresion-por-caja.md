# Agentes de impresión: varias cajas y preferencia de PDF

## Configuración

Nuevo y Editar agente permiten seleccionar una o varias cajas de la misma sucursal mediante un MultiSelectComboBox. Debe seleccionarse al menos una caja activa de una sucursal autorizada. Cambiar la sucursal limpia las cajas incompatibles. Todas las cajas de un agente comparten la impresora física configurada localmente en contaCloudPrint.

Mostrar PDF al imprimir es una propiedad persistente de cada agente. El checkbox aparece en Nuevo/Editar y como dato de solo lectura en el detalle. Cambiar la preferencia requiere facturas.configurar_impresion; editar nombre y cajas requiere agentes_impresion.editar. El formulario conserva el valor anterior si el usuario solo tiene permiso de consulta. No existe una pantalla independiente de impresión de Facturación.

El listado muestra las dos primeras cajas y +N para las restantes, con el listado completo en un tooltip. Nombre, Sucursal y Cajas son flexibles; las acciones se dimensionan por contenido y quedan fijadas al borde derecho.

## Integridad y migraciones

V99 crea print_agent_cash_registers y migra cada asignación anterior. Las claves compuestas validan tenant, empresa, sucursal, agente y caja. Una caja no puede tener dos destinos activos de Facturación: un índice único parcial lo impide incluso frente a escrituras concurrentes. El estado de la asociación se deriva del agente mediante un trigger y una FK con ON UPDATE CASCADE; no puede ser elegido libremente por un cliente. Los agentes pendientes se validan de nuevo al completar el pairing antes de activar su destino.

El backend bloquea primero el agente y después las cajas en un orden determinista. La edición tiene control de versión para evitar sobrescribir cambios de otro administrador. La paginación del listado permanece en base de datos; las cajas se consultan por lote únicamente para los agentes de la página, evitando N+1 y paginación de colecciones en memoria.

V99 copia la preferencia de empresa anterior a cada agente y utiliza true cuando no había configuración, conservando el comportamiento existente. V100 verifica que las relaciones anteriores estén copiadas y retira print_agents.cash_register_id. La tabla anterior billing_print_settings se conserva como historial de migración; el código ya no la consulta ni la modifica.

El permiso agentes_impresion.editar se asigna a ADMINISTRADOR y a los roles que ya podían crear agentes. Las sesiones que conservan permisos antiguos necesitan iniciar sesión de nuevo para cargar ese permiso.

## Autorización y trabajos

La caja se obtiene de la factura confirmada. El backend resuelve su agente activo a partir de las asociaciones y aplica la preferencia de ese mismo agente. La solicitud resuelve el modo bajo bloqueo del agente antes de generar el ticket o insertar un trabajo. En modo PDF devuelve mostrarPdfAlImprimir=true sin ID de trabajo. En modo impresora devuelve el ID del único trabajo y mostrarPdfAlImprimir=false. Un reintento de un trabajo existente conserva el destino físico incluso si cambia la configuración; los trabajos históricos nunca provocan también apertura de PDF. Una nueva impresión explícita consulta la configuración actual.

POST /api/print-agents/jobs/claim obtiene la identidad del Bearer autenticado. Su consulta exige una asociación actual con la caja y no admite un agentId o cashRegisterId arbitrario para seleccionar trabajos. La reserva usa FOR UPDATE SKIP LOCKED. Descargar un documento no lo marca como impreso.

POST /api/print-agents/jobs/{id}/complete exige el mismo agente, tenant, empresa y claimId. Es idempotente. Un trabajo que ya estaba PROCESSING puede registrar su resultado después de retirar una caja: conserva el destino original y no se entrega a otro agente. La edición cancela los trabajos PENDING de las cajas retiradas dentro de la misma transacción.

No hay fallback entre agentes, redundancia ni balanceo. Revocar un agente invalida sus credenciales; el diálogo advierte cuántas cajas perderán ese destino. Agregar o quitar cajas no cambia las credenciales y no requiere pairing.

## Identidad de contaCloudPrint

GET /api/print-agents/me devuelve cashRegisters con objetos {id, name}. Esa colección es la identidad autorizada y se actualiza con la sincronización del cliente. Los alias cashRegisterId (solo si hay una caja) y cashRegister (texto de las cajas) se mantienen por compatibilidad de lectura con clientes instalados; no se utilizan para autorizar ni enrutar.

contaCloudPrint muestra Cajas como una lista de solo lectura con wrapping. No existe selector local de cajas. Printer-config.txt y SecretStore conservan sus responsabilidades anteriores.

## PDF y transacción de venta

El checkbox define dos salidas mutuamente excluyentes en Facturación y Últimas facturas:

- Marcado: abre el PDF normal mediante FacturaVentaPdfService.generar. No genera ticket POS de 80 mm, no crea trabajo y no envía nada a contaCloudPrint. La falta de heartbeat del agente no bloquea esta salida.
- Desmarcado: genera el ticket POS y crea un único trabajo para contaCloudPrint; no abre PDF automáticamente. Se mantienen las reglas de cola cuando el agente está desconectado, sin fallback a PDF.

Ver PDF siempre utiliza el generador normal y nunca crea trabajos, independientemente del checkbox. Imprimir se deshabilita en el cliente durante la solicitud; la cola conserva su idempotencia backend. La configuración permanece en el agente, sin cambios en printer-config.txt. Un fallo de salida no revierte la venta.

La solicitud de cola se realiza desde la interfaz después de confirmar la transacción de venta. Una caída entre confirmar y solicitar requiere una impresión explícita desde el historial; no se implementa un outbox financiero en esta versión. Las reservas PROCESSING con resultado físico incierto requieren revisión antes de reimprimir.

## Validación

Las pruebas de PostgreSQL son opt-in mediante CONTACLOUD_POSTGRES_TESTS=true y las variables DB_URL/DB_USERNAME/DB_PASSWORD del entorno; usan la base existente conta_cloud y registros temporales con IDs aleatorios, que se eliminan al finalizar. Hibernate valida el esquema; estas pruebas no crean tablas ni bases de datos. Cubren integridad compuesta, activación y asignación concurrentes, JPA, cambios de sucursal, consumo concurrente y autorización de cajas. Las pruebas de cliente cubren la identidad de varias cajas y el reintento de confirmación sin doble impresión.

La verificación de componentes Vaadin cubre las resoluciones 1366×768, 1600×900 y 1920×1080 con escalados de 100 %, 125 % y 150 %. No se ejecutan impresiones físicas como parte de esas pruebas.
