# Facturación: doble Enter para abrir Cobrar

## Causa identificada

El listener anterior ya usaba captura en document, pero clasificaba cualquier `BUTTON` del composedPath como editable y descartaba el atajo. Así, un botón de cantidad o de acción que conservaba el foco tras un clic quedaba excluido, aunque no hubiera edición activa. También había dos estados de detección con 450 ms (JavaScript y DoubleEnterDetector Java), sin reinicio central por clic, foco u otras teclas. El hallazgo está comprobado en código; no se obtuvo una traza de activeElement en la aplicación real porque no fue posible conectar el navegador.

## Solución y ciclo de vida

`src/main/frontend/billing-keyboard-shortcuts.js` es la única fuente de interpretación del doble Enter. `FacturacionPosView` lo carga mediante JsModule. El listener reconoce el par en el cliente y emite una única solicitud `cc-pos-open-payment`, cuyo receptor Java invoca la misma `dialogoCobro()` utilizada por el botón y F9. Java ya no vuelve a detectar el par. El intervalo procede de `DoubleEnterDetector.DEFAULT_WINDOW_MILLIS`: **450 ms**.

El teclado se escucha en captura en window para preceder a los handlers de document/controles hijos; solo actúa con el host de Facturación conectado y dentro de su contexto. Body sin control enfocado se admite como contexto de reposo. Otro control fuera de la vista mantiene su Enter normal. Instalar otro host retira el anterior; detach y observación de desconexión eliminan los listeners y temporizadores. No se añadió ninguna llamada a focus como solución.

## Política de eventos

- `keydown` reconoce Enter y NumpadEnter; `keyup` exige liberación real entre pulsaciones. Repeat y una segunda bajada sin liberación no cuentan como otra pulsación.
- Teclas modificadas/IME, otra tecla, cambio de foco, pointerdown, edición, change, rueda y pérdida de foco de ventana descartan el par incompleto.
- INPUT/TEXTAREA/SELECT, controles numéricos, contenteditable y controles ARIA de edición conservan su Enter. El Enter que termina una edición no forma parte del siguiente par.
- Autocomplete/select/menú abiertos y modales descartan el par. Un combo ordinario mantiene Enter para seleccionar. El buscador de producto vacío y cerrado conserva el contexto de reposo ya previsto por el POS; una búsqueda escrita o una lista abierta nunca cuenta como primer Enter del cobro.
- En una zona no editable se consume Enter al reconocerlo como parte del atajo. En un botón enfocado se difiere su activación individual hasta vencer la ventana de 450 ms. Si llega el segundo Enter, se cancela esa activación y se solicita Cobrar; no se ejecutan simultáneamente ambas acciones. Un solo Enter continúa activando el botón una vez si sigue enfocado y no se abrió otro contexto. También se contemplan botones ARIA y hosts Vaadin con shadow DOM.
- Tras reconocer el par hay un bloqueo pendiente hasta la respuesta del servidor. Se descartan pulsaciones adicionales sin otra solicitud ni activación nativa detrás del cobro. Reset del flujo Java libera el bloqueo también cuando una validación o error impide abrir.

`dialogoCobro()` conserva las validaciones y llamadas existentes; añade guardas explícitas de preparación en curso, permiso `facturas.cobrar` y caja abierta. Las guardas se aplican a botón, F9 y doble Enter por igual. La autorización y confirmación financiera continúan en backend; este atajo no confirma facturas ni cobros.

## Pruebas ejecutadas

- **38 pruebas JavaScript, 0 fallos, 0 omitidas.** Ejecutan el módulo real con EventTarget y un entorno DOM simulado, sin navegador. Cubren controles no editables, body, Grid/fila/celda, iconos, texto, editores, autocomplete, overlays, Enter numérico, tiempo y límite inclusivo de 450 ms, repeat, triple Enter, botones, cambios de contexto, cancelación/reapertura, reemplazo y eliminación del listener.
- **27 pruebas Java/JUnit, 0 fallos, 0 errores, 0 omitidas.** Incluyen seis pruebas de integración de la vista real con servicios simulados: factura válida, apertura única y reapertura, factura vacía, caja cerrada, falta de permiso, recuperación del error y conexión común de botón/F9/evento. El resto cubre detector anterior/constante, formato y regresión de salida de impresión. Una prueba JUnit lanza la matriz JavaScript como parte de Maven: requiere Node disponible en PATH o NODE_BINARY. El total JUnit incluye ese lanzador; no se considera un segundo conjunto independiente de 38 casos.
- Evidencia: `target/billing-keyboard-js-tests.log` y `target/billing-double-enter-final-tests.log`.
- Comandos: `node --test src/test/frontend/billing-keyboard-shortcuts.test.mjs`; Maven con BillingKeyboardJavascriptTest, FacturacionDobleEnterIntegrationTest, DoubleEnterDetectorTest, FacturacionPosViewFormatTest, FacturacionSalidaTest y PosChargeButtonTest.

| Contexto | Política/eventos automatizados | Navegador real |
| --- | --- | --- |
| Grid, fila y celda sin edición | PASS | PENDIENTE |
| Total, cliente mostrado, producto, texto, iconos y área vacía | PASS para los elementos no editables simulados | PENDIENTE |
| Botón enfocado y controles de paginación | PASS para política de botón | PENDIENTE |
| Autocomplete: seleccionar, cerrar y nuevo doble Enter | PASS | PENDIENTE |
| Edición/textarea/selector: respetar Enter | PASS | PENDIENTE |
| Cerrar Cobrar y volver a abrir | PASS en lógica de eventos y componente Java | PENDIENTE |
| Remontar/desmontar y reemplazar listener | PASS en lógica de eventos | PENDIENTE para navegación real |
| Intercalar mouse, foco o edición entre los dos Enter | PASS | PENDIENTE |
| Tecla mantenida, numpad, triple Enter y latencia pendiente | PASS | PENDIENTE |

## E2E y verificación manual/visual: PENDIENTES

Se intentó abrir `http://localhost:8080/facturacion` en el navegador integrado. La herramienta agotó el tiempo esperando que se conectara el webview; la posterior búsqueda de esa pestaña indicó que no existía. No se realizaron E2E de navegador ni una prueba manual POS, y no se declara PASS visual.

Quedan pendientes en la instancia real: agregar producto, hacer clic en fila/celda, doble Enter, cancelar y repetir sobre otra fila; matriz de foco con cliente/producto/paginación; edición de cantidades; selección por autocomplete; Enter numérico; entrada/salida de la ruta y revisión de errores JavaScript. No se ejecutaron confirmaciones financieras para simular una validación visual. No se requieren migraciones ni cambios en impresión o cálculos monetarios.
