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

Incluye el esquema completo de datos de la Fase 1, login multitenant, permisos base, dashboard, datos de empresa, generacion segura de secuencias, manejo uniforme de errores y pruebas unitarias de aislamiento. Los CRUD visuales restantes se incorporan progresivamente sobre esta base, sin adelantar modulos de facturacion, ventas, compras o inventario.
