# AGENTS.md

# ContaCloud — Guía para Agentes de Desarrollo

## 1. Descripción del proyecto

**ContaCloud** es una aplicación SaaS multitenant orientada a la gestión administrativa, contable y fiscal de empresas.

El sistema permitirá que múltiples empresas utilicen la misma aplicación y la misma base de datos, manteniendo los datos de cada empresa completamente aislados mediante `empresa_id`.

La arquitectura debe estar preparada para crecer progresivamente hacia módulos como:

- Contabilidad
- Cuentas por cobrar
- Cuentas por pagar
- Facturación
- Inventario
- Bancos
- Compras
- Ventas
- Activos fijos
- Presupuestos
- Reportes financieros
- Gestión fiscal
- Integraciones

La primera versión corresponde al:

**MVP — Fase 1: Base administrativa, fiscal y contable**

No implementar módulos avanzados antes de completar correctamente esta base.

---

# 2. Stack tecnológico

## Backend

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL

## Frontend / UI

- Vaadin
- Diseño responsive
- Componentes Vaadin
- Interfaz moderna tipo SaaS

Preferencias de diseño:

- Moderno
- Profesional
- Responsive
- Minimalista
- Claro
- Consistente
- Fácil de utilizar

## Base de datos

PostgreSQL.

Base de datos local:

conta_cloud

La base de datos ya existe localmente y debe ser utilizada por la aplicación.

No crear otra base de datos automáticamente.

---

# 3. Configuración de PostgreSQL

La aplicación debe conectarse a:

Base de datos: conta_cloud

Servidor:

localhost:5432

La configuración debe estar centralizada.

Ejemplo:

spring.datasource.url=jdbc:postgresql://localhost:5432/conta_cloud
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD}

Nunca almacenar contraseñas reales dentro del repositorio.

Utilizar variables de entorno para:

- Contraseñas
- API Keys
- Tokens
- Credenciales externas
- Secrets

---

# 4. Flyway

Flyway será el mecanismo oficial para administrar la estructura de la base de datos.

## Regla fundamental

NO crear tablas manualmente desde PostgreSQL.

NO utilizar Hibernate/JPA para generar automáticamente la estructura.

NO utilizar:

spring.jpa.hibernate.ddl-auto=create

ni:

spring.jpa.hibernate.ddl-auto=update

Utilizar:

spring.jpa.hibernate.ddl-auto=validate

La estructura de la base de datos debe ser administrada exclusivamente mediante migraciones Flyway.

---

# 5. Estructura de migraciones

Las migraciones deben almacenarse en:

src/main/resources/db/migration/

Utilizar nombres descriptivos.

Ejemplo:

V1__crear_empresas.sql
V2__crear_seguridad.sql
V3__crear_sucursales.sql
V4__crear_datos_empresa.sql
V5__crear_monedas.sql
V6__crear_tasas_cambio.sql
V7__crear_impuestos.sql
V8__crear_tipos_comprobantes_fiscales.sql
V9__crear_secuencias.sql
V10__crear_condiciones_pago.sql
V11__crear_periodos_fiscales.sql
V12__crear_configuracion_contable.sql

Nunca modificar una migración que ya haya sido ejecutada.

Si se necesita modificar una estructura existente, crear una nueva migración.

Ejemplo:

V13__agregar_campo_moneda_base_empresa.sql

---

# 6. Arquitectura Multitenant

ContaCloud utilizará:

Shared Database
+
Shared Schema
+
empresa_id

Todas las empresas estarán dentro de:

PostgreSQL
└── conta_cloud

No crear una base de datos independiente para cada empresa.

Ejemplo conceptual:

conta_cloud
│
├── empresas
├── usuarios
├── roles
├── permisos
├── sucursales
├── monedas
├── tasas_cambio
├── impuestos
├── tipos_comprobantes_fiscales
├── secuencias
├── condiciones_pago
├── periodos_fiscales
├── configuracion_contable
└── ...

Los registros pertenecientes a una empresa estarán separados mediante:

empresa_id

---

# 7. Regla fundamental de multitenancy

Todo dato perteneciente a una empresa debe estar asociado a:

empresa_id

Ejemplo:

CREATE TABLE sucursales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID NOT NULL,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(150) NOT NULL
);

Nunca realizar consultas sobre información de negocio sin aplicar el contexto del tenant.

Incorrecto:

SELECT * FROM sucursales;

Correcto:

SELECT *
FROM sucursales
WHERE empresa_id = :empresaId;

