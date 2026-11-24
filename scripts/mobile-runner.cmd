@echo off
REM Mobile runner agent: claims mobile runs from the portal and drives the Appium session the portal started.
REM Needs KEEL_PORTAL_URL (default http://127.0.0.1:8080).
REM The portal writes delivery-store\mobile-runner.token when you open an Android project.
if not "%KEEL_RUNNER_TOKEN%"=="" goto :havetoken
if exist "delivery-store\mobile-runner.token" set /p KEEL_RUNNER_TOKEN=<delivery-store\mobile-runner.token
:havetoken
if "%KEEL_RUNNER_TOKEN%"=="" (
  echo Open the Android project in the portal so Keel can enroll a runner.
  exit /b 2
)
REM Install mobile-runner and its sibling modules (mobile-contracts) so the standalone run can resolve them.
call mvn -q -pl mobile-runner -am install -DskipTests || exit /b 1
call mvn -q -f mobile-runner/pom.xml exec:java -Dexec.skip=false -Dexec.mainClass=delivery.mobile.runner.agent.MobileRunnerAgent -Dexec.args="%*"
