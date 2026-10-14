@echo off
REM Phase 0a (company machine): start the Keel portal manually on http://localhost:8080.
REM No services, certificates, proxy or firewall changes; nothing is installed.
REM Optional: set KEEL_PORTAL_HOME to keep portal data in its own folder
REM (e.g. %USERPROFILE%\Keel\portal). If unset, data stays in .\delivery-store as today.
setlocal
cd /d "%~dp0"

if exist "%~dp0api-keys.local.bat" call "%~dp0api-keys.local.bat"
REM LD-01: product profile keeps delivery.mobile.enabled=true for live devices.
REM CI reads base application.properties (false) and never sets this profile.
REM Override: set SPRING_PROFILES_ACTIVE= before calling this script (e.g. empty/ci).
if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=product"
if "%KEEL_DB_PASSWORD%"=="" echo NOTE: KEEL_DB_PASSWORD is not set; the dev profile runs with a warning (see docs\ops\backup-restore.md).

set "KEEL_PORT=8080"
set "KEEL_ARGS=--server.port=%KEEL_PORT%"
if not "%KEEL_PORTAL_HOME%"=="" (
  if not exist "%KEEL_PORTAL_HOME%" mkdir "%KEEL_PORTAL_HOME%"
  set "KEEL_ARGS=%KEEL_ARGS% --delivery.store-root=%KEEL_PORTAL_HOME%\delivery-store --delivery.work-dir=%KEEL_PORTAL_HOME%\delivery-work"
)

REM Free port 8080 only if a previous Keel (java.exe) still holds it; never kill other programs.
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%KEEL_PORT% .*LISTENING"') do (
  tasklist /FI "PID eq %%p" /FI "IMAGENAME eq java.exe" | findstr /I "java.exe" >nul && (
    echo Stopping previous Keel java.exe PID %%p on port %KEEL_PORT%...
    taskkill /PID %%p /F >nul 2>&1
  ) || (
    echo Port %KEEL_PORT% is used by another program ^(PID %%p^). Close it and retry.
    exit /b 1
  )
)

title Keel portal :%KEEL_PORT%
echo Starting Keel portal on http://localhost:%KEEL_PORT% ...
set "JAR=%~dp0keel-portal\target\keel-portal-1.0-SNAPSHOT.jar"
if exist "%JAR%" (
  java -jar "%JAR%" %KEEL_ARGS%
) else (
  call mvn -q -Dmaven.compiler.release=21 -pl keel-portal -am -DskipTests spring-boot:run "-Dspring-boot.run.arguments=%KEEL_ARGS%"
)
endlocal