El `empresa_id` nunca debe ser proporcionado libremente desde el frontend para determinar el tenant de una operación.

Debe obtenerse exclusivamente desde el contexto de autenticación.

El backend es responsable de establecer y validar el tenant.

---

# 8. Login Multitenant

El login de ContaCloud debe utilizar:

Empresa
Usuario
Contraseña

Ejemplo:

Empresa: EMPRESA01
Usuario: gabriel
Contraseña: ********

Proceso:

1. Buscar la empresa mediante su código.
2. Validar que la empresa exista.
3. Validar que la empresa esté activa.
4. Buscar el usuario dentro de esa empresa.
5. Validar la contraseña.
6. Validar que el usuario esté activo.
7. Crear el contexto de autenticación.
8. Mantener disponible el `empresa_id` durante toda la sesión.

Nunca buscar usuarios únicamente por:

usuario

Utilizar:

empresa_id + usuario

Debe existir:

UNIQUE (empresa_id, usuario)

Esto permite que múltiples empresas tengan usuarios como:

admin

sin producir conflictos.

---

# 9. MVP — Fase 1

La primera fase de ContaCloud incluirá:

1. Tenants / Empresas
2. Usuarios
3. Roles
4. Permisos
5. Sucursales
6. Datos de la empresa
7. Monedas
8. Tasas de cambio
9. Impuestos
10. Tipos de comprobantes fiscales
11. Secuencias
12. Condiciones de pago
13. Períodos fiscales
14. Configuración contable
15. Auditoría básica

Esta fase constituye la infraestructura funcional necesaria para los módulos administrativos y contables posteriores.

No implementar todavía:

- Facturación
- Ventas
- Compras
- Inventario
- Cuentas por cobrar
- Cuentas por pagar
- Bancos
- Asientos contables operativos
- Activos fijos
- Nómina
- Presupuestos

salvo componentes técnicos estrictamente necesarios para preparar la arquitectura.

---

# 10. Modelo inicial de datos

Las principales tablas del MVP serán:

empresas
usuarios
roles
permisos
usuario_roles
rol_permisos

sucursales

datos_empresa

monedas
tasas_cambio

impuestos

tipos_comprobantes_fiscales
secuencias

condiciones_pago

periodos_fiscales

configuracion_contable

auditoria

También pueden existir tablas técnicas para:

sesiones
tokens
configuracion_sistema
parametros_empresa

cuando sean necesarias.

---

# 11. Relaciones principales

## Empresa

Una empresa puede tener:

1:N usuarios
1:N roles
1:N sucursales
1:N monedas configuradas
1:N tasas de cambio
1:N impuestos
1:N tipos de comprobantes fiscales
1:N secuencias
1:N condiciones de pago
1:N períodos fiscales

Una empresa debe tener su propia configuración contable.

---

## Usuario

Un usuario pertenece a una empresa.

Puede tener:

N:M roles

Los roles deben pertenecer al mismo tenant del usuario.

---

## Rol

Un rol pertenece a una empresa.

Puede tener:

N:M usuarios
N:M permisos

---

## Sucursal

Una sucursal pertenece obligatoriamente a una empresa.

Una empresa puede tener múltiples sucursales.

Debe existir la posibilidad de identificar una sucursal principal.

---

## Moneda

Las monedas representan las monedas disponibles para operaciones financieras.

La configuración debe permitir definir:

- Moneda base
- Monedas adicionales
- Símbolo
- Código ISO
- Cantidad de decimales
- Estado

Ejemplos:

DOP
USD
EUR

---

## Tasa de cambio

Una tasa de cambio relaciona una moneda con su valor respecto a la moneda base.

Debe considerar:

- Empresa
- Moneda
- Fecha
- Tasa
- Estado

Debe conservarse el histórico de tasas.

---

## Impuesto

Los impuestos pertenecen al contexto de una empresa.

Deben permitir definir:

- Código
- Nombre
- Porcentaje
- Tipo
- Estado
- Cuenta contable asociada cuando corresponda

---

## Tipo de comprobante fiscal

Representa los diferentes tipos de documentos fiscales que podrá manejar ContaCloud.

Debe permitir:

- Código
- Nombre
- Prefijo
- Tipo
- Estado
- Reglas de secuencia

La implementación debe estar preparada para los requerimientos fiscales correspondientes al país configurado para la empresa.

---

## Secuencia

Las secuencias controlarán la numeración de documentos.

Ejemplos futuros:

Facturas
Notas de crédito
Notas de débito
Recibos
Comprobantes fiscales
Órdenes
Documentos internos

