# Vale de caja POS de 80 mm

`CASH_VOUCHER` utiliza `CashVoucherPrintDocument` y `CashVoucher80mmPdfRenderer` sobre el motor medido común. El PDF tiene ancho real de 80 mm, márgenes de 3,5 mm y altura dependiente del contenido. Muestra empresa, número, fecha histórica, caja, cajero disponible, concepto, monto, total con moneda dinámica y dirección explícita: ENTRADA DE CAJA o SALIDA DE CAJA. Los campos opcionales no reservan líneas. Conceptos, observaciones y referencias se ajustan completos; los valores son datos, nunca instrucciones, enlaces ni comandos. No se añaden marcas del producto.

El modelo admite `CASH_IN` y `CASH_OUT`, beneficiario/entregado a/recibido por y firma opcional. No deduce dirección por signo ni calcula importes o saldos. El negocio actual de ContaCloud confirma exclusivamente vales de **salida de efectivo** (`EXPENSE`, medio `CASH`); su adaptador captura explícitamente `CASH_OUT` y «Efectivo». No se agregan entradas financieras ni se inventan terceros, medios o referencias. La representación de entrada está cubierta por pruebas del modelo y del renderer, pero no se añade una nueva operación de entrada a la interfaz.

## Política de salida

La inscripción existente en `BillingPrintDocumentsConfiguration` apunta ahora al documento específico. `BillingPrintApplicationService` y `BillingPrintRouter` mantienen la única decisión sobre `PrintOnScreen`:

- Activado: PDF POS por pantalla; no se crea un trabajo.
- Desactivado: trabajo `CASH_VOUCHER` para el agente asignado a la caja de origen; el backend no abre PDF ni utiliza uno de respaldo si falla el agente.

Se preservan el permiso `vales_caja.imprimir`, la consulta `vales_caja.ver`, el módulo CAJA_BANCOS y el ámbito de tenant/empresa/sucursal. La interfaz no proporciona un AgentId. Se conserva la clave de intento de los botones comunes frente a doble clic, reintento y timeout; otra apertura del detalle permite solicitar una copia nueva. El permiso normal de DEMO ya existe mediante las migraciones de vales, sin excepciones adicionales.

«Ver PDF» es una acción manual independiente que mantiene `ValeCajaPdfService` y la plantilla normal, tanto en Facturación como en Operaciones de caja. No pasa por la cola ni registra otro movimiento.

**Excepción confirmada por el usuario:** al seleccionar Microsoft Print to PDF en el agente, se guarda el documento y se solicita abrirlo en el visor predeterminado, igual que las facturas. Con una impresora física se conserva el envío mediante el controlador Windows sin visor. Un fallo del visor no borra el archivo ni provoca otra impresión. Esta excepción prevalece sobre el requisito genérico de no abrir visor en la especificación.

## Historial y transacciones

La migración nueva `V107__snapshot_pos_vales_caja.sql` añade `cash_voucher.pos_print_snapshot`. Al confirmar, después de guardar el vale y vincular su movimiento, se conserva una sola vez el documento lógico con identidad de empresa, país, dirección, teléfono, cajero, usuario, beneficiario, moneda, importes y fecha convertida a la zona horaria empresarial. Se persiste con la misma transacción, antes de completar la idempotencia. No se genera PDF ni se contacta con el agente durante esa confirmación.

Imprimir se ejecuta con la transacción independiente ya existente, después de la confirmación. La reimpresión solo consulta información y procesa la salida; no invoca confirmar/anular, no consume otra secuencia y no registra movimientos. Fallos de generación o entrega no revierten el vale. Los vales anulados se identifican claramente con ANULADO y el motivo, conservando el documento original y sin reescribir el snapshot.

Los vales anteriores ya contienen datos históricos inmutables. Se imprimen a partir de esos campos guardados, usando su fecha con el desfase almacenado, sin consultar catálogos actuales. Como no conservan país ni cajero de apertura, se utiliza identificación genérica (ID) y se omite ese cajero. No se reconstruye una zona horaria histórica desconocida ni se rellenan datos retrospectivamente.

## Comprobación y uso

Las pruebas verifican entrada/salida, campos opcionales, textos largos, monedas, cantidades extremas, ancho, márgenes, solapamientos, altura, caracteres españoles y contenido literal sin acciones PDF. La recepción autenticada simulada utiliza el nuevo vale y conserva los bytes recibidos, abre el archivo guardado para Microsoft Print to PDF y confirma el trabajo. Las pruebas del backend cubren ambos destinos, origen y agente correctos, autorización, aislamiento, persistencia del snapshot, migración, reimpresión, anulación, errores e idempotencia del movimiento.

Para probar localmente, reiniciar ContaCloud para aplicar V107 y recompilar/reiniciar el agente con su configuración habitual. La validación automatizada utiliza bases de prueba y servidores HTTP simulados; no modifica la base operativa. La aceptación contra PostgreSQL en ejecución y una impresora física requiere la comprobación en el entorno real. No se confirma salida de papel solo porque el controlador acepte el trabajo.

Validación del 02/11/2026: agente con 155 pruebas unitarias y 44 de integración, análisis estático y de dependencias correctos. Backend con 801 pruebas: 789 aprobadas y 12 opcionales de PostgreSQL omitidas. La comprobación antigua de recibos se actualizó al botón común que ya existía y sus tres pruebas se verificaron de nuevo por separado. No quedan fallos en los reportes finales. Los 14 archivos del núcleo POS coinciden entre ambos proyectos. Se revisaron visualmente los ejemplos de entrada y salida, además de las comprobaciones automáticas de geometría y superposición.
