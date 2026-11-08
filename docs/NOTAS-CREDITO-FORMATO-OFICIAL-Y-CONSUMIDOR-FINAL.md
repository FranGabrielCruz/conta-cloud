# Notas de crédito: formato oficial y cliente opcional

## PDF grande oficial

`LargeDocumentPdfTemplate` utiliza directamente `reports/purchase-order/purchase-order.jrxml` como base Jasper. No se modifica ese recurso. Orden de Compra también obtiene su plantilla a través de esta entrada compartida, con los mismos bytes y composición anteriores.

La nota hereda A4 horizontal (842 × 595 puntos visibles), márgenes laterales de 30 puntos, ancho útil de 782, estilos, encabezado corporativo, imagen de hasta 78 × 58, encabezado de continuidad y paginación. La representación interna puede usar MediaBox vertical con rotación de 90 grados, igual que la exportación oficial de Orden de Compra.

Se adaptan únicamente los datos y las secciones propias: cliente/documento afectado, información de nota, seis columnas, impuestos históricos y aplicación financiera. Las cajas crecen según contenido. Sin logo, la empresa comienza al margen izquierdo. La fuente y jerarquía de TOTAL proceden de la plantilla oficial. El pie reúne usuario y fecha de emisión en una sola línea.

Los ejemplos de referencia usan datos sintéticos. `OrdenCompra-Referencia-PRUEBA.pdf` se genera con la plantilla oficial y `Comparacion-OC-NotaCredito-100.png` muestra ambas primeras páginas a 72 DPI (un punto por píxel), lado a lado. No se trata de una captura del navegador al 100%.

## Identidad y logo

`CompanyReportAssets` centraliza el mismo acceso al archivo y límite de 5 MB utilizados por Orden de Compra. Al emitir una nota nueva se conservan nombre legal, correo y bytes del logo disponibles en su snapshot. Generar PDF no vuelve a consultar el logo actual ni ejecuta operaciones financieras.

Las notas anteriores a esta captura conservan sus datos originales y pueden no tener logo histórico. No se reemplazan retroactivamente con una imagen actual presentada como si hubiese sido registrada al emitir. El nuevo lector es compatible con snapshots antiguos que no incluyen los campos visuales añadidos.

## Nueva nota: cuatro columnas

El bloque de factura utiliza el componente compartido `AppDetailSection` con una clase responsive acotada a ese bloque. Orden de lectura:

| Fila | Columna 1 | Columna 2 | Columna 3 | Columna 4 |
|---|---|---|---|---|
| 1 | Factura | Fecha | Cliente | Moneda |
| 2 | Total factura | Total acreditado | Disponible para acreditar | Condición |

El grid pasa a dos columnas bajo 900 px y a una bajo 600 px. Los nombres extensos hacen wrap y los campos mantienen `min-width: 0`.

«Ver factura» consulta el documento seleccionado mediante `UltimasFacturasService`, con su permiso backend `facturas.ver`. «Cancelar» permanece a la derecha de Guardar, usa la acción global gris con X y cierra directamente solo cuando no hubo cambios. Si hubo cambios, ofrece Descartar cambios / Continuar editando. No guarda una nota parcial. Escape y clic exterior no permiten eludir esta decisión.

## Consumidor Final y facturas sin cliente

Se eliminó el filtro `customer_id IS NOT NULL` del selector y la validación equivalente de emisión. La consulta parte de la factura y no incorpora un INNER JOIN obligatorio a Customer. Continúa exigiendo factura confirmada, misma empresa/tenant, sucursal permitida y cantidades e importes disponibles.

El nombre se obtiene del snapshot de factura, sin consultar obligatoriamente un cliente activo. Si está vacío, se muestra Consumidor Final. El cliente permanece nullable en la nota y no se modifica la factura original ni se crea un cliente ficticio.

Cuando hay cliente, se conserva la liquidación de CxC y el saldo a favor ya implementados. Cuando no hay cliente, la nota queda confirmada contra la factura y consume su disponibilidad; la aplicación a CxC y el crédito de cliente son cero. No se crean cuentas, saldos, reembolsos, movimientos de caja ni inventario. El PDF explica este efecto sin mostrar una identificación vacía.

No cambian la numeración, idempotencia, bloqueos de factura/líneas, auditoría ni protección multitenant. No se requiere una migración adicional: las claves de cliente ya admitían NULL y los datos visuales se incorporan al JSON histórico.

## Impresión

El detalle ofrece Ver PDF e Imprimir mediante `AppActionButton`, usando `ActionType.PDF` y `ActionType.PRINT`. Imprimir utiliza el componente global de 40 × 40, icono Printer de 22 × 22, blanco sobre azul, sin texto y tooltip «Imprimir nota de crédito».

Igual que Orden de Compra, Imprimir abre el PDF grande en el visor normal, desde donde se imprime. No envía un trabajo térmico ni depende de PrintAgent, PrintOnScreen o medidas 50/58/80 mm. Abrir/regenerar el PDF no repite la emisión.

## Verificación y límites

Las pruebas cubren los componentes del formulario, cancelación, estilo semántico del botón, PDF, logo, compatibilidad histórica, seguridad y regresión de Orden de Compra. PostgreSQL utiliza facturas temporales con limpieza: cliente registrado y anónimo, parcial/total, reintento idempotente, dos solicitudes concurrentes para una unidad, aislamiento y logo congelado que sigue disponible después de eliminar su archivo de prueba.

El recorrido E2E y la comparación visual del botón dentro del navegador quedan **PENDIENTES**. La herramienta de control detuvo la comprobación porque no pudo determinar la URL de la ventana con suficiente seguridad. Las pruebas de componentes y la comparación de los PDF renderizados no se presentan como E2E.

Registro: `target/credit-note-official-verification.log`.

Resultado final: **51 PASS / 0 FAIL / 0 omitidas**.

| Grupo | Aprobadas |
|---|---:|
| Servicios de notas de crédito | 9 |
| PDF de notas, seguridad de contenido y comparación oficial | 9 |
| Integración PostgreSQL, incluida concurrencia con y sin cliente | 9 |
| Autorización Spring | 4 |
| Componentes, cuatro columnas, impresión y cancelación | 6 |
| Regresión de PDF de Orden de Compra | 8 |
| Regresión de devoluciones y post-COMMIT | 6 |

Revisión de las diez páginas finales de los cuatro PDF sintéticos y comparación 1:1: **PASS**. E2E y observación del botón en navegador: **PENDIENTE**, por el bloqueo explicado arriba. La plantilla original de Orden de Compra no presenta cambios.
