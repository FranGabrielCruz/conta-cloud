# Configuración del motor contable

La ruta `configuracion-contable` presenta Cuentas contables y Automatización.
Los períodos se administran en `periodos-fiscales`; la URL anterior con
`?tab=periodos` redirige allí. Los cierres y reaperturas con Contabilidad habilitada
continúan en Cierre contable. Devengado es el método interno soportado.

Guardar está en el flujo normal, arriba a la derecha, sin barra inferior ni scroll
interno forzado. Usa el componente compartido de 40×40 px, icono de 22×22 px y tooltip.
Cambiar de pestaña con cambios pendientes permite continuar editando o descartarlos.
Las funciones compartidas conservan una única asociación UUID entre submódulos.

## Los seis módulos configurables

`Modulo.automatizables()` es la colección de Ventas, Compras, Inventario, CxC, CxP y
Activos fijos. El GET de configuración devuelve exactamente esos seis en `modulos`.
`cuentasModulos` incluye también Cierre para la pestaña de cuentas, sin Finanzas.

Finanzas continúa como origen interno para transferencias. Su preferencia histórica
puede permanecer en la base por compatibilidad, pero no se expone ni gobierna las
operaciones nuevas. Las transferencias internas dependen del maestro y de validar
los recursos utilizados. Los payloads de evaluación y escritura rechazan FINANZAS
y CIERRE como módulos de automatización.

Configuración completa, integración disponible, permiso para administrar,
preferencia por módulo y estado efectivo son independientes. El estado efectivo
requiere maestro, preferencia, configuración obligatoria e integración. La ausencia
de una cuenta opcional no bloquea todo el módulo: se valida cuando un evento la usa.

El maestro puede habilitarse si existe al menos un módulo disponible. No exige tener
todos los módulos completos ni seleccionar previamente un interruptor. Disponible
se muestra en verde; una cuenta obligatoria faltante o inválida produce Configuración
incompleta. La vista representa exclusivamente los estados calculados en backend.

## Seguridad y asociaciones

Todas las operaciones usan tenant y empresa de la sesión autenticada.
Las cuentas deben ser activas, de movimiento y compatibles con su función.
No se infieren por código comercial. Resultado del ejercicio exige su habilitación
explícita; no se sustituye por Capital ni condiciona el cierre mensual.

La lectura requiere el permiso de consultar configuración. Modificar interruptores
requiere `configuracion_contable.automatizacion`; ese permiso no autoriza editar
asociaciones de cuentas. Los mappings requieren el permiso de editar configuración.
Un operador autorizado para registrar un cobro, pago o compra no necesita permisos
para administrar automatización.

Los UUID se almacenan en `cuenta_contable_clave_empresa`, con relaciones multitenant.
Las cajas y cuentas bancarias conservan su asociación propia desde V136 y se configuran
en sus detalles. No existe una cuenta financiera global en esta pantalla.
La cuenta de cada recurso se valida al usarlo; otros recursos incompletos no bloquean
el módulo ni una operación que no los utiliza.

La escritura usa versión y bloqueo de empresa. Se auditan valores anteriores y nuevos,
usuario, ámbito y TraceId. Cambiar configuración no modifica historia ni contabiliza
documentos antiguos.

## Integraciones y propietarios

`AccountingPostingService` recibe eventos internos. `RegistroIntegracionesContables`
resuelve handlers reales; éstos proponen líneas, sin insertar asientos. El motor central
valida configuración, período, cuentas, partida doble y moneda, y escribe el libro.

| Efecto | Propietario y evento |
| --- | --- |
| Ingreso e impuesto de factura POS | Ventas, FACTURA/EMISION |
| Costo de mercancía vendida y salida contable | Ventas, FACTURA/COSTO |
| Inventario/gasto, impuesto recuperable y obligación de factura de proveedor | Compras, FACTURA_PROVEEDOR/REGISTRO |
| Entrada financiera de recibo y aplicación inicial | CxC, RECIBO/CONFIRMACION |
| Aplicación posterior de anticipo de cliente | CxC, APLICACION_RECIBO/APLICACION |
| Salida financiera de pago y aplicación inicial | CxP, PAGO_PROVEEDOR/CONFIRMACION |
| Aplicación posterior de anticipo al proveedor | CxP, APLICACION_PAGO/APLICACION |
| Ajuste propio positivo, negativo o mixto | Inventario, AJUSTE_INVENTARIO/CONFIRMACION |
| Diferencias confirmadas de conteo físico | Inventario, CONTEO_FISICO/CONFIRMACION |
| Depreciación | Activos fijos, DEPRECIACION/PROCESAMIENTO |
| Transferencia entre recursos financieros | Origen interno FINANZAS, TRANSFERENCIA/REGISTRO |

