# Snapshot lógico en trabajos de impresión — V1

El claim de Print Agents conserva `id`, `claimId`, `pdf` y `documentType`, y añade los campos opcionales `snapshotVersion`, `snapshot` y `traceId`. `snapshot` es una cadena JSON con el documento lógico histórico del tipo indicado, versión 1; fechas locales ISO, importes decimales y los mismos campos de los records POS existentes. No contiene nombres de clases Java ni credenciales.

Los seis adaptadores suministran su documento mediante un supplier interno. El router captura el JSON al crear un trabajo físico nuevo, después de verificar origen, tenant, empresa, sucursal, agente y caja. La misma transacción guarda PDF 80, snapshot y versión. El modo pantalla y los reintentos idempotentes no capturan snapshots ni generan trabajos adicionales. No se ejecutan comandos financieros en esta operación.

V110 añade columnas opcionales al trabajo existente; limita el JSON a 2 MB UTF-8 y exige versión 1 cuando hay snapshot. Las migraciones anteriores y los trabajos ya creados permanecen intactos. El claim entrega los datos almacenados bajo los filtros originales de trabajo, tenant, empresa, agente y claim. El DTO omite PDF/snapshot en su representación de diagnóstico.

El agente actualizado renderiza los seis documentos en 50 mm desde el snapshot y utiliza el PDF recibido sin cambios en 80 mm. Un agente anterior ignora los campos nuevos. Los trabajos antiguos sin snapshot siguen disponibles en 80 mm; para imprimirlos en 50 se solicita una nueva copia con un intento nuevo. Nunca se reduce el PDF de 80 mm.

No se cambian PrintOnScreen, permisos, routing, idempotencia, pairing, refresh, heartbeat ni acuses. Microsoft Print to PDF conserva la excepción aprobada de guardar y solicitar abrir el archivo; la salida física permanece silenciosa.

Validación: selección de impresión/documentos de backend con 195 pruebas aprobadas, cero fallos y 12 pruebas PostgreSQL omitidas por no estar activadas. Repetición final de 37 pruebas del contrato/router/reserva aprobadas. La migración se aplicó con Flyway a una base H2 aislada para verificar compatibilidad de trabajos anteriores y restricciones de versión. No se aplicó a la base real durante estas pruebas; se aplicará al reiniciar el backend por su procedimiento normal de Flyway.

El informe completo de layouts, matriz, dimensiones y límites está en contaCloudPrint: `docs/DOCUMENTOS-50MM.md`.
