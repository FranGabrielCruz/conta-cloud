# Desconexión mientras Vaadin entrega index.html

El registro del 08/11/2026 muestra primero `ClientAbortException` al escribir `index.html`. Después Vaadin intenta `sendError` sobre una respuesta ya comprometida y genera varias excepciones secundarias. El registro acredita una interrupción del transporte; no permite identificar qué software provocó el corte.

`HtmlClientDisconnectFilter` protege únicamente solicitudes GET que aceptan HTML. Al escribir por ServletOutputStream comprueba además que la respuesta sea text/html. Si Spring DisconnectedClientHelper reconoce una desconexión, detiene las escrituras posteriores sobre ese socket y registra una sola línea DEBUG con TraceId. Así, el manejador de bootstrap no recibe una IOException de desconexión que lo lleve a intentar una segunda respuesta de error. No se reintenta el envío de un HTML parcialmente entregado.

Los errores de escritura distintos de una desconexión siguen propagándose. También siguen propagándose las excepciones de aplicación, incluidos otros IllegalStateException por respuestas comprometidas. POST, JSON, PDF y respuestas que no son HTML mantienen su comportamiento. No se bajó el nivel de logging global de Vaadin/Tomcat ni se modificaron seguridad, contratos REST, reglas financieras, dependencias o migraciones.

El filtro se ejecuta después de CorrelationIdFilter y mantiene su cabecera y limpieza MDC. No modifica autenticación ni corta la ejecución normal de la cadena de filtros.

Verificación: **14 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**. Nueve casos nuevos cubren respuesta HTML ya comprometida, bytes/cabeceras normales, IOException real, excepciones de aplicación, POST/JSON/PDF, supresión de escrituras posteriores, flush y conservación del TraceId; se ejecutan además cinco regresiones de ErrorClassifier. Evidencia: `target/html-client-disconnect-final-tests.log`.

Las pruebas simulan el fallo de transporte con ClientAbortException de Tomcat. No reproducen la interrupción específica del equipo del usuario ni demuestran que el software que cerró la conexión haya dejado de hacerlo. El cambio evita la cascada secundaria en el servidor, no repara una conexión que ya se perdió.

Referencia: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/DisconnectedClientHelper.html
