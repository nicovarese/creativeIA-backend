# CreativeIA Backend

API Spring Boot para autenticacion, proyectos por usuario, generacion de imagenes, biblioteca por proyecto y serving local de assets.

El backend queda listo para arrancar sin preparar PostgreSQL. Por defecto usa H2 persistente en disco y fallback mock para generacion si ComfyUI no esta disponible.

## 1. Stack

- Java 21
- Spring Boot 3.5.5
- Spring Security
- Spring Data JPA
- H2 para desarrollo local inmediato
- PostgreSQL para desarrollo real o despliegue
- WebFlux `WebClient` para integracion con ComfyUI

## 2. Modulos principales

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

## 3. Arranque rapido

Requisitos:

- JDK 21
- Maven Wrapper (`mvnw` / `mvnw.cmd`)

Ejecutar:

```powershell
./mvnw.cmd spring-boot:run
```

Con eso el backend arranca usando:

- base H2 persistente en `./data/creativeai`
- assets en `./assets`
- directorio temporal en `./tmp/comfy`
- JWT local de desarrollo
- fallback mock si ComfyUI falla

## 4. Configuracion por defecto

Archivo base:

- `src/main/resources/application.properties`

Defaults actuales:

```properties
SERVER_PORT=8080

DB_URL=jdbc:h2:file:./data/creativeai;MODE=PostgreSQL;AUTO_SERVER=TRUE
DB_USERNAME=sa
DB_PASSWORD=
DB_DRIVER_CLASS_NAME=org.h2.Driver

JPA_DDL_AUTO=update
JPA_SHOW_SQL=false

H2_CONSOLE_ENABLED=true
H2_CONSOLE_PATH=/h2-console

STORAGE_ASSETS_DIR=./assets

COMFY_BASE_URL=http://127.0.0.1:8188
COMFY_INPUT_DIR=./ComfyUI/input
COMFY_DOWNLOAD_DIR=./tmp/comfy
GENERATION_FALLBACK_TO_MOCK=true

APP_JWT_SECRET=creativeai_local_dev_secret_at_least_32_chars
APP_JWT_EXPIRES_MINUTES=120
APP_PUBLIC_BASE_URL=http://localhost:8080
APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:4200,http://localhost:4200
```

## 5. Usar PostgreSQL en lugar de H2

Si prefieres correr con PostgreSQL real, define estas variables antes de arrancar:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/creativeai"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="tu_password"
$env:DB_DRIVER_CLASS_NAME="org.postgresql.Driver"
./mvnw.cmd spring-boot:run
```

Si luego quieres volver al modo listo-para-usar, cierra la terminal o elimina esas variables y arranca otra vez.

## 6. Perfiles

- `application.properties`: configuracion portable y lista para desarrollo local
- `application-dev.properties`: tuning minimo de desarrollo
- `src/test/resources/application-test.properties`: H2 para tests

## 7. Endpoints principales

### Auth

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `GET /v1/auth/me`

### Catalogo del Studio

- `GET /v1/catalog/studio-options`

### Proyectos

- `POST /v1/projects`
- `GET /v1/projects?page=0&size=50`
- `GET /v1/projects/{projectId}/assets?page=1&size=100&search=...`

### Generacion y jobs

- `POST /v1/generate`
- `GET /v1/jobs/{id}`
- `GET /v1/jobs/{id}/result`
- `GET /v1/jobs/{id}/events`
- `GET /v1/jobs/project/{projectId}`

## 8. Seguridad

- JWT stateless via `Authorization: Bearer <token>`
- `/v1/auth/**` es publico
- `/assets/**` es publico para permitir `<img src="...">`
- el resto requiere autenticacion
- el ownership se valida en proyectos, assets, jobs y generacion
- las `imageUrls` externas arbitrarias ya no se descargan server-side

## 9. Desarrollo local

### H2 Console

Si `H2_CONSOLE_ENABLED=true`, puedes abrir:

- `http://localhost:8080/h2-console`

Valores habituales:

- JDBC URL: `jdbc:h2:file:./data/creativeai`
- User Name: `sa`
- Password: vacio

### Build y tests

```powershell
./mvnw.cmd -q clean test
```

La suite actual usa H2 y no depende de PostgreSQL local.

## 10. Integracion con frontend

El frontend actual consume:

- `GET /v1/catalog/studio-options`
- `GET /v1/projects`
- `POST /v1/projects`
- `GET /v1/projects/{id}/assets`
- `POST /v1/generate`
- `GET /v1/jobs/{id}`

Tambien espera que:

- `status` sea `QUEUED | RUNNING | DONE | FAILED`
- `phase` exista
- los assets tengan `url` utilizable en `<img>`

## 11. Troubleshooting

### La app falla al arrancar por base de datos

Si no configuraste nada, no deberia intentar usar PostgreSQL. Debe arrancar con H2.

Si configuraste PostgreSQL manualmente:

- revisa `DB_URL`
- revisa `DB_USERNAME`
- revisa `DB_PASSWORD`
- revisa `DB_DRIVER_CLASS_NAME=org.postgresql.Driver`

### `401` desde frontend

- revisa `APP_JWT_SECRET`
- verifica que el token no este expirado
- revisa el header `Authorization`

### Assets no aparecen

- revisa `STORAGE_ASSETS_DIR`
- verifica permisos de escritura
- revisa que `APP_PUBLIC_BASE_URL` coincida con el host real

### ComfyUI falla

- revisa `COMFY_BASE_URL`
- revisa `COMFY_INPUT_DIR`
- si `GENERATION_FALLBACK_TO_MOCK=true`, el backend cae al provider mock para pruebas locales
