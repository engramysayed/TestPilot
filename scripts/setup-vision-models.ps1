# Install / verify local vision models for TestPilot (Ollama).
# Hardware target: ~4GB VRAM (e.g. NVIDIA RTX A2000 Laptop) + 16–32GB RAM.
#
# Usage:
#   pwsh -File scripts/setup-vision-models.ps1
#   pwsh -File scripts/setup-vision-models.ps1 -SkipUiTars
#   pwsh -File scripts/setup-vision-models.ps1 -UiTarsOnly

param(
    [switch]$SkipUiTars,
    [switch]$UiTarsOnly,
    [string]$UiTarsGgufUrl = "https://huggingface.co/bartowski/UI-TARS-2B-SFT-GGUF/resolve/main/UI-TARS-2B-SFT-Q4_K_M.gguf",
    [string]$UiTarsMmprojUrl = "https://huggingface.co/mradermacher/UI-TARS-2B-SFT-GGUF/resolve/main/UI-TARS-2B-SFT.mmproj-fp16.gguf",
    [switch]$ForceRecreateUiTars
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Ensure-Ollama {
    curl.exe -s -f http://127.0.0.1:11434/api/tags | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Starting ollama serve..."
        Start-Process -FilePath "ollama" -ArgumentList "serve" -WindowStyle Minimized
        Start-Sleep -Seconds 5
    }
    curl.exe -s -f http://127.0.0.1:11434/api/tags | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Ollama not reachable on http://127.0.0.1:11434" }
}

function Has-Model([string]$name) {
    $list = ollama list 2>$null | Out-String
    return $list -match [regex]::Escape($name)
}

Ensure-Ollama
Write-Host "Ollama OK"

if (-not $UiTarsOnly) {
    if (Has-Model "qwen2.5vl:3b") {
        Write-Host "qwen2.5vl:3b already installed"
    } else {
        Write-Host "Pulling qwen2.5vl:3b (~3.2GB, vision)..."
        ollama pull qwen2.5vl:3b
    }
}

if (-not $SkipUiTars) {
    $dir = Join-Path $root "models\ui-tars"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $gguf = Join-Path $dir "UI-TARS-2B-SFT-Q4_K_M.gguf"
    $mmproj = Join-Path $dir "UI-TARS-2B-SFT.mmproj-fp16.gguf"
    $needsCreate = $ForceRecreateUiTars -or -not (Has-Model "ui-tars")

    if (-not (Test-Path $gguf)) {
        Write-Host "Downloading UI-TARS-2B Q4 GGUF (fits ~4GB VRAM)..."
        Write-Host "URL: $UiTarsGgufUrl"
        try {
            Invoke-WebRequest -Uri $UiTarsGgufUrl -OutFile $gguf -UseBasicParsing
        } catch {
            Write-Host "Direct GGUF download failed: $($_.Exception.Message)" -ForegroundColor Yellow
            Write-Host "Manual: download a UI-TARS-2B Q4 GGUF into $dir then re-run."
            exit 2
        }
    } else {
        Write-Host "UI-TARS GGUF already on disk"
    }

    if (-not (Test-Path $mmproj)) {
        Write-Host "Downloading UI-TARS mmproj (vision projector, ~1.4GB)..."
        Write-Host "URL: $UiTarsMmprojUrl"
        try {
            Invoke-WebRequest -Uri $UiTarsMmprojUrl -OutFile $mmproj -UseBasicParsing
        } catch {
            Write-Host "mmproj download failed: $($_.Exception.Message)" -ForegroundColor Yellow
            Write-Host "Without mmproj, ui-tars is text-only (no screenshot vision)."
            exit 2
        }
    } else {
        Write-Host "UI-TARS mmproj already on disk"
    }

    if ($needsCreate -or -not (Has-Model "ui-tars")) {
        $modelfile = Join-Path $dir "Modelfile"
        @"
FROM ./UI-TARS-2B-SFT-Q4_K_M.gguf
FROM ./UI-TARS-2B-SFT.mmproj-fp16.gguf
"@ | Set-Content -Path $modelfile -Encoding ascii
        Push-Location $dir
        try {
            Write-Host "Creating Ollama model tag ui-tars (LLM + mmproj)..."
            ollama create ui-tars -f Modelfile
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "ui-tars already installed (pass -ForceRecreateUiTars to rebuild with mmproj)"
    }
}

Write-Host ""
Write-Host "Installed models:"
ollama list
Write-Host ""
Write-Host "Switch in application.properties:"
Write-Host "  delivery.vision.provider=qwen"
Write-Host "  delivery.vision.model=qwen2.5vl:3b"
Write-Host "  # or"
Write-Host "  delivery.vision.provider=uitars"
Write-Host "  delivery.vision.model=ui-tars"
