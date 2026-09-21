# ContaCloud

Base del MVP administrativo, fiscal y contable construida con Java 21, Spring Boot, Vaadin, Spring Security, Spring Data JPA, Flyway y PostgreSQL.

## Requisitos

- JDK 21
- PostgreSQL en `localhost:5432`
- Base de datos existente `conta_cloud`

La aplicacion no crea otra base de datos. Flyway crea y versiona las tablas dentro de `conta_cloud`; Hibernate solo valida el esquema.

## Configuracion

Defina como minimo:

```powershell
$env:DB_PASSWORD="su-password-local"
```

Los logos se guardan como archivos locales, fuera de PostgreSQL. En desarrollo se usa
`./data/contacloud` (ignorado por Git). Para producción configure
`APP_STORAGE_LOCAL_BASE_PATH=/data/contacloud` y monte esa ruta en un volumen persistente
del contenedor; no dependa de su capa efímera. La base solo guarda `logo_object_key`,
con la forma `tenants/{tenantId}/companies/{empresaId}/logos/logo-{uuid}.{ext}`.
El `tenantId` es una identidad estable y distinta del `empresaId`, preparada para
una futura organización con varias empresas por tenant.

Una copia de seguridad y su restauración deben incluir **PostgreSQL y el directorio
completo de archivos**, conservando las rutas relativas de los logos. El repositorio
no contiene Docker Compose; al añadirlo, monte el volumen en la misma ruta configurada.

Para crear el primer tenant y administrador en una instalacion vacia, ejecute una sola vez con estas variables. No se guardan en el repositorio y el bootstrap no modifica una empresa ya existente.

```powershell
$env:BOOTSTRAP_EMPRESA_CODIGO="EMPRESA01"
$env:BOOTSTRAP_EMPRESA_NOMBRE="Empresa Principal"
$env:BOOTSTRAP_USUARIO="admin"
$env:BOOTSTRAP_PASSWORD="una-clave-segura"
```

## Ejecucion

```powershell
./mvnw.cmd spring-boot:run
```

Abra `http://localhost:8080` e ingrese Empresa, Usuario y Contraseña.

## Decisiones de seguridad

- El tenant se obtiene exclusivamente del usuario autenticado.
- Los repositorios de negocio consultan por `empresa_id`.
- Las relaciones sensibles usan claves foraneas compuestas para impedir cruces entre tenants.
- Las contraseñas se almacenan con BCrypt y nunca se exponen en DTOs.
- Las secuencias usan transaccion y `SELECT ... FOR UPDATE`; no usan `MAX + 1`.
- La estructura se administra exclusivamente mediante migraciones Flyway.

## Alcance actual

Incluye el esquema de datos de la Fase 1, login multitenant, permisos base, dashboard,
configuración operativa de la empresa, sucursales, catálogo y monedas habilitadas,
moneda base, logo local, generación segura de secuencias, manejo uniforme de errores
y pruebas de aislamiento. La configuración está disponible desde el menú de usuario en
`/configuracion` y nunca recibe el tenant o la empresa desde el navegador.

Los módulos de facturación, ventas, compras e inventario permanecen fuera del alcance
de esta etapa.
