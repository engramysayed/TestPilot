# Live tunnel broker smoke (local portal :8080).
# Prerequisites: JDK 21, mvn package, python 3 on PATH.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$UpstreamPort = 19521
$PortalPort = 8080
$User = if ($env:KEEL_USER) { $env:KEEL_USER } else { "admin@testpilot.local" }
$Pass = if ($env:KEEL_PASSWORD) { $env:KEEL_PASSWORD } else { "ChangeMeAdmin1!" }
$Base = "http://127.0.0.1:$PortalPort"
$Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${User}:${Pass}"))

function Invoke-KeelApi {
    param([string]$Method, [string]$Path, [string]$Body = $null)
    $headers = @{
        "Authorization" = "Basic $Auth"
        "X-Keel-Requested-With" = "Keel"
        "Accept" = "application/json"
    }
    if ($Body) {
        $headers["Content-Type"] = "application/json"
        return Invoke-WebRequest -Uri "$Base$Path" -Method $Method -Headers $headers -Body $Body -UseBasicParsing
    }
    return Invoke-WebRequest -Uri "$Base$Path" -Method $Method -Headers $headers -UseBasicParsing
}

Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match 'keel-portal-1.0-SNAPSHOT' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

Write-Host "==> Building portal jar (skip tests)"
mvn -q -pl keel-portal -am -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

$jar = Join-Path $Root "keel-portal\target\keel-portal-1.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) { throw "Missing $jar" }

Write-Host "==> Starting python upstream on $UpstreamPort"
$upstream = Start-Process -FilePath "python" -ArgumentList "-m","http.server",$UpstreamPort `
    -WorkingDirectory $Root -PassThru -WindowStyle Hidden

Write-Host "==> Starting portal with tunnel enabled"
$store = Join-Path $Root "target\tunnel-live-smoke-store"
$work = Join-Path $Root "target\tunnel-live-smoke-work"
New-Item -ItemType Directory -Force -Path $store, $work | Out-Null
$portalArgs = @(
    "-jar", $jar,
    "--server.port=$PortalPort",
    "--spring.profiles.active=default",
    "--spring.datasource.url=jdbc:h2:mem:tunnel-live-smoke;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "--spring.jpa.hibernate.ddl-auto=create-drop",
    "--delivery.admin-email=$User",
    "--delivery.admin-password=$Pass",
    "--delivery.tunnel.enabled=true",
    "--delivery.tunnel.public-base-url=$Base",
    "--delivery.dry-run=true",
    "--delivery.store-root=$store",
    "--delivery.work-dir=$work"
)
$portalLog = Join-Path $Root "target\tunnel-live-smoke-portal.log"
$portal = Start-Process -FilePath "java" -ArgumentList $portalArgs -WorkingDirectory $Root `
    -RedirectStandardOutput $portalLog -RedirectStandardError (Join-Path $Root "target\tunnel-live-smoke-portal.err.log") -PassThru -WindowStyle Hidden

try {
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $h = Invoke-WebRequest -Uri "$Base/api/health" -UseBasicParsing -TimeoutSec 2
            if ($h.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) {
        if (Test-Path $portalLog) { Get-Content $portalLog -Tail 40 }
        throw "Portal did not become healthy on $Base (see $portalLog)"
    }

    Write-Host "==> Create project"
    $projRes = Invoke-KeelApi -Method POST -Path "/api/projects" -Body '{"name":"TunnelSmoke","baseUrl":"https://example.com/"}'
    $projectId = ($projRes.Content | ConvertFrom-Json).projectId

    Write-Host "==> Start keel tunnel agent (background; enrolls + heartbeats)"
    $env:KEEL_PORTAL_URL = $Base
    $env:KEEL_USER = $User
    $env:KEEL_PASSWORD = $Pass
    $agent = Start-Process -FilePath "cmd.exe" -ArgumentList "/c","scripts\keel-portal-cli.cmd","tunnel","start","--project",$projectId,"--target","http://127.0.0.1:$UpstreamPort","--listen","4041" `
        -WorkingDirectory $Root -PassThru -WindowStyle Hidden

    Start-Sleep -Seconds 12

    $badgeRes = Invoke-KeelApi -Method GET -Path "/api/projects/$projectId/tunnel/badge"
    $badge = $badgeRes.Content | ConvertFrom-Json
    $token = $badge.token
    if (-not $token) { throw "tunnel badge missing token (is agent running?)" }
    if ($badge.status -ne "active") { throw "tunnel not active yet (status=$($badge.status))" }

    Write-Host "==> GET public relay (token $token)"
    $relayUrl = "$Base/tunnel/p/$token/"
    $proxy = Invoke-WebRequest -Uri $relayUrl -UseBasicParsing -TimeoutSec 120
    if ($proxy.StatusCode -ne 200) { throw "relay status $($proxy.StatusCode)" }
    Write-Host "OK: relay returned $($proxy.Content.Length) bytes (expect HTML index listing)"
    Write-Host "LIVE_TUNNEL_SMOKE=PASS project=$projectId token=$token"
    exit 0
} finally {
    Write-Host "==> Cleanup"
    if ($agent -and -not $agent.HasExited) { Stop-Process -Id $agent.Id -Force -ErrorAction SilentlyContinue }
    if ($portal -and -not $portal.HasExited) { Stop-Process -Id $portal.Id -Force -ErrorAction SilentlyContinue }
    if ($upstream -and -not $upstream.HasExited) { Stop-Process -Id $upstream.Id -Force -ErrorAction SilentlyContinue }
}