Toda generación de número secuencial debe realizarse en backend.

Debe ser segura frente a concurrencia.

Nunca calcular una secuencia utilizando:

MAX(numero) + 1

Utilizar mecanismos transaccionales y bloqueo apropiado.

---

## Condición de pago

Representa las condiciones comerciales utilizadas posteriormente en compras y ventas.

Ejemplos:

Contado
15 días
30 días
45 días
60 días

Debe permitir definir:

- Código
- Nombre
- Días
- Estado

---

## Período fiscal

Representa períodos contables/fiscales de una empresa.

Debe permitir:

- Año
- Mes
- Fecha inicial
- Fecha final
- Estado

Estados mínimos:

ABIERTO
CERRADO

La arquitectura debe permitir posteriormente implementar:

BLOQUEADO

No permitir operaciones contables sobre períodos cerrados cuando los módulos contables sean implementados.

---

## Configuración contable

Debe centralizar parámetros necesarios para el futuro motor contable.

Debe permitir preparar:

- Moneda base
- Año fiscal
- Método de numeración
- Manejo de decimales
- Configuración fiscal
- Parámetros generales de contabilidad

No implementar todavía el motor completo de asientos si no pertenece al alcance de la fase.

---

# 12. Reglas de integridad multitenant

Las relaciones deben impedir que se mezclen registros pertenecientes a diferentes empresas.

Ejemplo:

Una condición de pago de:

empresa_id = A

no puede ser utilizada por:

empresa_id = B

Lo mismo aplica a:

- Usuarios
- Roles
- Sucursales
- Impuestos
- Secuencias
- Períodos
- Configuraciones
- Tasas
- Cualquier entidad futura

Implementar protección mediante:

1. Validaciones de servicio.
2. Restricciones de base de datos cuando sea posible.
3. Contexto de tenant.
4. Filtros de repositorio.
5. Autorización.

Nunca confiar únicamente en el frontend.

---

# 13. UUID

Utilizar UUID como identificador principal para entidades de negocio.

Ejemplo:

id UUID PRIMARY KEY DEFAULT gen_random_uuid()

No utilizar IDs incrementales como mecanismo principal de identificación de entidades multitenant.

Las secuencias comerciales/documentales son independientes del ID técnico.

Ejemplo:

id = UUID
numero_factura = 000001

Nunca utilizar el número comercial como Primary Key.

---

# 14. Índices

Crear índices teniendo siempre presente el acceso multitenant.

Ejemplos:

CREATE INDEX idx_usuarios_empresa
ON usuarios(empresa_id);

CREATE INDEX idx_sucursales_empresa
ON sucursales(empresa_id);

CREATE INDEX idx_impuestos_empresa
ON impuestos(empresa_id);

CREATE INDEX idx_periodos_empresa
ON periodos_fiscales(empresa_id);

CREATE INDEX idx_tasas_empresa_fecha
ON tasas_cambio(empresa_id, fecha);

Cuando corresponda:

UNIQUE (empresa_id, codigo)

UNIQUE (empresa_id, usuario)

No crear índices innecesarios.

Evaluar índices según patrones reales de búsqueda.

---

# 15. Vaadin — Diseño de interfaz

La interfaz de ContaCloud debe desarrollarse utilizando Vaadin.

El diseño debe ser:

- Moderno
- Profesional
- Limpio
- Minimalista
- Responsive
- Orientado a SaaS
- Fácil de utilizar
- Consistente

Evitar interfaces antiguas o excesivamente cargadas.

Todas las pantallas deben mantener una identidad visual consistente.

---

# 16. Referencia visual

Crear y mantener:

design/

Utilizar correctamente `design`, no `desing`.

Esta carpeta contendrá referencias visuales.

Ejemplo:

design/
├── README.md
├── login.png
├── dashboard.png
├── empresas.png
├── usuarios.png
├── sucursales.png
├── monedas.png
├── impuestos.png
├── periodos-fiscales.png
└── referencia-ui.png

Las referencias generadas mediante Stitch u otras herramientas deben utilizarse como guía visual.

No copiar ciegamente componentes que no sean apropiados para Vaadin.

Las referencias definen principalmente:

- Layout
- Espaciado
- Tipografía
- Jerarquía
- Colores
- Componentes
- Navegación
- Tablas
- Formularios
- Cards
- Estados
- Modales
- Filtros

---

# 17. Layout principal

ContaCloud utilizará un layout administrativo tipo SaaS.

