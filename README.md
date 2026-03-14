# CreativeIA Backend

API Spring Boot para autenticación, proyectos por usuario, generación de imágenes, biblioteca por proyecto y serving local de assets.

Este backend quedó preparado para trabajar con el frontend Angular actual en:

- autenticación JWT stateless
- proyectos por usuario con ownership real
- generación por flow
- catálogo remoto para el Studio
- assets buscables por metadata
- tests con H2, sin depender de PostgreSQL local

## 1. Stack

- Java 21
- Spring Boot 3.5.5
- Spring Security
- Spring Data JPA
- PostgreSQL
- H2 para tests
- WebFlux `WebClient` para integración con ComfyUI

## 2. Módulos principales

```text
src/main/java/com/ryn/creativeai
  adapters/
    flow/
    provider/
    storage/
    templates/
  api/
    controller/
    dto/
    exception/
  config/
  core/
    application/
    domain/
    ports/
  infra/
  security/
```

### Archivos importantes

- `security/AuthService.java`: register/login/me
- `security/JwtService.java`: emisión y parseo de JWT
- `security/JwtAuthFilter.java`: autenticación por bearer token
- `api/controller/ProjectsController.java`: proyectos y assets
- `api/controller/GenerateController.java`: creación de jobs
- `api/controller/JobsController.java`: estado, result y SSE
- `api/controller/StudioCatalogController.java`: catálogo remoto del Studio
- `core/application/usecase/CreateGenerationJobUseCase.java`: orchestration principal de generación
- `adapters/provider/ComfyUIAdapter.java`: integración con ComfyUI
- `adapters/provider/MockImageProviderAdapter.java`: fallback/mock provider
- `adapters/storage/LocalStorageAdapter.java`: persistencia local de assets

## 3. Requisitos

- JDK 21
- Maven Wrapper (`mvnw` / `mvnw.cmd`)
- PostgreSQL 14+ para desarrollo real
- ComfyUI local si quieres generación real

## 4. Configuración

Archivo base:

- `src/main/resources/application.properties`

El proyecto ya no depende de secretos ni paths hardcodeados en el repo. Todo se puede configurar por variables de entorno.

### Variables principales

```properties
SERVER_PORT=8080

DB_URL=jdbc:postgresql://localhost:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=postgres

JPA_DDL_AUTO=update
JPA_SHOW_SQL=false

STORAGE_ASSETS_DIR=./assets

COMFY_BASE_URL=http://127.0.0.1:8188
COMFY_INPUT_DIR=./ComfyUI/input
COMFY_DOWNLOAD_DIR=./tmp/comfy
GENERATION_FALLBACK_TO_MOCK=true

APP_JWT_SECRET=change_me_with_a_secret_of_at_least_32_chars
APP_JWT_EXPIRES_MINUTES=120
APP_PUBLIC_BASE_URL=http://localhost:8080
APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:4200,http://localhost:4200
```

### Perfiles

- `application.properties`: base portable
- `application-dev.properties`: tuning mínimo de desarrollo
- `src/test/resources/application-test.properties`: H2 + paths de test

## 5. Ejecución local

### Levantar backend

```powershell
./mvnw.cmd spring-boot:run
```

### Build

```powershell
./mvnw.cmd -q clean test
```

## 6. Modelo de dominio

### User

- email único
- password hash con BCrypt
- role (`USER`)

### Project

- pertenece a un `owner`
- nombre validado
- unicidad lógica por owner y nombre

### Job

- pertenece a un proyecto
- guarda template, provider, payload compilado, estado, progreso y metadata del flow

### Asset

- pertenece a `job` y `project`
- guarda:
  - `url`
  - `displayName`
  - `prompt`
  - `flow`
  - `width`
  - `height`

## 7. Seguridad

### Auth

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `GET /v1/auth/me`

### Reglas activas

- JWT stateless vía `Authorization: Bearer <token>`
- `/v1/auth/**` público
- `/assets/**` público para permitir `<img src="...">`
- el resto requiere autenticación

### Ownership

El backend valida ownership real en:

- proyectos
- assets por proyecto
- detalle de job
- creación de jobs

Un usuario autenticado ya no puede crear jobs sobre proyectos de otro usuario usando un UUID ajeno.

## 8. Endpoints

### Auth

#### `POST /v1/auth/register`

```json
{
  "email": "user@example.com",
  "password": "12345678"
}
```

Respuesta:

```json
{
  "token": "jwt",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "role": "USER"
  }
}
```

#### `POST /v1/auth/login`

Mismo formato que register.

#### `GET /v1/auth/me`

Devuelve usuario autenticado.

### Catálogo Studio

#### `GET /v1/catalog/studio-options`

Respuesta:

```json
{
  "styles": ["Ninguno", "Realismo"],
  "brands": [
    {
      "name": "Hyundai",
      "products": ["Ninguno", "Kona"]
    }
  ],
  "mockupTemplates": ["Remera", "Cartel"]
}
```

