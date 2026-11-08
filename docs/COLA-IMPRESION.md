# Cola de impresión, recuperación y confirmaciones

Implementación de ContaCloud + contaCloudPrint, 7 de noviembre de 2026.

## Arquitectura y compatibilidad

`billing_print_jobs` sigue siendo la fuente de verdad. `BillingPrintRouter` conserva el destino, snapshot y clave de intención originales; una solicitud repetida no crea otro trabajo ni vuelve a generar el documento. La emisión comercial y su transacción no dependen de que la impresora esté disponible.

`BillingPrintService` reserva mediante bloqueo transaccional y `FOR UPDATE SKIP LOCKED`, valida agente/tenant/empresa/sucursal/caja y administra intentos, reservas y resultados. `PrintJobRetryPolicy` concentra la política. La migración aditiva V111 incorpora los metadatos sin cambiar los estados ni documentos anteriores.

En el agente, `PrintJobPollingService` inicia una sola vez y `PrintJobQueueProcessor` permite una sola salida en curso. La renovación de reserva tiene un hilo daemon independiente; la salida física no retiene el bloqueo de sesión. `InvoicePrintService` sigue utilizando los renderers existentes y lee la configuración vigente para cada nuevo intento. No cambia automáticamente de impresora ni escala documentos de otro tamaño.

Se negocia protocolo 2 con `X-Print-Queue-Version: 2`. El backend conserva `/complete` y el protocolo 1 para clientes anteriores. Debe actualizarse primero el backend y después el agente.

## Estados y política

| Situación | Resultado |
|---|---|
| Creación | PENDING, disponible para el agente autorizado |
| Reserva | PROCESSING, nuevo claimId e incremento de attempt_count |
| Salida autorizada y confirmada | PRINTED (equivalente al estado completado) |
| Fallo transitorio con intentos disponibles | PENDING con available_at futuro |
| Error definitivo o intentos agotados | FAILED con código operativo |
| Reserva vencida sin iniciar salida, protocolo 2 | PENDING o FAILED si agotó los intentos |
| Salida iniciada o protocolo antiguo con reserva vencida | FAILED / OUTPUT_UNCERTAIN; no se vuelve a enviar automáticamente |
| Confirmación tardía de éxito duradero del mismo claim incierto | PRINTED, sin otra salida |

Son transitorios PRINTER_NOT_CONFIGURED, PRINTER_UNAVAILABLE, TEMPORARY_IO_ERROR, TIMEOUT y OUTPUT_ERROR. Son definitivos los errores de snapshot/payload/tipo/layout/papel, generación, cancelación y salida incierta. Windows informa OUTPUT_UNCERTAIN cuando la excepción ocurre después de empezar `PrinterJob.print()`: podría haberse aceptado parte del trabajo.

Propiedades del backend, todas bajo `contacloud.print-queue`:

| Propiedad | Valor inicial |
|---|---:|
| maximum-attempts | 5 |
| lease-seconds | 300 |
| base-delay-seconds | 5 |
| maximum-delay-seconds | 300 |
| jitter | 0.1 |

El retraso es exponencial, acotado y con variación aleatoria. `attempt_count` cuenta reservas, no repeticiones del ACK. Los errores de red utilizan la recuperación de conexión existente; no consumen otro intento de salida por sí solos. El ACK local tiene retraso separado, configurable mediante `-Dcontacloud.print.ackBaseSeconds=2` y `-Dcontacloud.print.ackMaximumSeconds=60`.

La recuperación de reservas se ejecuta al consultar trabajos del agente autorizado. Mientras el agente está desconectado, la fila permanece persistida; al volver a consultar se evalúa el vencimiento. No se reasigna a otro agente. Un agente revocado requiere intervención administrativa y nueva autorización.

## Contrato adicional

Todos los endpoints están bajo `/api/print-agents`, requieren autenticación PRINT_AGENT y validan la propiedad del trabajo y claimId:

* `POST jobs/claim`: respuesta anterior más queueVersion, attempt y leaseSeconds.
* `POST jobs/{id}/start`, cuerpo `{ "claimId": "UUID" }`: valida reserva vigente y caja asignada y registra el inicio de salida.
* `POST jobs/{id}/lease`, mismo cuerpo: extiende la reserva vigente; un claim vencido o reemplazado no vuelve a ser válido.
* `POST jobs/{id}/result`, cuerpo `{ "claimId": "UUID", "printed": false, "errorCode": "PRINTER_UNAVAILABLE" }`. Para éxito, printed=true y errorCode=null.

