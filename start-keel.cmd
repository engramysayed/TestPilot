@echo off
REM Phase 0a (company machine): one-cmd start for secrets, Ollama, Appium, portal, engine.
REM Close a window (or press Ctrl+C in it) to stop that part. Nothing is installed as a service.
REM No network/firewall/VPN/service/cert changes. Does not install Ollama/Appium/SQL.
setlocal
REM LD-01: portal inherits SPRING_PROFILES_ACTIVE=product from start-portal.cmd (mobile on). CI uses base props (mobile off).
cd /d "%~dp0"

if exist "%~dp0keel-env.local.bat" call "%~dp0keel-env.local.bat"

set "SECRETS_STATUS=missing"
set "OLLAMA_STATUS=skipped"
set "APPIUM_STATUS=skipped"

REM --- 1. Local secrets (env for this session + child windows) ---
if exist "%~dp0api-keys.local.bat" (
  call "%~dp0api-keys.local.bat"
  set "SECRETS_STATUS=loaded"
  echo [1/5] Secrets: loaded api-keys.local.bat
) else (
  echo [1/5] WARNING: api-keys.local.bat not found next to this script.
  echo       Copy api-keys.local.bat.example to api-keys.local.bat and set keys.
  echo       Continuing without local secrets...
)

REM --- 2. Ollama (default port 11434) if not already listening ---
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('localhost', 11434); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 (
  set "OLLAMA_STATUS=already_up"
  echo [2/5] Ollama: already listening on :11434 - skip start.
) else (
  where ollama >nul 2>&1
  if errorlevel 1 (
    set "OLLAMA_STATUS=not_on_path"
    echo [2/5] WARNING: Ollama not on PATH and nothing on :11434. Start Ollama yourself if needed.
  ) else (
    start "Keel Ollama" cmd /k ""%~dp0scripts\start-ollama-keel.cmd""
    set "OLLAMA_STATUS=started"
    echo [2/5] Ollama: started window "Keel Ollama" ^(models on D: via OLLAMA_MODELS^).
  )
)

REM --- 3. Appium (KEEL_APPIUM_PORT or default 4723) if not already listening ---
if "%KEEL_APPIUM_PORT%"=="" set "KEEL_APPIUM_PORT=4723"
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('localhost', %KEEL_APPIUM_PORT%); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 (
  set "APPIUM_STATUS=already_up"
  echo [3/5] Appium: already listening on :%KEEL_APPIUM_PORT% - skip start.
) else (
  where appium >nul 2>&1
  if not errorlevel 1 (
    start "Keel Appium :%KEEL_APPIUM_PORT%" cmd /k appium
    set "APPIUM_STATUS=started"
    echo [3/5] Appium: started window "Keel Appium :%KEEL_APPIUM_PORT%" ^(appium^).
  ) else (
    where npx >nul 2>&1
    if not errorlevel 1 (
      start "Keel Appium :%KEEL_APPIUM_PORT%" cmd /k npx appium
      set "APPIUM_STATUS=started"
      echo [3/5] Appium: started window "Keel Appium :%KEEL_APPIUM_PORT%" ^(npx appium^).
    ) else (
      set "APPIUM_STATUS=not_on_path"
      echo [3/5] WARNING: Neither appium nor npx on PATH; nothing on :%KEEL_APPIUM_PORT%. Start Appium yourself if needed.
    )
  )
)

REM --- 4/5. Portal then engine (existing flow). No local start-sql/docker-compose in this tree. ---
start "Keel portal :8080" cmd /k ""%~dp0start-portal.cmd""
echo [4/5] Portal: started window "Keel portal :8080"; waiting for http://localhost:8080 ...
set /a TRIES=0
:wait
set /a TRIES+=1
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('localhost', 8080); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 goto up
if %TRIES% GEQ 90 (
  echo The portal did not answer within ~3 minutes; starting the engine anyway ^(it retries^).
  goto up
)
timeout /t 2 /nobreak >nul
goto wait
:up
start "Keel engine" cmd /k ""%~dp0start-engine.cmd""
echo [5/5] Engine: started window "Keel engine".

echo.
echo ========== Keel start summary (started vs skipped) ==========
echo   Secrets : %SECRETS_STATUS%
echo   Ollama  : %OLLAMA_STATUS%  ^(port 11434^)
echo   Appium  : %APPIUM_STATUS%  ^(port %KEEL_APPIUM_PORT%^)
echo   Portal  : started  ^(window "Keel portal :8080" -^> http://localhost:8080^)
echo   Engine  : started  ^(window "Keel engine"^)
echo   SQL/DB  : none  ^(no start-sql / docker-compose helper in this repo^)
echo Windows that may be open: Ollama, Appium, portal, engine ^(secrets loaded into this session if present^).
echo =============================================================
endlocal