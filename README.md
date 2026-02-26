# CreativeIA Backend

Backend Spring Boot (Java 21) para auth, ownership por usuario, proyectos, jobs y assets.

## Estado Actual (PR1 + base para PR2)

- Auth JWT:
  - `POST /v1/auth/register`
  - `POST /v1/auth/login`
  - `GET /v1/auth/me`
- Seguridad stateless:
  - endpoints protegidos por default
  - `401` no autenticado
  - `403` sin permisos
- Ownership:
  - `GET /v1/projects` lista solo proyectos del usuario
  - `POST /v1/projects` crea proyecto con owner autenticado
  - `GET /v1/projects/{projectId}/assets` valida owner
  - `GET /v1/jobs/project/{projectId}` valida owner
  - `GET /v1/jobs/{id}` valida owner
- CORS dev:
  - `http://127.0.0.1:4200`
  - `http://localhost:4200`

## Requisitos

- Java 21
- Maven Wrapper (`mvnw.cmd`)
- PostgreSQL local

## Configuración local

Archivo: `src/main/resources/application.properties`

- `server.port=8080`
- `spring.datasource.url=jdbc:postgresql://localhost:5432/postgres`
- `spring.datasource.username=postgres`
- `spring.datasource.password=1234`

## Migración mínima requerida para PR1

Si la DB ya existía antes del campo owner, asegurate de tener `projects.owner_id`:

```sql
BEGIN;

ALTER TABLE projects
  ADD COLUMN IF NOT EXISTS owner_id uuid;

UPDATE projects p
SET owner_id = u.id
FROM (
  SELECT id
  FROM users
  ORDER BY created_at ASC
  LIMIT 1
) u
WHERE p.owner_id IS NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_projects_owner'
  ) THEN
    ALTER TABLE projects
      ADD CONSTRAINT fk_projects_owner
      FOREIGN KEY (owner_id) REFERENCES users(id);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_projects_owner_id ON projects(owner_id);

ALTER TABLE projects
  ALTER COLUMN owner_id SET NOT NULL;

COMMIT;
```

## Build

```bash
./mvnw.cmd -DskipTests package
```

## Run

```bash
./mvnw.cmd spring-boot:run
```

Backend queda en `http://localhost:8080`.

## Endpoints útiles para validación manual

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `GET /v1/auth/me` (Bearer token)
- `GET /v1/projects` (Bearer token)
- `POST /v1/projects` (Bearer token)
- `GET /v1/projects/{projectId}/assets` (Bearer token)

## Notas

- ComfyUI no es requisito para validar PR1/PR2 de auth/proyectos/biblioteca.
- Generación de imágenes depende de endpoints de jobs/generate y entorno comfy.

## Resumen de implementación (esta rama)

- PR1:
  - auth JWT con `register/login/me`
  - passwords con BCrypt
  - seguridad stateless con filtro JWT
  - ownership por usuario en proyectos/jobs/assets
  - manejo correcto de status:
    - `401` no autenticado
    - `403` acceso denegado
    - `ResponseStatusException` ya no se transforma en `500`
- CORS dev:
  - orígenes permitidos:
    - `http://127.0.0.1:4200`
    - `http://localhost:4200`
  - métodos: `GET, POST, PUT, DELETE, OPTIONS`
  - headers: `Authorization, Content-Type`
- Nota DB aplicada para PR1:
  - columna `projects.owner_id` + FK/índice (si la base venía previa al cambio de ownership)
