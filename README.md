# CreativeAI – Backend

Backend **Java 21 + Spring Boot** para generar imágenes a partir de **workflows de ComfyUI** (plantillas JSON).  
Arquitectura **Hexagonal**, validación por **schema**, y proveedores intercambiables (**mock** / **ComfyUI**).

---

## ✨ Features

- Plantillas JSON de ComfyUI con **placeholders** (`{{key}}`) y compilación segura (**TemplateCompiler**).
- **Schemas JSON** por template (defaults, required, types, enum, min/max) con **ParamResolver**.
- Jobs con estados `QUEUED | RUNNING | DONE | FAILED` y assets persistidos.
- **Adapters**: `Mock` (dev) y `ComfyUI` (prod).
- Manejo de errores prolijo con `@ControllerAdvice`.
- Swagger/OpenAPI (solo dev), Flyway, Testcontainers.

---

## 🧱 Arquitectura (Hexagonal)

- **core/domain**: entidades y puertos (interfaces).
- **core/application**: casos de uso y servicios (p.ej. `CreateGenerationJobUseCase`, `TemplateCompiler`,
  `ParamResolver`).
- **adapters/**: implementaciones de puertos (p.ej. `FileTemplateRegistry`, `LocalStorageAdapter`, `MockImageProvider`,
  `ComfyUIAdapter`).
- **api/**: controladores REST y `ApiExceptionHandler`.
- **resources/workflows**: plantillas JSON (ComfyUI)
- **resoruces/schemas**: schemas JSON por plantilla workflows/ # plantillas JSON (ComfyUI)

## 📂 Plantillas & Schemas

- Workflows: `src/main/resources/workflows/`
- Schemas: `src/main/resources/schemas/`
- Convención: `{templateKey}_{version}.json` y `{templateKey}_{version}.schema.json`

Ejemplo:
workflows/flux_simple_v1.json
schemas/flux_simple_v1.schema.json

**Placeholders**

- Numéricos en el JSON como `"{{width}}"` → el compiler los reemplaza por `768` (sin comillas).
- Strings como `"{{prompt}}"`.

---

## 🔌 API (v1)

### POST `/api/generate`

Crea un Job a partir de un template + params.

**Request**

```json
{
  "template": "flux_simple",
  "version": "v1",
  "provider": "mock",
  "params": {
    "prompt": "A Victorian closeup a Scottish woman with a shocked face",
    "width": 768,
    "height": 1024
  }
}
```

**Response 200**

```json
{
  "jobId": "c67f3c56-11e2-4b97-9e84-...",
  "status": "QUEUED"
}
```

### GET `/api/jobs/{id}`

Devuelve el estado y los assets (cuando estén listos).

**Response**

```json
{
  "id": "...",
  "status": "DONE",
  "assets": [
    {
      "url": "/assets/job_123.png",
      "width": 768,
      "height": 1024
    }
  ]
}
```

En dev se sirven archivos estáticos desde ./assets como /assets/**.

## 🚦 Validación & Errores

#DTOs con @Valid (estructura del request).

ParamResolver valida params contra el schema (required, tipos, enum, min/max, additionalProperties).

ApiExceptionHandler traduce:

- IllegalArgumentException → 400 invalid_parameters

- JSON mal formado → 400 malformed_json

- @Valid falló → 400 validation_error

- Error inesperado → 500 internal_error

**Ejemplo 400**

```json
{
  "status": 400,
  "code": "invalid_parameters",
  "message": "Falta parámetro requerido: height"
}
```

# A partir de aca todo esta pendiente de desarrollo (tanto en implementacion como documentacion)

## ⚙️ Configuración

application.yml (ejemplo)

server:
port: 8080

storage:
assetsDir: ./assets

comfy:
baseUrl: http://localhost:8188

spring:
jpa:
hibernate:
ddl-auto: validate

## ▶️ Ejecutar en local

Postgres 16 corriendo.

Flyway corre al levantar.

App:

mvn spring-boot:run

curl de prueba

curl -X POST http://localhost:8080/api/generate \
-H 'Content-Type: application/json' \
-d '{
"template":"flux_simple","version":"v1","provider":"mock",
"params":{"prompt":"hola","width":768,"height":1024}
}'

## 🐘 Base de datos (Flyway)

Migraciones en src/main/resources/db/migration.

Tablas principales:

jobs(id, template_key, template_ver, provider, status, compiled_json, error_message, created_at, updated_at)

assets(id, job_id, url, width, height, created_at)

## 🖼️ Integración con ComfyUI

Instalar/ejecutar ComfyUI (p.ej. http://localhost:8188).

Colocar modelos requeridos por tu workflow.

Usar provider: "comfyui" o perfil prod.

El adapter envía /prompt, hace polling de /history/{prompt_id} y copia imágenes a ./assets.

## 🧪 Tests

Unit: TemplateCompiler, ParamResolver, UseCases.

Integración: Testcontainers (Postgres).

MockMvc: tests de endpoints.

## 🔐 Seguridad (básico)

API Key/JWT por cliente (pendiente).

Rate limiting por tenant (pendiente).

CORS restringido al front.

## 📈 Observabilidad

Actuator: /actuator/health, /actuator/metrics, /actuator/prometheus.

Micrometer + Prometheus (pendiente).

Logs estructurados JSON (pendiente).

## 🗺️ Roadmap corto

Adapter ComfyUI con timeouts/retries y circuit breaker.

Idempotency-Key en POST /api/generate.

Almacenamiento S3/MinIO + CDN.

Multi-tenant (orgs, planes, cuotas).

Webhooks job.completed.

## 📄 Licencia

Privado

