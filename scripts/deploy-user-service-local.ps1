$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.user-service-e2e.yml'
$composeArgs = @('-f', $composeFile, '-p', 'user-service-local')

docker compose @composeArgs up -d --build
if ($LASTEXITCODE -ne 0) { throw 'Local user-service deployment failed.' }

for ($attempt = 1; $attempt -le 60; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:18082/actuator/health' -TimeoutSec 3
        if ($health.status -eq 'UP') {
            & (Join-Path $PSScriptRoot 'inspect-user-service-local.ps1')
            Write-Host 'user-service is deployed locally: http://127.0.0.1:18082'
            exit 0
        }
    } catch {
        if ($attempt -eq 60) { throw 'Local user-service health check timed out.' }
        Start-Sleep -Seconds 2
    }
}
