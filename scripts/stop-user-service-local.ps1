$ErrorActionPreference = 'Continue'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.user-service-e2e.yml'
docker compose -f $composeFile -p user-service-local down -v
