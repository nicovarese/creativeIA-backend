# CreativeIA Backend

Backend API para autenticación, proyectos por usuario, generación de imágenes (jobs) y biblioteca de assets.

## 1. Stack

- Java 21
- Spring Boot 3.5.5
- Spring Security (JWT stateless)
- Spring Data JPA + PostgreSQL
- WebFlux client (`WebClient`) para integración con ComfyUI

## 2. Qué resuelve hoy

- Auth:
  - `POST /v1/auth/register`
  - `POST /v1/auth/login`
  - `GET /v1/auth/me`
- Ownership por usuario autenticado:
  - Proyectos
  - Jobs
  - Assets por proyecto
- Generación:
  - `POST /v1/generate` (JSON o multipart)
  - polling de estado de job
  - SSE opcional por job

## 3. Requisitos

- JDK 21 instalado
- PostgreSQL 14+ (probado en 16)
- Maven Wrapper (`mvnw` / `mvnw.cmd`)

## 4. Configuración local

Archivo: `src/main/resources/application.properties`

Variables principales:

- `server.port=8080`
- `spring.datasource.url=jdbc:postgresql://localhost:5432/postgres`
- `spring.datasource.username=postgres`
- `spring.datasource.password=1234`
- `spring.jpa.hibernate.ddl-auto=update`
- `app.jwt.secret=<secret de al menos 32 chars>`
- `app.jwt.expiresMinutes=120`
- `comfy.baseUrl=http://127.0.0.1:8188`
- `comfy.inputDir=<ruta real a ComfyUI/input>`
- `comfy.downloadDir=./tmp/comfy`
- `storage.assetsDir=./assets`

Notas:

- En `dev` se permite CORS para `http://127.0.0.1:4200` y `http://localhost:4200`.
- Si no levantás ComfyUI, el flujo de generación real puede fallar según provider activo.

## 5. Levantar proyecto

Windows (PowerShell):

```powershell
./mvnw spring-boot:run
```

Build sin tests:

```powershell
./mvnw -DskipTests package
```

## 6. Modelo funcional (resumen)

- `User` autentica por JWT.
- `Project` pertenece a un `owner` (`User`).
- `Job` pertenece a un `Project` y guarda estado/progreso.
- `Asset` pertenece a `Job` y `Project`.

## 7. Endpoints principales

### Auth

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `GET /v1/auth/me`

### Proyectos

- `GET /v1/projects`
- `POST /v1/projects`
- `GET /v1/projects/{projectId}/assets?page=1&size=24&search=...`

### Generación

- `POST /v1/generate` (JSON)
- `POST /v1/generate` (multipart, partes: `payload`, `image`)

### Jobs

- `GET /v1/jobs/{id}`
- `GET /v1/jobs/{id}/result`
- `GET /v1/jobs/{id}/events` (SSE)
- `GET /v1/jobs/project/{projectId}`

## 8. Contrato de estado de job

`GET /v1/jobs/{id}` devuelve:

- `id`
- `status` (`QUEUED | RUNNING | DONE | FAILED`)
- `flow`
- `progress` (0..100)
- `phase` (`QUEUED | PREPARING | GENERATING | STORING | DONE | FAILED`)
- `assets[]`
- `error`

## 9. Seguridad

- Stateless JWT (`Authorization: Bearer <token>`).
- `401` cuando no hay autenticación válida.
- `403` cuando hay autenticación pero no permisos.
- Todo protegido salvo:
  - `/v1/auth/**`
  - Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) si está habilitado
  - `OPTIONS` (preflight CORS)

## 10. Troubleshooting rápido

- Error `Unrecognized 'hibernate.hbm2ddl.auto'`:
  - Verificar `spring.jpa.hibernate.ddl-auto=update`.
- Error de ownership por columna:
  - Verificar que exista `projects.owner_id` y FK a `users(id)`.
- 401 desde frontend:
  - Confirmar token en `Authorization` y que no esté expirado.
- CORS preflight:
  - Confirmar origin exacto (`127.0.0.1:4200` o `localhost:4200`).

## 11. Siguiente estándar recomendado

- Migraciones versionadas (Flyway/Liquibase).
- DTOs de respuesta para `Project` (evitar exponer entidad JPA directa).
- Trazabilidad de jobs (request id, métricas, tiempos por fase).
