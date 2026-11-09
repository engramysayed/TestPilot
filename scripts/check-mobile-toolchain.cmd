@echo off
REM Verifies Android platform-tools (adb) and Appium are installed on this machine.
REM Run from an ordinary terminal; nothing is installed, only checked.

setlocal

set OK=1

echo === adb (Android platform-tools) ===
where adb >NUL 2>&1
if errorlevel 1 (
  echo   MISSING: adb is not on PATH
  echo   Install: winget install Google.PlatformTools, then add ^"%LOCALAPPDATA%\Android\platform-tools^" to PATH
  set OK=0
) else (
  for /f "tokens=* delims=" %%v in ('adb version 2^>^&1') do echo   %%v
  echo   Connected devices:
  adb devices
)

echo.
echo === Appium ===
where node >NUL 2>&1
if errorlevel 1 (
  echo   MISSING: node is not on PATH. Install Node 20+: winget install OpenJS.NodeJS.LTS
  set OK=0
)
where appium >NUL 2>&1
if errorlevel 1 (
  echo   MISSING: appium is not on PATH
  echo   Install: npm install -g appium
  echo   Android driver: appium driver install uiautomator2
  set OK=0
) else (
  for /f "tokens=* delims=" %%v in ('appium -v 2^>^&1') do echo   appium %%v
  echo   Drivers:
  appium driver list --installed 2>NUL
)

echo.
if %OK%==1 (
  echo READY: the portal machine has the mobile runner toolchain.
  exit /b 0
) else (
  echo MISSING PIECES above. Install them before starting the mobile runner.
  exit /b 1
)
