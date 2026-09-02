$ErrorActionPreference = 'Continue'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.microservices.yml'
docker compose -f $composeFile -p hangyin-microservices down
