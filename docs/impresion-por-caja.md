# Impresión de Facturación por caja

La empresa y el tenant se obtienen de la autenticación. La caja procede de la factura confirmada; el destino se resuelve por tenant, empresa, sucursal y caja. Un destino ausente o ambiguo produce un error funcional. No existe fallback entre cajas.

Las migraciones V96 y V97 agregan caja opcional para agentes históricos, claves foráneas compuestas, configuración por empresa y trabajos persistentes. Los nuevos agentes requieren caja activa de una sucursal autorizada. Los históricos deben asignarse administrativamente. Se admiten varios agentes por caja; mientras no se configure un mecanismo de principal, más de un agente activo se considera ambiguo.

## Revinculación

Generar otro código conserva las credenciales del equipo actual. Completar el pairing consume el código y revoca las credenciales anteriores en la misma transacción. Revocar tiene efecto inmediato en la siguiente petición. Generar un código después de revocar prepara el agente para una nueva vinculación sin recuperar credenciales revocadas. Los bloqueos se toman primero sobre el agente y después sobre códigos o credenciales. Los códigos se conservan únicamente como hashes y no se reutilizan desde su histórico.

## Contrato de trabajos

- POST /api/print-agents/jobs/claim: devuelve 204 sin trabajo o un objeto con id, claimId y pdf (Base64).
- POST /api/print-agents/jobs/{id}/complete: recibe claimId y printed.

El agente se identifica con Bearer; no se aceptan tenant, empresa, caja o agente desde parámetros de consulta. El trabajo queda reservado a una sola instancia. Una descarga no cambia su estado a PRINTED. El cliente reintenta la confirmación sin volver a imprimir durante la misma sesión. No se redistribuyen automáticamente reservas con resultado incierto tras una caída del equipo: revisar el resultado físico antes de una reimpresión explícita.

## PDF y transacciones

Configuración → Impresión de Facturación permite Mostrar PDF al imprimir (inicialmente activado). La lectura es inmediata en cada solicitud. Imprimir y Ver PDF son acciones separadas. El PDF de consulta conserva su plantilla original; el documento para impresora utiliza una plantilla de 80 mm. El fallo de impresión no revierte la factura.

La cola se solicita desde la interfaz después de completar la transacción de confirmación. Una interrupción entre confirmar y solicitar requiere una impresión explícita desde el historial; no se garantiza entrega automática mediante outbox en esta versión.

## Verificación en entorno

Se deben aplicar las migraciones con Flyway y validar integración contra PostgreSQL, dos cajas simultáneas y una impresora física de 80 mm. No se considera validado el resultado físico mediante pruebas unitarias. Los trabajos PROCESSING con resultado incierto requieren revisión antes de reimprimir.
