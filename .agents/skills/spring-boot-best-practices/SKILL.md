---
name: spring-boot-best-practices
description: Generar y mantener proyectos Spring Boot con Maven, Java 21 y arquitectura por capas controller/service/repository/model. Usar cuando se solicite crear una API básica de Spring Boot, un monolito Spring Web, o crear, agregar o modificar una entidad, repository, service o controller de Spring.
---

# Spring Boot Best Practices

## Creación obligatoria con Spring Initializr

- Para crear un proyecto Spring Boot desde cero, usar obligatoriamente [Spring Initializr](https://start.spring.io), la herramienta oficial de Spring, para generar la estructura base. No crear manualmente el `pom.xml`, el Maven Wrapper ni la estructura inicial equivalente.
- Consultar primero los metadatos de `https://start.spring.io/metadata/client` y usar el endpoint oficial de generación de Initializr para descargar el proyecto generado. Seleccionar la versión estable de Spring Boot que Initializr publique y que sea compatible con Java 21; no usar versiones preview ni `SNAPSHOT` salvo petición expresa.
- Generar un proyecto con Maven, Java, empaquetado `jar`, `application.properties` y el grupo, artifactId, nombre, paquete y dependencias establecidos en este skill. Incluir en la solicitud de Initializr: Spring Web, Validation, Spring Data JPA, H2 Database, Spring Boot DevTools y Spring Boot Actuator. Conservar la dependencia de pruebas que Initializr añade por defecto.
- Extraer el proyecto generado en el directorio raíz del workspace. Tras la generación, revisar el resultado y hacer únicamente los ajustes posteriores necesarios para cumplir las demás reglas de este skill.

## Configuración obligatoria

- Usar la versión estable oficial de Spring Boot que Spring Initializr haya seleccionado y que sea compatible con Java 21; no usar versiones preview o `SNAPSHOT` salvo petición expresa.
- Configurar Java 21, Maven, empaquetado `jar` y `application.properties`. No crear `application.yml` ni `application.yaml`.
- Usar el groupId `com.citacloud.springboot`.
- Derivar el artifactId y nombre del proyecto del directorio raíz del workspace. Si el nombre no cumple las reglas de Maven, conservarlo cuando sea posible y explicar la normalización necesaria. Para paquetes Java, convertir el artifactId en un identificador Java válido en minúsculas (por ejemplo, `my-app` a `myapp`).
- Usar como paquete raíz `com.citacloud.springboot.<artifactIdNormalizado>.app` y poner la clase `@SpringBootApplication` en dicho paquete raíz.
- Incluir las dependencias base: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, H2, `spring-boot-devtools` y `spring-boot-starter-actuator`. Incluir `spring-boot-starter-test` para pruebas.
- Generar y conservar Maven Wrapper: `.mvn/`, `mvnw` y `mvnw.cmd`. Para iniciar localmente, preferir siempre `./mvnw -DskipTests spring-boot:run`; en PowerShell usar `./mvnw.cmd -DskipTests spring-boot:run` si el script Unix no es ejecutable.

## Estructura de capas

Organizar el código bajo el paquete raíz:

```text
app/
  <ArtifactId>Application.java
  models/
  dto/
  mappers/
  repositories/
  services/
  controllers/
```

- Crear entidades JPA en `models`.
- Crear repositorios como interfaces en `repositories` que extiendan `JpaRepository<Entity, IdType>`.
- Colocar las reglas de negocio y la coordinación de repositorios en `services`; no exponer entidades JPA desde esta capa.
- Exponer en `controllers` únicamente endpoints REST. Validar las entradas con Bean Validation, delegar en servicios y devolver DTOs con códigos HTTP adecuados.
- Crear un mapper explícito por entidad en `mappers`. Debe proporcionar `Entity -> DTO` y `DTO -> Entity`; el servicio debe usarlo para toda conversión.
- Definir DTOs como `record` en `dto`. Usar el mismo DTO para request y response cuando resulte apropiado. Nunca incluir `password`, `created_at`, `updated_at`, `create_at` ni `update_at`, ni sus equivalentes Java (`password`, `createdAt`, `updatedAt`, `createAt`, `updateAt`).
- Mantener controladores pequeños; no introducir lógica de negocio, consultas de repositorio ni mapeo de entidades allí.

## API REST

Al crear o modificar una API, seguir esta secuencia:

1. Ajustar la entidad, sus restricciones y el repositorio.
2. Definir o actualizar el record DTO y el mapper explícito.
3. Implementar la lógica de caso de uso en el servicio, incluidas las excepciones de recurso no encontrado.
4. Crear o ajustar los endpoints REST con `@Valid` para el body y restricciones para parámetros.
5. Añadir pruebas proporcionales al cambio y ejecutar Maven Wrapper.

No exponer campos sensibles ni devolver directamente entidades JPA.

## Monolito con Spring Web

Cuando se soliciten páginas web renderizadas por el servidor o un monolito Spring Web, añadir Thymeleaf y Tailwind CSS. Mantener una estructura de vistas reutilizable:

```text
src/main/resources/templates/
  layouts/base.html
  fragments/header.html
  fragments/footer.html
  <vistas>.html
```

Construir las páginas con HTML semántico: `header`, `main` y `footer`; incluir navbar, footer y contenido principal centrado en el layout compartido. Reutilizar fragmentos de Thymeleaf y evitar duplicar el marco visual entre vistas.
- Definir el `header`, el `main` y el `footer` en `layouts/base.html`. Cada vista debe pasar al layout únicamente un fragmento de contenido, por ejemplo un `section`; no debe reemplazar el elemento `main` del layout ni repetir el header o footer.

## Verificación

Antes de entregar, comprobar que se cumple lo siguiente:

- Si se creó un proyecto, la base proviene de Spring Initializr y conserva los archivos generados, incluido Maven Wrapper.
- `pom.xml` usa la versión estable oficial de Spring Boot seleccionada por Initializr, Java 21, Maven y `jar`.
- Sólo existe `application.properties` para la configuración de la aplicación.
- Están presentes las dependencias base solicitadas y el Maven Wrapper.
- Los paquetes, DTOs records, mappers y capas respetan las reglas anteriores.
- Ejecutar `./mvnw test` cuando sea viable; para levantar la aplicación, usar el comando de inicio preferido indicado arriba.