Ejemplo conceptual:

┌────────────────────────────────────────────────────────────┐
│ ContaCloud                 Empresa            Usuario      │
├────────────────┬───────────────────────────────────────────┤
│ Dashboard      │                                           │
│                │                                           │
│ Administración│                                           │
│ ├ Empresas     │                                           │
│ ├ Sucursales   │               CONTENIDO                   │
│                │                                           │
│ Configuración  │                                           │
│ ├ Monedas      │                                           │
│ ├ Tasas        │                                           │
│ ├ Impuestos    │                                           │
│ ├ Comprobantes │                                           │
│ ├ Secuencias   │                                           │
│ ├ Cond. Pago   │                                           │
│ ├ Períodos     │                                           │
│ └ Contabilidad │                                           │
│                │                                           │
│ Seguridad      │                                           │
│ ├ Usuarios     │                                           │
│ └ Roles        │                                           │
└────────────────┴───────────────────────────────────────────┘

El sidebar debe:

- Ser responsive.
- Poder colapsarse.
- Mostrar solamente opciones autorizadas.
- Agrupar módulos lógicamente.
- Mantener seleccionado el módulo actual.

---

# 18. Dashboard

El dashboard debe mostrar exclusivamente información de la empresa autenticada.

Durante el MVP debe ser sencillo.

Puede incluir:

- Empresa activa
- Sucursales activas
- Usuarios activos
- Moneda base
- Período fiscal actual
- Estado del período
- Última tasa de cambio
- Configuración pendiente

También puede mostrar accesos rápidos a:

- Nueva sucursal
- Nuevo usuario
- Nueva tasa de cambio
- Nuevo impuesto
- Configuración contable

No inventar indicadores financieros antes de implementar los módulos que generan dicha información.

Ejemplo:

No mostrar:

Ventas del mes
Utilidad
Cuentas por cobrar
Balance bancario

si todavía no existen esos módulos.

Nunca mostrar información de otra empresa.

---

# 19. Estándar de pantallas CRUD

Los módulos administrativos deben mantener un comportamiento consistente.

Pantalla típica:

Título
Descripción opcional

Filtros / búsqueda

Tabla

Paginación

Acciones

La paginación estándar será:

10 registros por página.

Las tablas deben soportar, cuando corresponda:

- Ordenamiento
- Búsqueda
- Filtros
- Paginación
- Estados visuales
- Acciones

---

# 20. Estándar de botones

## Nuevo

Color:

Azul

Contenido:

Solo icono +

Tooltip contextual.

Ejemplos:

Nueva sucursal
Nuevo usuario
Nueva moneda
Nuevo impuesto

No colocar texto visible dentro del botón.

---

## Guardar

Color:

Verde

Contenido:

Icono de guardar

Tooltip:

Guardar

---

## Cancelar

Color:

Gris

Contenido:

Icono correspondiente

Tooltip:

Cancelar

---

## Acciones de tablas

Utilizar únicamente iconos.

Sin contorno visual innecesario.

Ejemplos:

Editar
Ver
Activar
Desactivar

Todos deben tener tooltip descriptivo.

No utilizar botones grandes con texto dentro de las tablas.

---

# 21. Formato de datos

## Fechas

Mostrar fechas utilizando:

DD/MM/YYYY

Ejemplo:

09/09/2026

Aplica a:

- Pantallas
- Formularios
- Tablas
- Reportes
- PDF
- Excel

Internamente pueden utilizarse los tipos de fecha correspondientes de Java/PostgreSQL.

---

## Valores numéricos

Los campos numéricos deben aplicar formato al perder el foco.

Ejemplo:

1000

debe mostrarse como:

1,000.00

cuando corresponda a valores monetarios o decimales configurados.

Nunca utilizar valores formateados como representación interna para cálculos.

Utilizar:

BigDecimal

para importes monetarios.

No utilizar:

float

o:

double

para cálculos contables o monetarios.

---

# 22. Pantallas mínimas del MVP

## Autenticación

- Login
- Recuperación de contraseña

## Dashboard

- Dashboard principal

## Empresa

- Datos de empresa
- Configuración general

## Usuarios

- Listado
- Crear
- Editar
- Activar/desactivar

## Roles

- Listado
- Crear
- Editar
- Permisos

## Sucursales

- Listado
- Crear
- Editar
- Activar/desactivar

## Monedas

- Listado
- Crear
- Editar
- Activar/desactivar
- Definir moneda base

## Tasas de cambio

