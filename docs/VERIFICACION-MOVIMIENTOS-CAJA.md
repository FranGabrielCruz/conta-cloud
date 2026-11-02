# Verificación de integración de Movimientos en Caja

## Causa encontrada

La implementación anterior modificó `MovimientosCajaDialog`, invocado desde Facturación POS. `OperacionesCajaView` declaraba exclusivamente Estado actual, Historial de cierres, Recuentos y Vales de caja. No importaba ni montaba el resumen de movimientos. La ausencia no se debía a la migración 109 ni a una ruta inexistente del módulo: faltaba integrar el componente en esa navegación.

## Corrección

- `OperacionesCajaView` declara las cinco pestañas en el orden solicitado y monta el contenido de Movimientos dentro del módulo.
- `MovimientosCajaPanel` contiene la presentación compartida de sesión, resumen, formas de pago, detalle, filtros, paginación y actualización. El POS conserva su diálogo y sus comandos explícitos anteriores; la nueva pestaña solo admite consulta.
- `MovimientoCajaService.consultarCaja` valida los dos permisos y el módulo, resuelve la caja por tenant/empresa, valida sucursal y consulta su sesión OPEN dentro de una transacción nueva de solo lectura REPEATABLE_READ. La administración puede consultar a otro cajero autorizado, como el resto de Operaciones de caja; el acceso POS sigue restringido a su cajero. Ambos accesos reutilizan el mismo cálculo y DTO.
- Una caja sin sesión abierta devuelve un estado vacío; no reutiliza turnos cerrados. Cambiar de caja reemplaza el contenido anterior. Actualizar vuelve a resolver la sesión actual en backend.

## Navegación y contrato

Estado actual | **Movimientos** | Historial de cierres | Recuentos | Vales de caja

- Ruta: `/operaciones-caja?tab=movements`.
- Registro: `@Route(value="operaciones-caja",layout=MainLayout.class)`; el query parameter se procesa en `beforeEnter`.
- Identificador: `movements`; ID del tab: `cash-tab-movements`.
- Las selecciones usan navegación Vaadin con query parameters. Una entrada directa reconstruye la pestaña seleccionada; atrás/adelante utiliza el historial normal del router. La comprobación real en navegador de refresh/atrás/adelante está pendiente.
- Autorización del módulo: CAJA_BANCOS + `operaciones_caja.ver`. Movimientos exige además `movimientos_caja.ver`; se oculta y se rechaza su ruta sin ese permiso. La consulta backend mantiene ambos requisitos.
- Backend real: RPC de Vaadin hacia `MovimientoCajaService.consultarCaja(cajaId, buscar, tipo, origen, pagina, tamano)`. No se creó un endpoint REST paralelo ni una estructura de respuesta nueva.
- Los cuatro roles ADMINISTRADOR existentes contienen ambos permisos. No existe una empresa con código DEMO en esta base; no se crearon empresas ni se modificaron roles para la comprobación.

## Evidencia automatizada

`OperacionesCajaNavegacionTest` construye el componente real de Caja y comprueba exactamente las cinco pestañas autorizadas, en orden. Recorre `beforeEnter` con la ruta de Movimientos, comprueba la selección y el montaje del panel real, la llamada a `consultarCaja`, las secciones de resumen/medios/detalle, el rechazo de acceso sin permiso y el cambio de caja. Verifica además las consultas de las cuatro pestañas anteriores. Es una prueba de integración de componentes Vaadin con servicios simulados, no un E2E de navegador.

| Comprobación | Automatizada | Aplicación real en navegador |
| --- | --- | --- |
| Existencia, orden y visibilidad autorizada de pestañas | PASS | PENDIENTE |
| Ruta directa, pestaña activa y contenido correcto | PASS | PENDIENTE |
| Sesión, caja, cajero, moneda y fondo inicial | PASS | PENDIENTE |
| Entradas, salidas y efectivo esperado | PASS | PENDIENTE |
| Formas de pago dinámicas y total operaciones | PASS | PENDIENTE |
| Detalle, filtros y totales independientes | PASS | PENDIENTE |
| Paginación y actualización | PASS | PENDIENTE |
| Ausencia de comandos financieros en la pestaña | PASS | PENDIENTE |
| Tenant, empresa, sucursal, caja y sesión | PASS | PENDIENTE |
| Concurrencia y lectura sin bloqueos de escritura | PASS | PENDIENTE |
| Otras cuatro pestañas conectadas a sus consultas | PASS | PENDIENTE |
| Refresh y atrás/adelante en navegador | No ejecutado en navegador | PENDIENTE |
| Resoluciones 1366×768, 1600×900 y 1920×1080 | No comprobadas visualmente | PENDIENTE |

Se ejecutó el conjunto anterior de 76 pruebas y se agregaron 14 comprobaciones: total **90 PASS, 0 FAIL, 0 ERROR, 0 SKIP**. Clasificación sin doble conteo: 79 unitarias/componentes/regresión, 5 de seguridad y 6 de PostgreSQL. Las pruebas de seguridad están implementadas dentro de las clases de servicio y navegación. E2E de navegador: **0 ejecutados, PENDIENTE**, sin convertir esta ausencia en PASS.

Evidencia de ejecución: `target/cash-movements-tab-final-tests.log` y reportes Surefire. Las seis pruebas PostgreSQL usan esquema real validado, fixtures aleatorios retirados al finalizar y comprobaciones de aislamiento/concurrencia; no ejecutan operaciones financieras de usuarios desde la UI.

## Migración y semántica conservadas

V109 continúa sin cambios: índice `idx_receipt_payment_session_lookup`. Flyway valida las 109 migraciones y Hibernate valida el esquema al iniciar las pruebas PostgreSQL. La aplicación original desde V108 a V109 permanece documentada en `target/cash-summary-migration.log`. No se añadió otra migración para esta corrección.

Los recibos exclusivamente bancarios sin una línea que conserve `cash_register_session_id` siguen excluidos. Este vínculo se guarda en las líneas de efectivo; la limitación existe también en capturas actuales exclusivamente bancarias, no solo en documentos históricos. Los recibos mixtos se vinculan por su línea en efectivo y muestran todos sus medios. No se asignan recibos por fecha, usuario o caja de impresión. Se conservan los cálculos financieros descritos en `MOVIMIENTOS-CAJA-SESION.md`.

## VERIFICACIÓN VISUAL PENDIENTE

Se obtuvo el inventario de aplicaciones y se intentó acceder a Chrome. La herramienta detuvo Computer Use porque no pudo determinar la URL actual de Windows con suficiente confianza para aplicar sus controles de acceso. No se continuó con acciones del navegador ni se sustituyó la comprobación por una captura simulada.

No se afirma que la pestaña haya sido vista o abierta en la instancia real. Falta verificar navegación, datos, filtros, paginación, actualización, responsive y E2E en navegador. La corrección de código y las pruebas automatizadas están realizadas; la aceptación visual completa sigue pendiente.