Los resultados repetidos del mismo claim y mismo contenido son idempotentes. Un claim antiguo no puede modificar una reserva nueva. Los mensajes y códigos siguen el manejo central de errores del backend.

## Historial local y reinicio

`FilePrintJobStateStore` guarda únicamente jobId, claimId, protocolo, estado operativo, código, fecha, versión de archivo y confirmación. No guarda PDF, snapshot, montos ni credenciales. Se ubica en el directorio privado del contexto de servidor, dentro de `print-jobs/<agentId>`. No se mezcla con PrinterConfig ni con DPAPI.

Antes de la salida se persiste STARTED; después se persiste SUCCEEDED o FAILED y solo entonces se envía el resultado al servidor. Las escrituras fuerzan el contenido y reemplazan el archivo atómicamente. Un error de escritura o archivo corrupto detiene la cola antes de continuar: se conserva la información para diagnóstico.

Al reiniciar, SUCCEEDED pendiente repite únicamente el ACK. STARTED sin resultado informa OUTPUT_UNCERTAIN y evita otra salida. FAILED pendiente repite el resultado anterior. Cambiar de vinculación usa otro subdirectorio; no confirma trabajos de una identidad anterior.

Retención predeterminada de confirmados: 30 días, configurable con `-Dcontacloud.print.historyDays=30` (mínimo un día). Los pendientes no se eliminan por antigüedad. El límite es 10 000 registros por agente; al alcanzarlo sin espacio recuperable la cola falla de forma explícita. La limpieza se realiza al escribir nuevos estados. No se elimina automáticamente el historial del backend.

## Límites físicos y manejo operativo

No existe una transacción atómica entre el papel y el servidor. Si se corta la energía después de imprimir y antes de persistir SUCCEEDED, no puede saberse si salió el papel: se conserva como incierto. También puede quedar incierto si se pierde la respuesta de `/start` antes de comenzar la salida. Una reimpresión de ese caso debe ser una decisión explícita y una nueva intención de impresión.

PRINTED significa que el destino aceptó la salida, no que un sensor haya certificado que el papel salió. Un controlador puede aceptar el trabajo y mantenerlo en su spool. Los fallos del controlador que no se expresen al proceso requieren comprobación física.

Microsoft Print to PDF conserva la excepción solicitada: guarda el PDF nativo e intenta abrirlo para todos los documentos. Un fallo del visor no repite un PDF que ya se guardó. Las impresoras físicas mantienen la impresión sin visor.

No se añade una pantalla de cola ni un botón «reintentar todos». Cerrar a bandeja mantiene el agente; salir cierra polling, renovación y conexiones. No cambian los controles ni sus dimensiones.

## Validación reproducible

* Agente: `mvn -B -Dcontacloud.buildDirectory=target/queue-build verify`.
* Backend: ejecutar las pruebas de impresión y `ReliablePrintQueuePostgresTest` con `CONTACLOUD_POSTGRES_TESTS=true` y las variables de la base local existente `conta_cloud`. La prueba aplica V111 mediante Flyway y limpia sus fixtures de UUID aleatorios.
* Matriz conjunta: generar `target/queue-classpath.txt` en ambos repositorios con `dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/queue-classpath.txt`, compilar sus clases de prueba y ejecutar `tools/queue-e2e/verify.ps1` con DB_PASSWORD en el entorno y Java 21. Combina el servicio real sobre PostgreSQL, los renderers del agente, persistencia local y FakePrinterOutput. No cambia el destino de trabajos reales.

La matriz conjunta verifica para cada documento en cada tamaño: PENDING → PROCESSING → fallo transitorio → PENDING → nuevo claim → salida → PRINTED; pérdida simulada de ACK después del commit, reinicio y reconciliación sin otra salida. La salida simulada comprueba el ancho nativo del PDF. Hay además pruebas de contratos HTTP, concurrencia, permisos, revocación, claim antiguo, renovación, corrupción, retención y reinicio incierto.

| Documento | 50 mm | 58 mm | 80 mm |
|---|---|---|---|
| Factura de venta | Simulado | Simulado | Simulado |
| Devolución de venta | Simulado | Simulado | Simulado |
| Recibo de cobro | Simulado | Simulado | Simulado |
| Recuento de caja | Simulado | Simulado | Simulado |
| Vale de caja | Simulado | Simulado | Simulado |
| Cierre de caja | Simulado | Simulado | Simulado |