- Listado
- Registrar tasa
- Editar cuando corresponda
- Histórico

## Impuestos

- Listado
- Crear
- Editar
- Activar/desactivar

## Comprobantes fiscales

- Tipos de comprobantes
- Configuración
- Activar/desactivar

## Secuencias

- Listado
- Crear
- Editar configuración
- Consultar numeración actual

## Condiciones de pago

- Listado
- Crear
- Editar
- Activar/desactivar

## Períodos fiscales

- Listado
- Crear
- Abrir
- Cerrar
- Consultar estado

## Configuración contable

- Configuración general
- Moneda base
- Año fiscal
- Parámetros contables

---

# 23. Arquitectura backend

Utilizar arquitectura por capas.

Controller / View
        ↓
Service
        ↓
Repository
        ↓
PostgreSQL

Separar correctamente:

DTO
Entity
Repository
Service
Controller / View
Mapper cuando corresponda

No colocar lógica compleja de negocio dentro de componentes Vaadin.

Vaadin debe encargarse principalmente de:

- Presentación
- Captura de datos
- Validaciones básicas de interfaz
- Navegación
- Interacción con servicios

La lógica real debe permanecer en servicios backend.

---

# 24. Transacciones

Las operaciones que modifiquen múltiples registros relacionados deben utilizar transacciones.

Utilizar:

@Transactional

cuando corresponda.

Especial atención a:

- Secuencias
- Cierre de períodos
- Configuraciones
- Operaciones contables futuras
- Facturación futura
- Inventario futuro

Una operación debe completarse completamente o revertirse.

---

# 25. Seguridad

Implementar:

- Spring Security
- Hash seguro de contraseñas
- Control de acceso por roles
- Permisos
- Validación del tenant
- Protección contra acceso cruzado
- Validación backend
- Auditoría
- Gestión segura de sesión

Nunca almacenar contraseñas en texto plano.

Nunca confiar en:

empresa_id

enviado desde frontend.

Nunca confiar exclusivamente en controles visuales de Vaadin para autorización.

Ocultar un botón NO sustituye la validación de permisos en backend.

---

# 26. Roles y permisos

Los permisos deben ser granulares.

Ejemplos:

USUARIO_VER
USUARIO_CREAR
USUARIO_EDITAR
USUARIO_DESACTIVAR

SUCURSAL_VER
SUCURSAL_CREAR
SUCURSAL_EDITAR

MONEDA_VER
MONEDA_CREAR
MONEDA_EDITAR

IMPUESTO_VER
IMPUESTO_CREAR
IMPUESTO_EDITAR

PERIODO_VER
PERIODO_CREAR
PERIODO_CERRAR

CONFIGURACION_CONTABLE_VER
CONFIGURACION_CONTABLE_EDITAR

Las pantallas y operaciones deben respetar estos permisos.

---

# 27. Auditoría

ContaCloud debe disponer de auditoría básica desde el MVP.

Registrar operaciones importantes.

Como mínimo:

- Empresa
- Usuario
- Fecha/hora
- Acción
- Entidad
- ID del registro
- Información relevante del cambio

Ejemplos:

CREAR
EDITAR
ACTIVAR
DESACTIVAR
CERRAR_PERIODO
CAMBIAR_CONFIGURACION

La auditoría no debe depender únicamente del frontend.

---

# 28. Manejo de errores

No mostrar excepciones técnicas directamente al usuario.

Incorrecto:

org.springframework.dao.DataIntegrityViolationException...

Correcto:

No fue posible guardar el registro porque el código ya existe.

Los errores deben:

1. Registrarse técnicamente.
2. Transformarse en mensajes comprensibles.
3. No revelar información sensible.
4. Mantener contexto suficiente para diagnóstico.

---

# 29. Reglas para el código

- Código limpio.
- Entidades de dominio en español.
- Nombres descriptivos.
- Evitar duplicación.
- Crear componentes reutilizables.
- Crear servicios reutilizables.
- Validar frontend y backend.
- Mantener responsabilidades separadas.
- No introducir dependencias innecesarias.
- Utilizar BigDecimal para importes.
- Utilizar tipos de fecha de java.time.
- Evitar lógica contable dentro de Views.
- Documentar decisiones arquitectónicas importantes.

---

# 30. Pruebas

Toda funcionalidad debe incluir las pruebas esenciales que correspondan.

## Unit Tests

Validar lógica aislada de:

- Servicios
- Validaciones
- Cálculos
- Reglas de negocio
- Secuencias
- Conversión de datos

## Integration Tests