### Proyectos

#### `POST /v1/projects`

```json
{
  "name": "Campania Marzo"
}
```

Validaciones:

- requerido
- 2 a 120 caracteres
- único por usuario

#### `GET /v1/projects?page=0&size=50`

Devuelve `Page<ProjectResponse>`.

### Assets por proyecto

#### `GET /v1/projects/{projectId}/assets?page=1&size=100&search=...`

Respuesta:

```json
{
  "items": [
    {
      "id": "uuid",
      "url": "/assets/uuid-imagen.png",
      "flow": "txt2img",
      "createdAt": "2026-03-13T23:00:00Z",
      "displayName": "imagen.png",
      "prompt": "clean studio product shot",
      "width": 1024,
      "height": 1024
    }
  ],
  "page": 1,
  "size": 100,
  "total": 1
}
```

La búsqueda aplica sobre:

- `url`
- `flow`
- `displayName`
- `prompt`

### Generación

#### `POST /v1/generate`

Soporta:

- JSON
- multipart con:
  - `payload`
  - `image`

Ejemplo JSON:

```json
{
  "projectId": "uuid",
  "flow": "txt2img",
  "prompt": "studio lighting, premium product shot",
  "width": 768,
  "height": 768,
  "batch": 1,
  "style": "Realismo",
  "brand": "Hyundai",
  "product": "Kona"
}
```

### Jobs

#### `GET /v1/jobs/{id}`

Respuesta:

```json
{
  "id": "uuid",
  "status": "QUEUED",
  "flow": "txt2img",
  "progress": 0,
  "phase": "QUEUED",
  "assets": [],
  "error": null
}
```

#### `GET /v1/jobs/{id}/result`

Redirige al mejor asset si el job ya terminó.

#### `GET /v1/jobs/{id}/events`

SSE opcional para tiempo real.

#### `GET /v1/jobs/project/{projectId}`

Lista resumida de jobs por proyecto.

## 9. Pipeline de generación

1. El controlador recibe request JSON o multipart.
2. `FlowDispatcher` resuelve el adapter correcto.
3. `CreateGenerationJobUseCase`:
   - valida ownership del proyecto
   - resuelve catálogo LoRA
   - compila template + schema
   - crea el job
   - dispara procesamiento async
4. Provider:
   - `comfyui`
   - `mock`
5. Storage:
   - copia el resultado a `assets/`
   - calcula dimensiones
   - devuelve URL pública local
6. Se persiste `Asset`.

## 10. Política de assets

### Serving

Los assets se sirven bajo:

- `GET /assets/**`

Esto está habilitado para que el frontend pueda usar `<img src="...">` sin pedir blobs ni headers especiales.

### Seguridad actual

- `imageUrls` externas arbitrarias ya no se descargan server-side.
- sólo se permiten `imageUrls` relativas a `/assets/...` o absolutas al mismo backend.
- esto evita SSRF en el flujo `img2img` / `upscale` / `mockup`.

## 11. Tests

### Suite actual

- `CreativeaiApplicationTests`
- `ProjectSecurityIntegrationTest`

### Qué validan

- arranque del contexto con perfil `test`
- uso de H2 en tests
- bloqueo de creación de jobs sobre proyectos ajenos
- rechazo de nombres de proyecto duplicados por usuario

### Ejecutar

```powershell
./mvnw.cmd -q clean test
```

## 12. Integración con el frontend

El frontend esperado consume:

- `GET /v1/catalog/studio-options`
- `GET /v1/projects`
- `POST /v1/projects`
- `GET /v1/projects/{id}/assets`
- `POST /v1/generate`
- `GET /v1/jobs/{id}`

También espera que:

- `status` sea `QUEUED | RUNNING | DONE | FAILED`
- `phase` exista
- los assets usen `url` utilizable en `<img>`

## 13. Notas operativas

- `assets/` y `tmp/` están ignorados en git.
- `target/` se limpia con `clean`.
- el storage local añade nombres únicos para evitar colisiones.
- el catálogo del Studio hoy vive en backend y no en el frontend.

## 14. Troubleshooting

### `401` desde frontend

- revisar `APP_JWT_SECRET`
- verificar que el token no esté expirado
- revisar header `Authorization`

### Assets no aparecen

- revisar `STORAGE_ASSETS_DIR`
- verificar permisos de escritura
- revisar que `APP_PUBLIC_BASE_URL` coincida con el host real

### ComfyUI falla

- revisar `COMFY_BASE_URL`
- revisar `COMFY_INPUT_DIR`
- si `GENERATION_FALLBACK_TO_MOCK=true`, el backend puede caer al provider mock para testing

### Tests no levantan

- confirmar que el perfil activo de test sea `test`
- correr `./mvnw.cmd -q clean test`
- verificar que `src/test/resources/application-test.properties` siga presente
