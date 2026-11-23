#!/usr/bin/env bash
set -euo pipefail

mvn -q clean test -Dmaven.test.failure.ignore=true "$@"
mvn -q io.qameta.allure:allure-maven:report

echo ""
echo "Allure report generated at: test-output/target/site/allure-report/index.html"
echo "Open it in your browser, or serve it with: mvn io.qameta.allure:allure-maven:serve"
