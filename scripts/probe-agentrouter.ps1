# Probe AgentRouter without printing the API key
$root = Split-Path -Parent $PSScriptRoot
$keyFile = Join-Path $root "api-keys.local.bat"
if (-not (Test-Path $keyFile)) {
    Write-Output "NO_KEY_FILE"
    exit 2
}
$line = Get-Content $keyFile | Where-Object { $_ -match 'AGENTROUTER_API_KEY=' } | Select-Object -First 1
if (-not $line) {
    Write-Output "NO_KEY_LINE"
    exit 2
}
$key = ($line -replace '.*AGENTROUTER_API_KEY=', '' -replace '"', '').Trim()
if ([string]::IsNullOrWhiteSpace($key) -or $key -eq 'PASTE_AGENTROUTER_KEY_HERE') {
    Write-Output "NO_KEY"
    exit 2
}
Write-Output ("KEY_PRESENT=true KEY_LEN=" + $key.Length)

$headers = @{
    "x-api-key"         = $key
    "anthropic-version" = "2023-06-01"
    "Content-Type"      = "application/json"
}

function Show-HttpError($ex, $label) {
    if ($ex.ErrorDetails -and $ex.ErrorDetails.Message) {
        Write-Output ($label + "_BODY=" + $ex.ErrorDetails.Message)
    }
    $resp = $ex.Exception.Response
    if ($resp -ne $null) {
        Write-Output ($label + "_STATUS=" + [int]$resp.StatusCode)
        try {
            $stream = $resp.GetResponseStream()
            if ($stream -ne $null) {
                $sr = New-Object System.IO.StreamReader($stream)
                $body = $sr.ReadToEnd()
                if ($body -and $body.Length -gt 0) {
                    if ($body.Length -gt 600) { $body = $body.Substring(0, 600) }
                    Write-Output ($label + "_BODY2=" + $body)
                }
            }
        } catch {
            Write-Output ($label + "_READ_ERR=" + $_.Exception.Message)
        }
    } else {
        Write-Output ($label + "_ERROR=" + $ex.Exception.Message)
    }
}

Write-Output "--- GET /v1/models ---"
try {
    $r = Invoke-WebRequest -Uri "https://agentrouter.org/v1/models" -Headers $headers -Method GET -TimeoutSec 45
    Write-Output ("MODELS_STATUS=" + [int]$r.StatusCode)
    $c = $r.Content
    if ($c.Length -gt 800) { $c = $c.Substring(0, 800) }
    Write-Output $c
} catch {
    Show-HttpError $_ "MODELS"
}

Write-Output "--- POST /v1/messages (claude-opus-5 ping) ---"
$body = @{
    model      = "claude-opus-5"
    max_tokens = 32
    messages   = @(@{ role = "user"; content = "Reply with exactly: OK" })
} | ConvertTo-Json -Depth 5

try {
    $r2 = Invoke-WebRequest -Uri "https://agentrouter.org/v1/messages" -Headers $headers -Method POST -Body $body -TimeoutSec 90
    Write-Output ("MSG_STATUS=" + [int]$r2.StatusCode)
    $c2 = $r2.Content
    if ($c2.Length -gt 800) { $c2 = $c2.Substring(0, 800) }
    Write-Output $c2
} catch {
    Show-HttpError $_ "MSG"
}
