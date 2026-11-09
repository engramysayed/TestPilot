@echo off
REM Refresh Graphify code graph (requires: py -3.12 -m pip install graphifyy)
cd /d "%~dp0\.."
py -3.12 -m graphify update . --force
if errorlevel 1 exit /b 1
echo Open graphify-out\graph.html or read graphify-out\GRAPH_REPORT.md
exit /b 0
