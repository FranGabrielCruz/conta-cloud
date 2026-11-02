# Política obligatoria de impresión de Facturación

## Matriz única

| Tipo | Documento | printOnScreen=true | printOnScreen=false |
| --- | --- | --- | --- |
| SALES_INVOICE | Factura | PDF normal | Trabajo con factura 80 mm |
| SALES_RETURN | Devolución | PDF de consulta | Trabajo con devolución 80 mm |
| PAYMENT_RECEIPT | Recibo | PDF del recibo | Trabajo con recibo 80 mm |
| CASH_COUNT | Recuento | PDF del recuento | Trabajo con recuento 80 mm |
| CASH_VOUCHER | Vale | PDF normal existente | Trabajo con vale nativo 80 mm |
| CASH_CLOSING | Cierre | PDF normal existente | Trabajo con cierre nativo 80 mm |

SCREEN y PRINT_AGENT son excluyentes. Generar un PDF como payload de impresión nunca implica abrirlo en pantalla. Ver PDF explícito permanece fuera de la cola y no consulta ni modifica el destino de Imprimir.

## Infraestructura obligatoria

Todas las acciones Imprimir/Reimprimir del POS y de Operaciones de caja usan BillingPrintUi. Esta clase contiene el único switch de presentación entre SCREEN y PRINT_AGENT; los handlers de documentos solo aportan tipo, ID e intento. Los callbacks de impresión de los diálogos reciben documento e intento por separado de Ver PDF; ya no admiten el constructor ambiguo con un Consumer de PDF como impresión.

BillingPrintApplicationService es la entrada de aplicación para los seis tipos. En una transacción REQUIRES_NEW, posterior al commit de negocio:

1. BillingPrintDocumentRegistry comprueba que el tipo esté registrado y valida su permiso antes de cargar información.
2. El adaptador obtiene el documento dentro del tenant/empresa actuales y su origen persistido; se comprueba la sucursal autorizada y se rechazan orígenes históricos sin caja.
3. BillingPrintRouter resuelve exclusivamente el agente de esa caja, bloquea la decisión y consulta printOnScreen.
4. SCREEN genera solamente el documento de pantalla y no crea Print Job.
5. PRINT_AGENT genera solamente el payload del renderer registrado, guarda el trabajo tipado y no devuelve un archivo de pantalla.

Un fallo no confirma, anula ni vuelve a ejecutar ventas, devoluciones, cobros, inventario, recuentos, vales o cierres. Un agente desconectado mantiene las reglas de cola. Un agente revocado en modo impresora, un origen inválido o un destino ambiguo producen un error seguro, sin sustituir el destino ni elegir otra caja.

## Registro de documentos nuevos

Cada nuevo TipoDocumentoImpresion debe tener una Registration en BillingPrintDocumentsConfiguration: permiso, carga autorizada, origen persistido y proveedores de PDF de pantalla y de impresión. El constructor del registro compara los adaptadores con todos los valores del enum y rechaza duplicados o tipos sin registrar. BillingPrintConfigurationTest carga la configuración real y garantiza esta cobertura en CI; agregar solo el enum hace fallar la aplicación y las pruebas.

No insertar Print Jobs desde una pantalla ni conectar Imprimir directamente a un generador/visor PDF. Usar BillingPrintUi.boton o los callbacks con un UUID estable por intento. BillingPrintActionButton bloquea clics mientras espera la respuesta. Conserva la clave si la solicitud falla o no se confirma; después de una respuesta correcta genera una clave nueva para la siguiente acción. Así, una nueva impresión consulta la configuración vigente y un reintento mantiene la decisión ya tomada.

## Exclusividad e idempotencia persistente

V105 amplía los tipos admitidos de trabajos a vale y cierre. V106 crea billing_print_requests, un registro técnico de intentos separado de los Print Jobs. Cada intento es único por tenant y empresa, pertenece a un documento/tipo y se bloquea antes de decidir su salida.

La primera resolución conserva SCREEN o PRINT_AGENT y su agente. Repetir el intento mantiene esa salida aunque cambie después la preferencia del agente: no convierte un PDF ya solicitado en una impresión física, ni un trabajo en una apertura de PDF. SCREEN no crea ningún Print Job. Los intentos fallidos antes de resolver/renderizar se revierten con la transacción de impresión, sin afectar el negocio confirmado. Las FKs compuestas impiden mezclar tenant, empresa, agente y trabajo.

Las migraciones anteriores permanecen intactas. No se crean preferencias por documento ni se invierten configuraciones. Desplegar el backend y el cliente reconstruidos, y reiniciar las instancias anteriores para que carguen los seis tipos.

## Representación y cliente

Se conservan los reportes de factura, devolución, recibo y recuento ya existentes. Vale y cierre usan un renderer nativo de 80 mm basado en PosTicket80mmPdfRenderer, con valores guardados y emisor histórico; no se amplía ni reduce artificialmente un A4. No se recalculan saldos o diferencias para imprimir.

contaCloudPrint acepta los seis tipos semánticos y conserva el PDF recibido sin reinterpretar el destino. printPdf utiliza printSilently. Con el perfil Microsoft Print to PDF se guarda el archivo sin abrir un visor; con un perfil físico se conserva el motor silencioso de Windows. La configuración de impresora y SecretStore no se modifican por esta política.

## Comprobación

La matriz de UI cubre los doce escenarios (seis tipos × dos salidas) y errores sin fallback. Las pruebas de registro cubren permisos, origen histórico, duplicados y la configuración real de los seis adaptadores. Las pruebas de PostgreSQL cubren el registro completo, múltiples cajas, trabajos tipados, reserva, solicitudes concurrentes y conservación del destino después de cambiar la preferencia. Los fixtures se eliminan al terminar y no ejecutan operaciones financieras.

La integración HTTP del cliente prueba cada tipo con Bearer de pruebas, payload PDF, destino silencioso y confirmación después de guardar. Estas pruebas automatizadas no equivalen a operar la sesión de escritorio del usuario; cualquier comprobación del agente abierto se documenta por separado.

Se comprobó localmente el motor del cliente con el perfil instalado Microsoft Print to PDF y seis documentos sintéticos de 80 mm: seis archivos guardados conservando exactamente el payload y cero aperturas del visor. No se crearon movimientos financieros. La sesión gráfica del agente conectado no se operó porque el helper de escritorio no respondió.
