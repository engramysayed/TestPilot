param(
    [Parameter(Mandatory=$true)][string]$StoreRoot,
    [Parameter(Mandatory=$true)][string]$TenantId,
    [Parameter(Mandatory=$true)][string]$SiteFolder
)
# Explicit operator assignment only. Never call automatically for a tenant job.
$ErrorActionPreference = 'Stop'
if ($TenantId -notmatch '^ws_[a-f0-9]{32}$') { throw 'Invalid tenant ID' }
if ($SiteFolder -notmatch '^[a-z0-9][a-z0-9-]*$') { throw 'Invalid site folder' }
$root = (Resolve-Path -LiteralPath $StoreRoot).Path
$directory = Get-Content -LiteralPath (Join-Path $root 'tenants/directory.json') -Raw | ConvertFrom-Json
if ($null -eq $directory.tenants.PSObject.Properties[$TenantId]) { throw 'Unknown workspace' }
$source = Join-Path $root "$SiteFolder/domain-locator-memory.json"
$destination = Join-Path $root "tenants/$TenantId/sites/$SiteFolder/domain-locator-memory.json"
if (!(Test-Path -LiteralPath $source)) { throw 'No legacy memory file to migrate' }
$legacy = Get-Content -LiteralPath $source -Raw | ConvertFrom-Json
if ($null -eq $legacy.entries) { throw 'Invalid legacy memory' }
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
$lock = [IO.File]::Open("$destination.lock", 'OpenOrCreate', 'ReadWrite', 'None')
try {
    $current = if (Test-Path -LiteralPath $destination) {
        Get-Content -LiteralPath $destination -Raw | ConvertFrom-Json
    } else { [pscustomobject]@{entries=@()} }
    if ($null -eq $current.entries) { throw 'Invalid destination memory' }
    $entries = [ordered]@{}
    foreach ($entry in @($legacy.entries) + @($current.entries)) {
        if ($null -eq $entry) { continue }
        $key = "$($entry.host)`n$($entry.path)`n$($entry.intentKey)"
        $entries[$key] = $entry # Existing tenant entries win conflicts.
    }
    $backup = Join-Path $root ("locator-memory-migrations/" + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($backup) | Out-Null
    Copy-Item -LiteralPath $source -Destination (Join-Path $backup 'legacy.json')
    if (Test-Path -LiteralPath $destination) {
        Copy-Item -LiteralPath $destination -Destination (Join-Path $backup 'tenant-before.json')
    }
    $temp = "$destination.$([guid]::NewGuid().ToString('N')).tmp"
    try {
        $json = @{entries=@($entries.Values)} | ConvertTo-Json -Depth 20
        [IO.File]::WriteAllText($temp, $json, [Text.UTF8Encoding]::new($false))
        [IO.File]::Move($temp, $destination, $true)
    } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
    # Retire the old active file; backup above remains available for recovery.
    Remove-Item -LiteralPath $source
    @{tenantId=$TenantId; source=$source; destination=$destination; count=$entries.Count;
      completedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding utf8
    Write-Output "Consolidated $($entries.Count) entries. Backup: $backup"
} finally { $lock.Dispose() }
