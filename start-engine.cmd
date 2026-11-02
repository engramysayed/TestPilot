@echo off
REM Phase 0a (company machine): start the Keel engine manually. It talks to the portal only over
REM http://localhost:8080 with the runner token (KEEL_RUNNER_TOKEN, tp_run_...). No new ports, services,
REM certificates or proxy. Engine scratch data lives in its own folder (KEEL_ENGINE_HOME).
REM B07: runs delivery.runner.EngineAgent (Runner API v2, token + HMAC; Execute and Convert jobs in 0a).
REM Fallback: set KEEL_ENGINE_LEGACY=1 to run the old v1 PrivateRunnerAgent (Execute only).
setlocal
cd /d "%~dp0"

if exist "%~dp0keel-env.local.bat" call "%~dp0keel-env.local.bat"
if exist "%~dp0api-keys.local.bat" call "%~dp0api-keys.local.bat"
if "%KEEL_PORTAL_URL%"=="" set "KEEL_PORTAL_URL=http://localhost:8080"
if "%KEEL_ENGINE_HOME%"=="" set "KEEL_ENGINE_HOME=%USERPROFILE%\Keel\engine"
if "%KEEL_RUNNER_DRY_RUN%"=="" set "KEEL_RUNNER_DRY_RUN=false"
if "%KEEL_RUNNER_TOKEN%"=="" (
  echo Set KEEL_RUNNER_TOKEN to the tp_run_ token from project Settings ^(e.g. in api-keys.local.bat^).
  exit /b 2
)
if not exist "%KEEL_ENGINE_HOME%\work" mkdir "%KEEL_ENGINE_HOME%\work"

title Keel engine -^> %KEEL_PORTAL_URL%
echo Keel engine: portal %KEEL_PORTAL_URL%, work dir %KEEL_ENGINE_HOME%\work, dry-run %KEEL_RUNNER_DRY_RUN%
if "%KEEL_ENGINE_LEGACY%"=="1" (
  echo Using the legacy v1 private runner agent ^(KEEL_ENGINE_LEGACY=1^).
  call mvn -q -Dmaven.compiler.release=21 -pl keel-portal -am -DskipTests exec:java "-Dexec.mainClass=delivery.runner.PrivateRunnerAgent" "-Dexec.args=--portal %KEEL_PORTAL_URL% --work-dir %KEEL_ENGINE_HOME%\work --dry-run %KEEL_RUNNER_DRY_RUN%"
) else (
  REM -am pulls mobile-contracts (standalone, no parent); those modules set exec.skip=true so only keel-portal runs.
  REM keel-portal mainClass is ${exec.mainClass} so -Dexec.mainClass overrides DeliveryCli default.
  call mvn -q -Dmaven.compiler.release=21 -pl keel-portal -am -DskipTests exec:java "-Dexec.mainClass=delivery.runner.EngineAgent" "-Dexec.args=--keel.engine.portal-url=%KEEL_PORTAL_URL% --keel.engine.home=%KEEL_ENGINE_HOME% --keel.engine.dry-run=%KEEL_RUNNER_DRY_RUN%"
)
endlocal
