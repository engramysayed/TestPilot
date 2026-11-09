@echo off
setlocal
cd /d "%~dp0\.."
call mvn -q -pl keel-portal -am -DskipTests exec:java "-Dexec.mainClass=delivery.portal.cli.KeelPortalCli" "-Dexec.args=%*"
