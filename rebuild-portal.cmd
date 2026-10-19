@echo off
REM Package the Keel portal fat jar. Does not start or restart the portal.
REM Output: keel-portal\target\keel-portal-1.0-SNAPSHOT.jar
REM After this: start-portal.cmd, then Ctrl+F5 in the browser.
setlocal
cd /d "%~dp0"

echo Packaging keel-portal jar (tests skipped)...
call mvn -pl keel-portal -am -Dmaven.compiler.release=21 -DskipTests package
if errorlevel 1 (
  echo Package failed.
  exit /b 1
)

set "JAR=%~dp0keel-portal\target\keel-portal-1.0-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo Maven succeeded but jar is missing: %JAR%
  exit /b 1
)

echo Packed %JAR%
echo This script does not start the portal. Run start-portal.cmd, then Ctrl+F5.
endlocal
exit /b 0
