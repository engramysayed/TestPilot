@echo off
REM Phase 0a (company machine): start the portal and the engine in two separate console windows.
REM Close a window (or press Ctrl+C in it) to stop that part. Nothing is installed as a service.
setlocal
cd /d "%~dp0"
start "Keel portal :8080" cmd /k ""%~dp0start-portal.cmd""
echo Waiting for the portal on http://localhost:8080 ...
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
echo Portal: http://localhost:8080   ^|   two windows opened: "Keel portal :8080" and "Keel engine".
endlocal
