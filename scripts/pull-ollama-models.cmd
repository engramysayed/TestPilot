@echo off
REM Pull chat + vision models referenced by application.properties and Admin LLM defaults.
setlocal
set "OLLAMA_MODELS=D:\Ollama\models"
echo Pulling Ollama models for Keel to %OLLAMA_MODELS% (this can take a while)...
call ollama pull gemma4:e2b
call ollama pull avil/UI-TARS
call ollama create ui-tars -f "%~dp0ollama-ui-tars-alias.modelfile"
call ollama pull qwen2.5:latest
call ollama pull qwen2.5vl:7b
echo Done. Refresh models in the portal: Admin -^> LLM Settings.
endlocal
