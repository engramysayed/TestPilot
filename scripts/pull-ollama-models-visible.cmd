@echo off
setlocal
set "OLLAMA_MODELS=D:\Ollama\models"
title Keel Ollama pulls (D: models)
echo OLLAMA_MODELS=%OLLAMA_MODELS%
echo.

powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('localhost', 11434); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if errorlevel 1 (
  echo Ollama is not running on :11434.
  echo Start scripts\start-ollama-keel.cmd in another window, then run this script again.
  pause
  exit /b 1
)

echo [1/4] Community UI-TARS ^(official ui-tars:latest tag is gone from Ollama^)
call ollama pull avil/UI-TARS
if errorlevel 1 goto fail

echo [2/4] Alias ui-tars for Keel ^(application.properties expects ui-tars^)
call ollama create ui-tars -f "%~dp0ollama-ui-tars-alias.modelfile"
if errorlevel 1 (
  echo Warning: could not create ui-tars alias; set Vision model to avil/UI-TARS in Admin.
)

echo [3/4] Chat model
call ollama pull qwen2.5:latest
if errorlevel 1 goto fail

echo [4/4] Vision assert ^(works on your 8GB GPU; slower on CPU^)
call ollama pull qwen2.5vl:7b
if errorlevel 1 goto fail

echo.
ollama list
echo.
echo Done. Keel: Admin -^> LLM Settings -^> Vision model: ui-tars or qwen2.5vl:7b -^> Refresh -^> Save.
pause
exit /b 0

:fail
echo.
echo Pull failed. Check: Ollama window running, free space on D:, CorpLink/VPN/proxy.
pause
exit /b 1