Validar integración entre:

- Service
- Repository
- PostgreSQL
- Flyway
- Seguridad
- Multitenancy

## Security Tests

Validar especialmente:

- Acceso entre tenants.
- Roles.
- Permisos.
- Sesiones.
- Recursos protegidos.

Debe existir una prueba explícita que demuestre que:

Usuario Empresa A

NO puede consultar:

Datos Empresa B

aunque intente manipular parámetros o IDs.

## E2E Tests

Aplicar a flujos críticos.

Ejemplos:

Login
Crear usuario
Crear sucursal
Registrar moneda
Registrar tasa
Crear impuesto
Crear período fiscal
Cerrar período

---

# 31. Base de datos

Base oficial de desarrollo:

conta_cloud

Servidor:

localhost:5432

Todas las modificaciones estructurales deben realizarse mediante Flyway.

Nunca crear tablas manualmente.

Nunca modificar directamente la estructura para solucionar rápidamente un problema.

Crear siempre una migración.

---

# 32. Reglas de migraciones

Antes de modificar el modelo:

1. Analizar relaciones existentes.
2. Analizar impacto multitenant.
3. Crear nueva migración.
4. Ejecutar Flyway.
5. Verificar restricciones.
6. Ejecutar pruebas.
7. Verificar índices.
8. No modificar migraciones aplicadas.

Cada migración debe representar un cambio claro y rastreable.

---

# 33. Reglas para dinero y contabilidad

Toda cantidad monetaria debe utilizar:

BigDecimal

En PostgreSQL utilizar:

NUMERIC(precision, scale)

según corresponda.

Nunca utilizar:

float
double

para valores monetarios.

Las operaciones contables deben mantener precisión decimal.

La cantidad de decimales mostrados puede depender de la configuración de moneda.

La lógica contable futura debe basarse en principios de partida doble.

Todo asiento deberá mantener:

Débitos = Créditos

cuando el motor contable sea implementado.

---

# 34. Reglas para secuencias

Las secuencias documentales son críticas.

Nunca utilizar:

SELECT MAX(numero) + 1

Debe garantizarse:

- Atomicidad
- Concurrencia
- Integridad
- Separación por empresa
- Separación por tipo de documento cuando corresponda

Una secuencia puede depender de:

empresa_id
sucursal_id
tipo_documento
serie
año

según las reglas futuras.

La generación debe realizarse en backend dentro de una transacción.

---

# 35. Reglas para períodos fiscales

Los períodos fiscales deben estar asociados a:

empresa_id

No permitir períodos inválidos.

Evitar solapamientos cuando la configuración así lo requiera.

Un período cerrado no debe aceptar nuevas operaciones contables.

El cierre debe registrarse en auditoría.

Debe conservarse:

fecha_cierre
usuario_cierre

cuando corresponda.

La reapertura de períodos, si se implementa posteriormente, deberá requerir permiso específico y auditoría.

---

# 36. Prioridad de implementación

Implementar en el siguiente orden.

## Etapa 1 — Infraestructura

PostgreSQL
Flyway
Configuración base
Empresas
Multitenancy
TenantContext

## Etapa 2 — Seguridad

Usuarios
Roles
Permisos
Login multitenant
Spring Security
Recuperación de contraseña

## Etapa 3 — Organización

Datos de empresa
Sucursales

## Etapa 4 — Configuración financiera

Monedas
Moneda base
Tasas de cambio
Histórico de tasas

## Etapa 5 — Configuración fiscal

Impuestos
Tipos de comprobantes fiscales
Secuencias

## Etapa 6 — Configuración comercial

Condiciones de pago

## Etapa 7 — Base contable

Períodos fiscales
Configuración contable

## Etapa 8 — Administración

Dashboard
Auditoría
Pruebas finales del MVP

No comenzar módulos posteriores hasta estabilizar esta base.

---

# 37. Criterios de aceptación del MVP

El MVP se considera funcional cuando:

