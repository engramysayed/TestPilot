@echo off
REM Ollama with models on D: (matches user env OLLAMA_MODELS).
set "OLLAMA_MODELS=D:\Ollama\models"
if not exist "%OLLAMA_MODELS%" mkdir "%OLLAMA_MODELS%"
title Ollama serve — models on %OLLAMA_MODELS%
echo OLLAMA_MODELS=%OLLAMA_MODELS%
echo Listening on http://127.0.0.1:11434 — close this window to stop Ollama.
echo.
"%LOCALAPPDATA%\Programs\Ollama\ollama.exe" serve
