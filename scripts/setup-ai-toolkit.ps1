# =====================================================================
#  setup-ai-toolkit.ps1
#  Clona ai-toolkit (Ostris), arma venv y instala dependencias.
#  No descarga el modelo Flux.2 Dev — eso te lo deja a vos en el paso final.
#
#  USO (desde la raíz del repo backend creativeIA):
#     powershell -ExecutionPolicy Bypass -File scripts\setup-ai-toolkit.ps1
# =====================================================================

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$aiToolkitDir = Join-Path $repoRoot "ai-toolkit"
$venvDir = Join-Path $aiToolkitDir "venv"
$pythonExe = Join-Path $venvDir "Scripts\python.exe"

Write-Host "==> Repo root: $repoRoot"
Write-Host "==> ai-toolkit target: $aiToolkitDir"

# ---------- 1. Chequear Python 3.10+ ----------
Write-Host ""
Write-Host "==> Chequeando Python..."
$pythonOk = $false
try {
    $v = python --version 2>&1
    if ($v -match "Python (3\.\d+)") {
        $minor = [int]($Matches[1].Split('.')[1])
        if ($minor -ge 10) { $pythonOk = $true; Write-Host "    $v" }
    }
} catch {}
if (-not $pythonOk) {
    Write-Error "Python 3.10+ no encontrado. Instalá Python 3.11 desde https://www.python.org/downloads/ y volvé a correr el script."
    exit 1
}

# ---------- 2. Chequear git ----------
Write-Host ""
Write-Host "==> Chequeando git..."
try { git --version | Out-Host } catch {
    Write-Error "git no encontrado en el PATH."
    exit 1
}

# ---------- 3. Clonar ai-toolkit ----------
Write-Host ""
if (Test-Path $aiToolkitDir) {
    Write-Host "==> ai-toolkit ya existe. Pulleando últimos cambios..."
    Push-Location $aiToolkitDir
    git pull --ff-only
    Pop-Location
} else {
    Write-Host "==> Clonando ostris/ai-toolkit en $aiToolkitDir..."
    git clone https://github.com/ostris/ai-toolkit.git $aiToolkitDir
    Push-Location $aiToolkitDir
    git submodule update --init --recursive
    Pop-Location
}

# ---------- 4. Crear venv ----------
Write-Host ""
if (-not (Test-Path $pythonExe)) {
    Write-Host "==> Creando venv en $venvDir..."
    Push-Location $aiToolkitDir
    python -m venv venv
    Pop-Location
} else {
    Write-Host "==> venv ya existe en $venvDir"
}

# ---------- 5. Instalar requirements ----------
Write-Host ""
Write-Host "==> Actualizando pip..."
& $pythonExe -m pip install --upgrade pip

Write-Host ""
Write-Host "==> Instalando PyTorch con CUDA 12.4 (puede tardar 10-20 min)..."
& $pythonExe -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu124

Write-Host ""
Write-Host "==> Instalando dependencias de ai-toolkit (otros 5-15 min)..."
Push-Location $aiToolkitDir
& $pythonExe -m pip install -r requirements.txt
Pop-Location

# ---------- 6. Resumen ----------
Write-Host ""
Write-Host "============================================================"
Write-Host " ai-toolkit instalado en: $aiToolkitDir"
Write-Host " Python venv:             $pythonExe"
Write-Host "============================================================"
Write-Host ""
Write-Host "PRÓXIMO PASO MANUAL: bajar el modelo base Flux.2 Dev (~24 GB)."
Write-Host ""
Write-Host "Opción A (huggingface-cli, recomendado):"
Write-Host "   $pythonExe -m pip install -U huggingface_hub[cli]"
Write-Host "   huggingface-cli login    # token gratuito de huggingface.co"
Write-Host "   huggingface-cli download black-forest-labs/FLUX.2-dev"
Write-Host ""
Write-Host "Opción B: descarga manual desde https://huggingface.co/black-forest-labs/FLUX.2-dev"
Write-Host ""
Write-Host "Cuando termine, activá el trainer real con:"
Write-Host "   `$env:AITOOLKIT_ENABLED='true'"
Write-Host "   `$env:AITOOLKIT_REPO_PATH='$aiToolkitDir'"
Write-Host "   `$env:AITOOLKIT_PYTHON_PATH='$pythonExe'"
Write-Host "   ./mvnw spring-boot:run"
