$ErrorActionPreference = 'Continue'
$composeFile = Join-Path $PSScriptRoot '..\gateway\docker-compose.yml'
docker compose -f $composeFile -p microdemo-gateway down