- El usuario puede iniciar sesión con Empresa + Usuario + Contraseña.
- El sistema identifica correctamente el tenant.
- Los usuarios solo acceden a información de su empresa.
- Existe protección backend contra acceso cruzado.
- Se pueden administrar usuarios.
- Se pueden administrar roles.
- Se pueden asignar permisos.
- Se pueden administrar sucursales.
- Se pueden configurar los datos de empresa.
- Se pueden administrar monedas.
- Se puede definir una moneda base.
- Se pueden registrar tasas de cambio.
- Se conserva histórico de tasas.
- Se pueden administrar impuestos.
- Se pueden configurar tipos de comprobantes fiscales.
- Se pueden administrar secuencias.
- Las secuencias son seguras frente a concurrencia.
- Se pueden administrar condiciones de pago.
- Se pueden administrar períodos fiscales.
- Se pueden abrir y cerrar períodos.
- Se puede establecer la configuración contable.
- Las operaciones importantes generan auditoría.
- PostgreSQL utiliza `conta_cloud`.
- Flyway controla completamente la estructura.
- Hibernate valida pero no crea/modifica tablas.
- La interfaz utiliza Vaadin.
- La interfaz es responsive.
- Las tablas utilizan paginación de 10 registros.
- Las fechas se muestran DD/MM/YYYY.
- Los valores monetarios utilizan BigDecimal.
- Las acciones respetan roles y permisos.
- Existen pruebas Unit.
- Existen pruebas Integration.
- Existen pruebas Security.
- Existen pruebas E2E para flujos críticos.

---

# 38. Regla principal para el agente

Antes de implementar cualquier funcionalidad, responder internamente:

1. ¿Pertenece a la fase actual?
2. ¿A qué tenant pertenece el dato?
3. ¿La operación respeta `empresa_id`?
4. ¿Existe riesgo de acceso cruzado?
5. ¿Necesita migración Flyway?
6. ¿Existe una referencia visual en `design/`?
7. ¿Respeta el estándar visual de ContaCloud?
8. ¿Respeta roles y permisos?
9. ¿Necesita auditoría?
10. ¿Necesita transacción?
11. ¿Utiliza correctamente BigDecimal si maneja dinero?
12. ¿Necesita índices?
13. ¿Necesita una restricción UNIQUE multitenant?
14. ¿Qué pruebas necesita?
15. ¿La solución será reutilizable en módulos posteriores?

No implementar funcionalidades fuera del alcance sin justificar técnicamente su necesidad.

---

# 39. Regla para nuevas entidades

Antes de crear cualquier entidad de negocio determinar:

¿Es global o pertenece a una empresa?

Si pertenece a una empresa:

Debe contener:

empresa_id

Analizar además si necesita:

created_at
created_by
updated_at
updated_by
activo

No agregar campos automáticamente si no tienen utilidad real.

Para códigos únicos de una empresa utilizar normalmente:

UNIQUE (empresa_id, codigo)

y no:

UNIQUE (codigo)

---

# 40. Preparación para módulos futuros

Aunque no deben implementarse todavía, la arquitectura debe evitar decisiones que dificulten incorporar posteriormente:

## Contabilidad

- Catálogo de cuentas
- Asientos
- Diario general
- Mayor general
- Balance de comprobación
- Estado de resultados
- Balance general
- Cierres

## Ventas

- Clientes
- Cotizaciones
- Pedidos
- Facturas
- Notas de crédito
- Notas de débito
- Recibos

## Cuentas por cobrar

- Documentos pendientes
- Aplicación de pagos
- Antigüedad de saldos
- Estados de cuenta

## Compras

- Proveedores
- Órdenes de compra
- Recepciones
- Facturas de proveedor

## Cuentas por pagar

- Obligaciones
- Pagos
- Vencimientos
- Antigüedad

## Inventario

- Productos
- Almacenes
- Existencias
- Movimientos
- Costos

## Bancos

- Cuentas bancarias
- Depósitos
- Cheques
- Transferencias
- Conciliación

La Fase 1 debe proporcionar una base reutilizable para estos módulos.

---

# 41. Escalabilidad

La aplicación debe diseñarse pensando en crecimiento.

Evitar:

- Acoplamiento fuerte.
- Consultas sin tenant.
- Servicios gigantes.
- Componentes Vaadin con lógica de negocio.
- Configuración hardcodeada.
- Dependencias innecesarias.
- Duplicación de reglas.

Favorecer:

- Servicios especializados.
- Componentes reutilizables.
- DTOs.
- Validaciones centralizadas.
- Configuración por empresa.
- Auditoría.
- Arquitectura modular.

---

# 42. Rendimiento

Toda consulta sobre tablas multitenant de alto crecimiento debe analizar:

empresa_id

como parte de sus índices.

Evitar:

N+1 queries

Carga innecesaria de relaciones.

SELECT * cuando no sea necesario.

No cargar miles de registros en memoria para realizar paginación visual.

La paginación debe realizarse preferiblemente desde backend/base de datos.

Tamaño estándar:

10 registros.

---