**Hardware: PENDIENTE — REQUIERE HARDWARE.** Para los 18 casos faltan: salida física y cortes de papel, impresora desconectada/apagada, agotamiento de papel, recuperación del spool, apagado durante salida, controlador bloqueado y comprobación de copias. FakePrinterOutput solo existe en pruebas y no es una impresora seleccionable en producción.

## Resultados de esta ejecución

Agente: **357 PASS / 0 FAIL** (278 pruebas con Surefire y 79 con Failsafe). El contrato HTTP adicional se repitió aisladamente después de corregir el dato de versión de su fixture; los informes XML actuales no contienen fallos. Análisis de dependencias: sin problemas; controles JaCoCo y PMD aprobados.

Matriz conjunta PostgreSQL + agente + salida simulada: **18 PASS / 0 FAIL**. Cada combinación tuvo un fallo temporal controlado (impresora indisponible, TIMEOUT o TEMPORARY_IO_ERROR), un nuevo claim para el mismo trabajo, éxito, ACK perdido después del commit, reinicio y un solo resultado de salida aceptado. Son pruebas a nivel de servicios; el transporte HTTP se comprueba por separado. No equivalen a impresión física.

El backend incluye la regresión de 300 reservas concurrentes, aislamiento de agentes, vinculación, permisos, seis adaptadores, política de pantalla, conservación de snapshot e idempotencia de intenciones. La última comprobación también aprobó la recuperación después de retirar todas las cajas y la reconciliación de confirmaciones antiguas. Total único del backend: **189 PASS / 0 FAIL**, sin sumar repeticiones de una misma prueba.

| Categoría principal, sin duplicar casos | Agente | Backend |
|---|---:|---:|
| Unit / contratos / componentes | 273 PASS / 0 FAIL | 104 PASS / 0 FAIL |
| Integration | 73 PASS / 0 FAIL | 50 PASS / 0 FAIL |
| Security | 11 PASS / 0 FAIL | 35 PASS / 0 FAIL |
| E2E conjunto con salida simulada | 18 PASS / 0 FAIL en total | Compartido |

La categoría de seguridad del backend agrupa tokens, vinculación, administración, asignación y enrutamiento. Las pruebas de PostgreSQL y Flyway se cuentan como integración, aunque también comprueban aislamiento y autorización. Las 18 pruebas conjuntas son adicionales a las 546 pruebas de ambos repositorios. No se ejecutó la suite completa de los demás módulos financieros del backend.

Evidencia local en `target/queue-build/*-reports`, `target/queue-complete.log`, `target/queue-http-final.log` y `target/queue-e2e.log`; backend en `target/queue-backend-verified.log`, `target/queue-regression.log` y `target/queue-latest.log`. Las ejecuciones repetidas no se suman como casos adicionales.

| Control de seguridad | Resultado y evidencia |
|---|---|
| Tenant / empresa | PASS: consultas con identidad autenticada; UUID de otra empresa/tenant rechazado |
| Sucursal | PASS: reserva, inicio y resultado limitados a la sucursal actual del agente; integridad de asignaciones en PostgreSQL |
| Caja | PASS: se exige caja activa y asignada antes de comenzar; puede confirmarse una salida ya iniciada después de retirar la caja |
| Agente / IDOR | PASS: jobId y claimId de otro agente o reserva no pueden iniciar ni confirmar la salida |
| Revocación | PASS: no se inicia ni se confirma con agente revocado |
| Secretos | PASS: historial solo operativo y pruebas de logging; sin credenciales ni snapshot en logs de cola |

| Hardware | Resultado |
|---|---|
| Impresora térmica real | PENDIENTE — REQUIERE HARDWARE |
| Corte automático | PENDIENTE — REQUIERE HARDWARE |
| Gaveta | PENDIENTE — REQUIERE HARDWARE |
| ESC/POS RAW | PENDIENTE — REQUIERE HARDWARE |
| Papel agotado | PENDIENTE — REQUIERE HARDWARE |
| Tapa / atasco | PENDIENTE — REQUIERE HARDWARE |

ESC/POS RAW, corte y gaveta no se incorporan como funciones nuevas de esta cola. Los controles existentes permanecen sujetos a las capacidades ya implementadas del destino.

Para activar la versión compilada, reiniciar primero ContaCloud y luego contaCloudPrint. Una vinculación vigente puede conservarse; la cola no cambia la caducidad ni el refresh de sus credenciales.
