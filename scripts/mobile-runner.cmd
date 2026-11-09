@echo off
REM Mobile runner agent: claims mobile runs from the portal and drives the Appium session the portal started.
REM Needs: KEEL_PORTAL_URL (default http://127.0.0.1:8080) and KEEL_RUNNER_TOKEN (enroll a runner on the project).
if "%KEEL_RUNNER_TOKEN%"=="" (
  echo Set KEEL_RUNNER_TOKEN first ^(Projects -^> Runners -^> enroll^).
  exit /b 2
)
REM Install mobile-runner and its sibling modules (mobile-contracts) so the standalone run can resolve them.
call mvn -q -pl mobile-runner -am install -DskipTests || exit /b 1
call mvn -q -f mobile-runner/pom.xml exec:java -Dexec.skip=false -Dexec.mainClass=delivery.mobile.runner.agent.MobileRunnerAgent -Dexec.args="%*"