# 43. Reglas para reportes

Los reportes futuros deben respetar:

- Tenant actual.
- Permisos.
- Formato de fechas DD/MM/YYYY.
- Formato monetario.
- Moneda.
- Empresa.
- Sucursal cuando corresponda.
- Filtros aplicados.

Los reportes PDF y Excel correspondientes al mismo reporte deben contener la misma información funcional, aunque su presentación pueda adaptarse al formato.

No generar información financiera utilizando datos pertenecientes a diferentes empresas.

---

# 44. Configuración por empresa

Evitar configuraciones globales cuando conceptualmente pertenecen a cada empresa.

Ejemplos:

Moneda base
Configuración fiscal
Configuración contable
Secuencias
Condiciones de pago
Períodos fiscales

Toda configuración empresarial debe resolverse mediante:

empresa_id

La existencia de una configuración global debe justificarse técnicamente.

---

# 45. Convenciones generales

## Entidades

Utilizar nombres de dominio en español.

Ejemplos:

Empresa
Sucursal
Usuario
Rol
Permiso
Moneda
TasaCambio
Impuesto
TipoComprobanteFiscal
Secuencia
CondicionPago
PeriodoFiscal
ConfiguracionContable

## Tablas

Utilizar nombres consistentes.

Ejemplos:

empresas
sucursales
usuarios
roles
permisos
monedas
tasas_cambio
impuestos
tipos_comprobantes_fiscales
secuencias
condiciones_pago
periodos_fiscales
configuracion_contable

Mantener la misma convención durante todo el proyecto.

---

# 46. Definición de terminado — Definition of Done

Una funcionalidad NO está terminada únicamente porque compile.

Se considera terminada cuando:

1. Cumple la especificación.
2. Respeta multitenancy.
3. Respeta permisos.
4. Tiene validaciones backend.
5. Tiene validaciones frontend cuando corresponda.
6. Tiene migración Flyway si modifica estructura.
7. Tiene manejo correcto de errores.
8. Tiene auditoría cuando corresponda.
9. Tiene pruebas necesarias.
10. La interfaz respeta el diseño.
11. Es responsive.
12. No introduce errores en funcionalidades existentes.
13. Las pruebas existentes continúan funcionando.
14. No existen consultas de negocio inseguras respecto al tenant.
15. El código mantiene las convenciones del proyecto.

---

# 47. Regla para modificaciones existentes

Antes de modificar código existente:

1. Analizar su funcionamiento.
2. Identificar dependencias.
3. Verificar pruebas existentes.
4. Determinar impacto sobre multitenancy.
5. Determinar impacto sobre seguridad.
6. Determinar impacto sobre base de datos.
7. Realizar el cambio mínimo necesario.
8. Ejecutar pruebas.
9. No reescribir módulos completos innecesariamente.

No eliminar funcionalidades existentes salvo que la especificación lo requiera.

---

# 48. Objetivo final

Construir una primera versión funcional, moderna, segura y escalable de:

ContaCloud

utilizando:

Java
Spring Boot
Spring Security
Spring Data JPA
Vaadin
PostgreSQL
Flyway

con arquitectura:

                    ContaCloud
                        │
               ┌────────┴────────┐
               │                 │
            Vaadin          Spring Boot
               │                 │
               └────────┬────────┘
                        │
                   PostgreSQL
                        │
                    conta_cloud
                        │
              ┌─────────┴─────────┐
              │                   │
          Empresa A           Empresa B
              │                   │
          empresa_id           empresa_id

La prioridad es construir una base administrativa, fiscal y contable sólida antes de incorporar módulos operativos.

El orden conceptual de crecimiento será:

FASE 1
Base administrativa + seguridad + configuración fiscal/contable
        ↓
FASE 2
Catálogo contable + motor de asientos
        ↓
FASE 3
Clientes + proveedores + productos/servicios
        ↓
FASE 4
Facturación + ventas
        ↓
FASE 5
Cuentas por cobrar
        ↓
FASE 6
Compras + cuentas por pagar
        ↓
FASE 7
Inventario
        ↓
FASE 8
Bancos y conciliación
        ↓
FASE 9
Reportes financieros y fiscales
        ↓
FASES POSTERIORES
Activos fijos + presupuestos + integraciones + módulos avanzados

Toda nueva funcionalidad debe fortalecer esta arquitectura y no comprometer:

- Multitenancy
- Integridad contable
- Seguridad
- Escalabilidad
- Mantenibilidad
- Trazabilidad
- Experiencia de usuario