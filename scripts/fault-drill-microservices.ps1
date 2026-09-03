[CmdletBinding()]
param(
    [string]$ComposeFile = '',
    [string]$ProjectName = 'hangyin-microservices',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path $PSScriptRoot '..\docker-compose.microservices.yml'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\evidence\fault-drill'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidence = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose -f $ComposeFile -p $ProjectName @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
}

function Save-Request {
    param([string]$Name, [string]$Uri, [hashtable]$Headers = @{})
    try {
        $response = Invoke-WebRequest -Uri $Uri -Headers $Headers -UseBasicParsing
        [pscustomobject]@{ name = $Name; status = [int]$response.StatusCode; body = $response.Content } |
            ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $evidence "$Name.json")
    } catch {
        [pscustomobject]@{ name = $Name; error = $_.Exception.Message } |
            ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $evidence "$Name.json")
    }
}

Invoke-Compose @('ps') | Out-File -Encoding UTF8 (Join-Path $evidence 'before.txt')
Save-Request 'before-recommendation' 'http://127.0.0.1:8080/api/videos/recommend?page=1&pageSize=5' @{ 'X-User-Id' = '1' }

# user-service is a personalization dependency. video-service must continue with guest/hot ranking.
Invoke-Compose @('stop', 'user-service')
Invoke-Compose @('ps') | Out-File -Encoding UTF8 (Join-Path $evidence 'user-service-stopped.txt')
Save-Request 'user-service-down-recommendation' 'http://127.0.0.1:8080/api/videos/recommend?page=1&pageSize=5' @{ 'X-User-Id' = '1' }
Save-Request 'user-service-down-live' 'http://127.0.0.1:8080/api/live/rooms'
Invoke-Compose @('logs', '--no-color', 'video-service') | Out-File -Encoding UTF8 (Join-Path $evidence 'video-service-fallback.log')
Invoke-Compose @('up', '-d', 'user-service')

# Restore the dependency and record recovery evidence.
Start-Sleep -Seconds 5
Invoke-Compose @('ps') | Out-File -Encoding UTF8 (Join-Path $evidence 'after-recovery.txt')
Save-Request 'after-recovery-recommendation' 'http://127.0.0.1:8080/api/videos/recommend?page=1&pageSize=5' @{ 'X-User-Id' = '1' }

Write-Host "Fault drill evidence written to $evidence"
