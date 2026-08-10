@echo off
setlocal
cd /d "%~dp0"

if exist "%~dp0api-keys.local.bat" (
  call "%~dp0api-keys.local.bat"
)

if not "%AGENTROUTER_API_KEY%"=="" if /I not "%AGENTROUTER_API_KEY%"=="PASTE_AGENTROUTER_KEY_HERE" (
  echo AGENTROUTER_API_KEY is set for this window ^(value not printed^).
) else (
  echo AGENTROUTER_API_KEY not set — Client delivery final revise will skip Opus until configured.
)

if not "%CURSOR_API_KEY%"=="" if /I not "%CURSOR_API_KEY%"=="PASTE_CURSOR_KEY_HERE" (
  echo CURSOR_API_KEY is set for this window ^(value not printed^).
)

echo Starting TestPilot Delivery Portal...
echo.

REM Kill anything still listening on 8080 so this JVM loads fresh classes.
for /f "tokens=5" %%p in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
  echo Stopping PID %%p on port 8080...
  taskkill /PID %%p /F >nul 2>&1
)
timeout /t 2 /nobreak >nul

echo Open http://localhost:8080  (admin from application.properties)
echo Ollama: keep the Ollama app/service running in the background.
echo   Health check (optional, not required every time):
echo   curl.exe http://127.0.0.1:11434/api/tags
echo See docs\ops\portal-restart.md
echo.
mvn -Dmaven.compiler.release=21 spring-boot:run
endlocal