Los cobros POS usan también CxC. El pago al contado de una factura de proveedor
pertenece a CxP, separado del reconocimiento de Compras: una misma liquidación no
se contabiliza en ambos handlers. Las preferencias por módulo permanecen independientes.

CxC debita los recursos realmente recibidos y acredita CxC por lo aplicado; el remanente
acredita Anticipos de clientes. Su aplicación posterior debita anticipos y acredita CxC.
CxP debita la obligación aplicada, o Anticipos a proveedores por el remanente, y acredita
el recurso pagador. Su aplicación posterior debita CxP y acredita anticipos.

Las recepciones físicas y los movimientos físicos de venta no vuelven a reconocer el
valor ya registrado por Compras o Ventas. Las transferencias de inventario entre almacenes
de la misma empresa tampoco producen una nueva ganancia o pérdida. Inventario publica
exclusivamente los ajustes y diferencias propios indicados en la tabla.

## Valoración y moneda

Compras distingue productos con control de existencias de servicios/productos sin control.
El inventario utiliza INVENTORY_DEFAULT; PURCHASE_EXPENSE exige GASTOS, no COSTOS de
mercancía vendida. Los descuentos reducen la base del grupo correspondiente.
PURCHASE_DISCOUNTS puede estar vacía con esta política neta. Impuesto recuperable e
inventario son condicionales: si la operación los necesita y faltan, se rechaza toda
la operación con un mensaje seguro.

Los ajustes y el costo de venta usan el costo de compra configurado del producto al
confirmar, multiplicado por su cantidad base. No se inventa un costo cuando falta.
Se conserva el producto, cantidad, costo unitario, moneda, tasa y valor base en el evento.
Un costo explícitamente cero no genera un asiento artificial. No se introduce FIFO
ni promedio ponderado donde el dominio actual no los dispone.

El libro utiliza la moneda base activa de la empresa y dos decimales. V138 conserva
la moneda original y la tasa. Para otra moneda se resuelve la tasa activa exacta de
la fecha, directa o inversa a ocho decimales, siguiendo el mecanismo existente.
Sin tasa se rechaza la operación. El residuo de conversión se distribuye entre las
mayores partidas de cada lado sin cuentas ni líneas artificiales.

## Atomicidad, historia e idempotencia

El posting participa síncronamente en la transacción JPA/JDBC de la operación.
Un fallo revierte documento, obligación, aplicación, movimiento y secuencia; no existe
un segundo commit ni un outbox necesario para este flujo local.

La clave única por tenant/empresa/origen/tipo/documento/evento y el bloqueo de empresa
impiden asientos duplicados. Los reintentos de documentos confirmados no realizan
contabilizaciones históricas al activar posteriormente un módulo.

Los asientos automáticos quedan Contabilizados e inmutables. La anulación crea una
reversión con las cuentas e importes históricos, incluso con maestro apagado o mappings
modificados. Revertir un recibo/pago revierte también sus aplicaciones posteriores.
Los períodos cerrados no se evaden cambiando fechas. Las operaciones de tesorería
usan la fecha del documento; los ajustes inversos usan su fecha operativa explícita.

V137 conserva versión y TraceId. V139 añade el snapshot JSON de los hechos confirmados,
incluida valoración. Los disparadores existentes protegen también esas columnas.
Los eventos estructurados de posting registran ámbito, origen, documento, evento,
versión, asiento y TraceId. El detalle de la factura de proveedor ofrece acceso al asiento
sujeto al permiso de Contabilidad.

## Verificación

Las pruebas cubren exactamente seis módulos en el endpoint, exclusión de Finanzas,
payload manipulado, permisos separados, obligatoriedad y opcionales, persistencia,
idempotencia, concurrencia, moneda, períodos cerrados y aislamiento entre empresas.

Las pruebas JPA/PostgreSQL ejercitan registros, pagos y anticipos de proveedor,
cobros y anticipos de cliente, ajustes de inventario y reversiones. Los datos de negocio
se crean en empresas de prueba y se revierten; la prueba de concurrencia limpia su
fixture temporal sin eliminar asientos contabilizados.

La pantalla de DEMO en 8080 se verificó con los seis módulos disponibles y sus
interruptores habilitados. Se guardaron y recargaron preferencias con el maestro
apagado y después se restauraron los valores originales. Se comprobó el diseño
en escritorio y móvil, sin desbordamiento horizontal, con Guardar de 40×40 e icono 22×22.

