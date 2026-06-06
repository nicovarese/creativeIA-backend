# Brand LoRA training real con ai-toolkit (Ostris)

Esta guía conecta tu backend al trainer real de Ostris. Mientras no hagas estos pasos,
el sistema sigue funcionando con el `StubTrainingExecutor` (simulación de 30s, safetensors
placeholder).

## 1. Requisitos de hardware

| Componente | Mínimo | Recomendado |
|---|---|---|
| GPU | RTX 3090 / 4070 Ti / A4500 (16 GB) | RTX 4090 (24 GB) |
| VRAM libre durante training | 14 GB con quantize=true | 22 GB |
| Disco libre | 80 GB | 200 GB (modelos + checkpoints) |
| RAM sistema | 16 GB | 32 GB |
| Driver CUDA | 12.4+ | 12.4+ |

**Importante**: durante un training, la GPU queda **completamente ocupada**. El backend
serializa generación de imagen ↔ training con un mutex (`GpuMutex`), así que cuando entrenás:
las generaciones de imagen quedan encoladas y arrancan al terminar. No hay forma de evitar
esto con una sola GPU.

## 2. Setup automático

Desde la raíz del repo backend:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-ai-toolkit.ps1
```

El script:
- chequea Python 3.10+ y git
- clona `ostris/ai-toolkit` en `./ai-toolkit/`
- crea un venv en `./ai-toolkit/venv/`
- instala PyTorch con CUDA 12.4 (10–20 min)
- instala dependencias de ai-toolkit (5–15 min)

## 3. Descargar el modelo base

Esto se hace una sola vez. Son **~24 GB** y requiere cuenta gratuita de Hugging Face.

```powershell
.\ai-toolkit\venv\Scripts\python.exe -m pip install -U "huggingface_hub[cli]"
huggingface-cli login
huggingface-cli download black-forest-labs/FLUX.2-dev
```

El modelo queda cacheado en `%USERPROFILE%\.cache\huggingface\`. ai-toolkit lo encuentra
automáticamente por el `name_or_path` del config.

## 4. Activar el trainer real en el backend

Tres formas, elegí una:

### A. Variables de entorno (sesión actual)

```powershell
$env:AITOOLKIT_ENABLED       = 'true'
$env:AITOOLKIT_REPO_PATH     = 'C:\Users\natal\IdeaProjects\creativeIA\ai-toolkit'
$env:AITOOLKIT_PYTHON_PATH   = 'C:\Users\natal\IdeaProjects\creativeIA\ai-toolkit\venv\Scripts\python.exe'
./mvnw spring-boot:run
```

### B. Editar `application.properties`

Cambiá las 3 líneas correspondientes:

```properties
creativeai.training.aitoolkit.enabled=true
creativeai.training.aitoolkit.repoPath=C:/Users/natal/IdeaProjects/creativeIA/ai-toolkit
creativeai.training.aitoolkit.pythonPath=C:/Users/natal/IdeaProjects/creativeIA/ai-toolkit/venv/Scripts/python.exe
```

### C. Perfil `local`

Creá `src/main/resources/application-local.properties` con esas 3 líneas y arrancá con
`-Dspring.profiles.active=local`. Conviene porque no contaminás el `application.properties`
que está en git.

## 5. ¿Cómo verificar que está activo?

Al bootear vas a ver en logs:

```
StubTrainingExecutor   -> NO bootea (ConditionalOnProperty=false)
AiToolkitTrainingExecutor -> bootea OK
```

Si bootean los DOS, algo está mal con el flag. Solo uno puede estar activo a la vez.

Probá rápido:

```bash
curl -s http://localhost:8080/v1/brand-loras
```

→ `401` significa "vivo, security OK". Listo.

## 6. Entrenar tu primera LoRA

Desde la UI (Header → "Mis marcas") creá un training con:

- **Nombre**: lo que veás en el select de marcas (ej. "Café Aurora")
- **Trigger word**: palabra inventada, snake_case (ej. `aurora_brand`). NO uses el nombre
  real de la marca como trigger word.
- **Tipo de producto**: la categoría del sujeto (ej. `soda bottle`, `running shoe`). Mejora
  mucho la calidad de los captions automáticos.
- **Dataset**: 15–60 imágenes (mínimo 5 por validación, pero menos de 15 da resultados
  pobres). Variá ángulos, fondos e iluminación.

El backend:
1. Persiste el dataset en `./training/datasets/<id>/`
2. Genera un caption `.txt` por imagen con el template:
   `"A <triggerWord> <productType>, professional product photography, photorealistic"`
3. Renderiza un YAML config en `./training/output/_configs/<name>.yaml` desde la plantilla
   `templates/training/brand_lora.yaml.template`
4. Lanza `python ai-toolkit/run.py <config>` como subprocess
5. Parsea la línea `step X/Y` del stdout para actualizar progreso
6. Al terminar, copia el último `.safetensors` a `./training/loras/`
7. Marca el BrandLora como `COMPLETED` y queda disponible en el select del Studio

Tiempo esperado en RTX 4090: **45–90 min** para 3000 steps. En 3090: ~1.5× eso.

## 7. Captioning per-imagen (opcional, mejora calidad)

El MVP usa el mismo caption para todas las imágenes del dataset. Si querés captions
distintos por imagen (recomendado para mejor fidelidad), después de subir el dataset:

1. Detener el training si ya arrancó (DELETE desde la UI)
2. Editar los `.txt` que están en `./training/datasets/<brandLoraId>/`
3. Re-crear el training desde la UI (vas a tener que volver a subir las imágenes — esta
   parte se mejora en el roadmap con un editor de captions integrado en el modal)

## 8. Troubleshooting

| Síntoma | Causa probable | Fix |
|---|---|---|
| `BeanCreationException AiToolkitTrainingExecutor` | falta `repoPath` o `pythonPath` | poné los paths absolutos correctos en properties/env |
| `RuntimeException ai-toolkit terminó con exit code 1` | error en el subprocess | mirá logs del backend, buscá líneas `[ai-toolkit <id>]` para ver stdout completo |
| OOM (Out of memory) | VRAM insuficiente | bajá `linear` a 16 en la plantilla, o usá Flux.2 Klein 4B en vez de Dev |
| Progress se queda en 1% | regex no matchea | ver patrón `STEP_RE` en `AiToolkitTrainingExecutor.java`, puede que tu versión de ai-toolkit imprima distinto |
| `GPU lock timeout` en generación | hay un training de muchas horas en curso | esperá o subí `creativeai.training.gpuTimeoutHours` |

## 9. Volver al stub

Si querés desarrollar UI sin esperar trainings reales:

```powershell
$env:AITOOLKIT_ENABLED='false'
./mvnw spring-boot:run
```

El `StubTrainingExecutor` vuelve a arrancar (es la implementación por default cuando
`aitoolkit.enabled` no está o es `false`).
