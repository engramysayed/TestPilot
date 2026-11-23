@echo off
setlocal

call mvn -q clean test -Dmaven.test.failure.ignore=true %*
if errorlevel 1 (
  echo Test run failed. Generating report from whatever results exist...
)

call mvn -q io.qameta.allure:allure-maven:report
if errorlevel 1 (
  echo Report generation failed.
  exit /b 1
)

echo.
echo Allure report generated at: test-output\target\site\allure-report\index.html
echo Open it in your browser, or serve it with: mvn io.qameta.allure:allure-maven:serve
endlocal
