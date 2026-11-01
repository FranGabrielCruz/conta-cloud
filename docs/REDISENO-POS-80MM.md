# Rediseño POS de 80 mm

Implementación del 01/11/2026 en ContaCloud y contaCloudPrint.

## Presentación

- Ancho físico de 80 mm, márgenes de 3,5 mm, fuente incrustada compatible con español.
- Empresa centrada a 12 pt; tipo documental y TOTAL destacados; cuerpo de 8,5 pt.
- Metadatos con etiquetas y valores alineados a la izquierda en columnas estables. NCF, identificación del cliente, dirección, teléfono y otros datos opcionales solo ocupan espacio cuando existen.
- Tabla compacta CANT./DESCRIPCIÓN/IMPORTE; descripción completa con wrapping; importes alineados a la derecha. Sin espacios vacíos entre productos.
- Divisores vectoriales finos, resumen con descuento/recargo/impuestos, moneda dinámica, total en una sola fila, medios de pago reales, recibido/cambio y pie configurable centrado.
- Altura medida por contenido, margen final breve y paginación acotada para documentos extensos. No se recorta contenido para simular una página fija.
- Opciones `hideZeroDiscount`/`hideZeroSurcharge` disponibles en el renderer; por defecto se muestran los ceros que el documento proporciona. Un valor ausente no se inventa.

## Integración

La entrada inmutable `PosInvoiceDocument` conserva todos los valores recibidos. El renderer no calcula subtotal, impuestos, descuentos, recargos, total, recibido o cambio. Tampoco emite números de factura o NCF ni interpreta HTML, JavaScript, rutas o comandos.

En ContaCloud, `FacturaVentaPdfService.generarParaImpresion` deriva al nuevo `FacturaPosDocumentoService`. Este utiliza líneas y pagos guardados, incluidos los nombres históricos de métodos de pago e impuestos. La agrupación de impuestos suma únicamente importes ya almacenados por concepto; no aplica tasas ni reemplaza el total confirmado de la factura. El PDF manual normal mantiene su ruta Jasper existente. Las preferencias de visualización, el enrutamiento por caja y el protocolo de reserva/confirmación de trabajos se conservan.

Cada aplicación incorpora el mismo núcleo de cinco archivos en su propio paquete para distribuirse sin depender de la instalación de la otra. Comprobar su equivalencia mediante `scripts/check-pos-renderer-parity.ps1`. Al cambiar el núcleo, actualizar ambas copias y repetir las pruebas. No hay dependencias Maven locales entre los dos ejecutables.

El destino Microsoft Print to PDF continúa guardando y abriendo directamente el archivo según la opción que el usuario solicitó para trabajar sin impresora física. No se añade apertura automática a la interfaz web de Facturación ni a las impresoras físicas.

## Datos históricos y fiscales

La migración **V98__conservar_emisor_factura_pos.sql** incorpora el emisor y la caja históricos en la factura. El snapshot se captura al confirmar una nueva venta, dentro de la transacción existente, y no puede sustituirse. Las reimpresiones posteriores usan ese snapshot sin consultar la empresa actual. Las consultas se limitan por tenant, empresa y sucursal autorizada.

Las facturas anteriores a V98 no guardaron esa identidad: no es posible recuperar retrospectivamente un RNC o dirección anteriores. Para conservar la compatibilidad de impresión, esas facturas usan los datos actuales del emisor y añaden una advertencia visible en el pie. No se hace un backfill que afirme conocer datos históricos inexistentes. Esta compatibilidad es una limitación explícita frente al requisito ideal de snapshot histórico universal.

El esquema actual `sales_invoice` tampoco conserva NCF ni tipo fiscal. La integración utiliza el título neutral **FACTURA** y no inventa un NCF ni clasifica la operación como consumo o crédito fiscal. El modelo y renderer sí admiten el título y NCF reales cuando la fuente los proporcione. Los tests cubren ambas variantes. Implementar emisión fiscal no forma parte de este rediseño.

Los Print Jobs ya existentes contienen un PDF inmutable: deben reenviarse como un trabajo nuevo para obtener el diseño nuevo. No se alteran sus bytes ni sus estados retroactivamente.

## Verificación

El agente se verifica con `mvn verify dependency:analyze`, usando `-Dcontacloud.buildDirectory=target/pos-redesign-build` para evitar interferencias con IntelliJ. La regresión incluye seguridad, reconexión, exportación PDF, trabajos remotos y JavaFX.

En ContaCloud se usa `mvn test -Dvaadin.skip=true -Dcontacloud.buildDirectory=target/pos-redesign-build`. Las pruebas de persistencia utilizan H2 en memoria, no PostgreSQL de desarrollo. La aplicación debe ejecutar V98 mediante su proceso Flyway habitual al reiniciar; no se ejecutó SQL sobre datos reales durante esta tarea. La compatibilidad SQL exacta de V98 con PostgreSQL queda a esa validación; H2 no admite la sintaxis de múltiples ADD COLUMN usada por PostgreSQL.

Los fixtures están en `target/pos-redesign-review`: 1, 5 y 20 productos, descripción/cliente/empresa largos, importe grande y multipago. Se revisan texto completo, geometría, ausencia de solapamientos horizontales, ancho de 80 mm, importes de 9.99 a 9,999,999.99 y total sin partirse. La muestra técnica está en `output/pdf`; se renderizó también con Poppler para revisar su resultado visual.

La aceptación con la caja real y una impresora térmica sigue requiriendo reiniciar ambos procesos y emitir un trabajo nuevo. Las pruebas automáticas no afirman que un dispositivo físico haya impreso.

### Resultado final

- contaCloudPrint: **147 pruebas**, cero fallos, errores u omisiones; incluye 113 unitarias/seguridad y 34 de integración. `verify`, PMD y análisis de dependencias aprobados.
- ContaCloud: **585 pruebas**, cero fallos, errores u omisiones, incluida persistencia del snapshot, pagos históricos, aislamiento por tenant/empresa/sucursal y las regresiones existentes.
- Ocho fixtures PDF de ambos proyectos coinciden byte por byte; los cinco archivos del núcleo coinciden tras ajustar exclusivamente el paquete Java.
- Revisión visual de los ocho escenarios y de la muestra técnica final con Poppler: sin recortes, superposiciones ni espacio blanco final excesivo.

Resultados reproducibles en `target/pos-redesign-verify.log` y `target/pos-backend-final-test.log` del agente; informes XML en las carpetas de compilación aisladas de cada proyecto. Recargar Maven en ambos IDE y reiniciar ambos procesos antes de probar un Print Job nuevo.
