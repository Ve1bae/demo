$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.user-service-e2e.yml'
$composeArgs = @('-f', $composeFile, '-p', 'user-service-local')
$reportDirectory = Join-Path $PSScriptRoot '..\user-service\target\deployment-reports'
$reportPath = Join-Path $reportDirectory 'user-service-local-status.md'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null

function Get-EndpointResult([string] $path) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:18082$path" -TimeoutSec 5
        $body = [System.Text.Encoding]::UTF8.GetString([System.Byte[]]$response.Content)
        return "HTTP $($response.StatusCode)`n$body"
    } catch {
        return "FAILED`n$($_.Exception.Message)"
    }
}

$status = (& docker compose @composeArgs ps 2>&1 | Out-String).Trim()
$logs = (& docker compose @composeArgs logs --no-color --tail 100 user-service 2>&1 | Out-String).Trim()
$health = Get-EndpointResult '/actuator/health'
$readiness = Get-EndpointResult '/actuator/health/readiness'
$info = Get-EndpointResult '/actuator/info'

$content = @(
    '# user-service local deployment status'
    ''
    "- Collected: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
    '- Service URL: http://127.0.0.1:18082'
    ''
    '## Container status'
    ''
    '```'
    $status
    '```'
    ''
    '## Liveness check'
    ''
    '```'
    $health
    '```'
    ''
    '## Readiness check'
    ''
    '```'
    $readiness
    '```'
    ''
    '## Version information'
    ''
    '```'
    $info
    '```'
    ''
    '## Last 100 service log lines'
    ''
    '```'
    $logs
    '```'
) -join "`n"

Set-Content -Path $reportPath -Value $content -Encoding utf8
Write-Host "Deployment status report: $reportPath"
