$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

docker compose -f .\docker-compose.srs.yml up -d
docker ps --filter "name=hangyin-srs"
