# Private runner installer (Windows)

Enrolls nothing by itself. After an OWNER/ADMIN creates a runner token in project Settings, store it and start the agent outbound to the portal.

```powershell
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Work = Join-Path $env:USERPROFILE "keel-private-runner"
New-Item -ItemType Directory -Force -Path $Work | Out-Null
$Start = Join-Path $Work "start-runner.cmd"
@"
@echo off
if "%KEEL_PORTAL_URL%"=="" set KEEL_PORTAL_URL=http://127.0.0.1:8081
if "%KEEL_RUNNER_TOKEN%"=="" (
  echo Set KEEL_RUNNER_TOKEN to the tp_run_ token from project Settings.
  exit /b 2
)
if "%KEEL_RUNNER_DRY_RUN%"=="" set KEEL_RUNNER_DRY_RUN=true
cd /d "$Root"
call mvn -q -DskipTests exec:java "-Dexec.mainClass=delivery.runner.PrivateRunnerAgent" "-Dexec.args=--portal %KEEL_PORTAL_URL% --token %KEEL_RUNNER_TOKEN% --work-dir $Work --dry-run %KEEL_RUNNER_DRY_RUN%"
"@ | Set-Content -Encoding ascii $Start
Write-Host "Wrote $Start"
Write-Host "Set KEEL_RUNNER_TOKEN then run that script. Auto-update is not provided."
