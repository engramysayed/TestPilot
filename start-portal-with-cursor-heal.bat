@echo off
setlocal
cd /d "%~dp0"

REM =============================================================================
REM Put keys in api-keys.local.bat (gitignored). Copy from:
REM   api-keys.local.bat.example
REM Legacy: cursor-api-key.local.bat still works if present.
REM Get a Cursor key: https://cursor.com/dashboard/integrations
REM =============================================================================

if exist "%~dp0api-keys.local.bat" (
  call "%~dp0api-keys.local.bat"
)
if exist "%~dp0cursor-api-key.local.bat" (
  call "%~dp0cursor-api-key.local.bat"
)

if "%CURSOR_API_KEY%"=="" goto :missing_key
if /I "%CURSOR_API_KEY%"=="PASTE_CURSOR_KEY_HERE" goto :missing_key
if /I "%CURSOR_API_KEY%"=="PASTE_YOUR_KEY_HERE" goto :missing_key

echo.
echo CURSOR_API_KEY is set for this window (value not printed).
echo Heal cascade: DOM -^> Ollama -^> Cursor Auto sidecar.
echo Starting portal...
echo.

call "%~dp0start-portal.bat"
exit /b %ERRORLEVEL%

:missing_key
echo.
echo ============================================================
echo  No Cursor API key found.
echo.
echo  Copy api-keys.local.bat.example to api-keys.local.bat
echo  and set CURSOR_API_KEY there (gitignored).
echo.
echo  OR create cursor-api-key.local.bat from
echo  cursor-api-key.local.bat.example (legacy).
echo ============================================================
echo.
pause
exit /b 1
