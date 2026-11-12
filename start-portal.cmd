@echo off
REM Phase 0a (company machine): start the Keel portal manually on http://localhost:8080.
REM No services, certificates, proxy or firewall changes; nothing is installed.
REM Optional: set KEEL_PORTAL_HOME to keep portal data in its own folder
REM (e.g. %USERPROFILE%\Keel\portal). If unset, data stays in .\delivery-store as today.
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if exist "%~dp0keel-env.local.bat" call "%~dp0keel-env.local.bat"
if exist "%~dp0api-keys.local.bat" call "%~dp0api-keys.local.bat"
REM LD-01: product profile keeps delivery.mobile.enabled=true for live devices.
REM CI reads base application.properties (false) and never sets this profile.
REM Local http://localhost (no HTTPS guard): in keel-env.local.bat set KEEL_DEV_PORTAL=1
REM   and KEEL_EXTRA_ARGS=--delivery.mobile.enabled=true for Android without product profile.
REM Or set SPRING_PROFILES_ACTIVE explicitly (any value except empty) before this script runs.
if "%KEEL_DEV_PORTAL%"=="1" (
  if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=local"
) else if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=product"
if "%KEEL_DB_PASSWORD%"=="" echo NOTE: KEEL_DB_PASSWORD is not set; the dev profile runs with a warning (see docs\ops\backup-restore.md).

set "KEEL_PORT=8080"
set "KEEL_ARGS=--server.port=%KEEL_PORT%"
if not "%KEEL_PORTAL_HOME%"=="" (
  if not exist "%KEEL_PORTAL_HOME%" mkdir "%KEEL_PORTAL_HOME%"
  set "KEEL_ARGS=%KEEL_ARGS% --delivery.store-root=%KEEL_PORTAL_HOME%\delivery-store --delivery.work-dir=%KEEL_PORTAL_HOME%\delivery-work"
)
if not "%KEEL_EXTRA_ARGS%"=="" set "KEEL_ARGS=%KEEL_ARGS% %KEEL_EXTRA_ARGS%"

REM Free port 8080 only if a previous Keel (java.exe) still holds it; never kill other programs.
REM netstat often lists the same PID twice (0.0.0.0 + [::]); after the first taskkill the second
REM pass must not treat a dead PID as "another program".
set "SEEN_PIDS=;"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%KEEL_PORT% .*LISTENING"') do (
  echo !SEEN_PIDS! | findstr /C:";%%p;" >nul 2>&1
  if errorlevel 1 (
    set "SEEN_PIDS=!SEEN_PIDS!%%p;"
    tasklist /FI "PID eq %%p" /FI "IMAGENAME eq java.exe" 2>nul | findstr /I "java.exe" >nul
    if not errorlevel 1 (
      echo Stopping previous Keel java.exe PID %%p on port %KEEL_PORT%...
      taskkill /PID %%p /F >nul 2>&1
    ) else (
      tasklist /FI "PID eq %%p" 2>nul | findstr /R /C:" %%p " >nul
      if not errorlevel 1 (
        echo Port %KEEL_PORT% is used by another program ^(PID %%p^). Close it and retry.
        exit /b 1
      )
    )
  )
)

REM Also stop any leftover Keel portal JVM that no longer listens but still holds H2 (portal-db.mv.db).
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'keel-portal|PortalApplication' } | ForEach-Object { Write-Host ('Stopping leftover Keel java.exe PID ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" 2>nul
REM Give the OS a moment to release the port and H2 file lock after taskkill.
timeout /t 3 /nobreak >nul

title Keel portal :%KEEL_PORT%
echo Starting Keel portal on http://localhost:%KEEL_PORT% ...
set "JAR=%~dp0keel-portal\target\keel-portal-1.0-SNAPSHOT.jar"
set "USE_JAR="
if exist "%JAR%" (
  REM Only java -jar when MANIFEST has Main-Class or Start-Class (Spring Boot executable).
  set "MF_DIR=%TEMP%\keel-portal-mf-check"
  if exist "%MF_DIR%" rmdir /s /q "%MF_DIR%" >nul 2>&1
  mkdir "%MF_DIR%" >nul 2>&1
  pushd "%MF_DIR%" >nul
  jar xf "%JAR%" META-INF/MANIFEST.MF >nul 2>&1
  findstr /R /I /C:"^Main-Class:" /C:"^Start-Class:" META-INF\MANIFEST.MF >nul 2>&1
  if not errorlevel 1 set "USE_JAR=1"
  popd >nul
  if exist "%MF_DIR%" rmdir /s /q "%MF_DIR%" >nul 2>&1
)
if defined USE_JAR (
  java -jar "%JAR%" %KEEL_ARGS%
) else (
  if exist "%JAR%" echo Jar exists but is not a Spring Boot executable ^(no Main-Class/Start-Class^); falling back to mvn spring-boot:run.
  call mvn -q -Dmaven.compiler.release=21 -pl keel-portal -am -DskipTests spring-boot:run "-Dspring-boot.run.arguments=%KEEL_ARGS%"
)
endlocal
