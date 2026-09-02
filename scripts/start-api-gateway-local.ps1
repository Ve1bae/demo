$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\gateway\docker-compose.yml'
docker compose -f $composeFile -p microdemo-gateway up -d
if ($LASTEXITCODE -ne 0) { throw 'API gateway startup failed.' }
Write-Host 'API gateway is running at http://127.0.0.1:8080'
