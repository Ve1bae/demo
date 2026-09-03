$ErrorActionPreference = 'Continue'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.microservices.yml'
# `down` still interpolates Compose variables, although it does not create containers.
if (-not $env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD = 'stop-only' }
if (-not $env:MYSQL_VIDEO_PASSWORD) { $env:MYSQL_VIDEO_PASSWORD = 'stop-only' }
if (-not $env:MYSQL_LIVE_PASSWORD) { $env:MYSQL_LIVE_PASSWORD = 'stop-only' }
if (-not $env:MINIO_ROOT_PASSWORD) { $env:MINIO_ROOT_PASSWORD = 'stop-only' }
docker compose -f $composeFile -p hangyin-microservices down
