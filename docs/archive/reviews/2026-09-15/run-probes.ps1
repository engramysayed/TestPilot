$ErrorActionPreference = 'Stop'
$auditRepo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Push-Location $auditRepo
try {
    $auditReportPath = Join-Path $auditRepo 'target/surefire-reports/TEST-TestSuite.xml'
    if (!(Test-Path -LiteralPath $auditReportPath)) {
        throw 'Run a Maven TestNG test first to produce the dependency classpath report.'
    }
    $auditReport = [xml](Get-Content -LiteralPath $auditReportPath -Raw)
    $auditClasspath = ($auditReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    if (!$auditClasspath) { throw 'Missing java.class.path in Maven test report.' }
    & java --class-path $auditClasspath (Join-Path $PSScriptRoot 'LaunchAuditProbe.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launch audit probe failed to execute.' }
    & mvn -B -f customer-framework-template/pom.xml test-compile
    if ($LASTEXITCODE -ne 0) { throw 'Customer template compilation failed.' }
    $auditTemplateClasspath = (Join-Path $auditRepo 'customer-framework-template/test-output/target/classes') + [IO.Path]::PathSeparator + $auditClasspath
    & java --class-path $auditTemplateClasspath (Join-Path $PSScriptRoot 'TemplateAssertionProbe.java')
    if ($LASTEXITCODE -ne 0) { throw 'Template assertion probe failed to execute.' }
} finally {
    Pop-Location
}
