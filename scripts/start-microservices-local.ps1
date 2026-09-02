$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.microservices.yml'
docker compose -f $composeFile -p hangyin-microservices up -d --build --remove-orphans mysql-user mysql-video mysql-live minio srs user-service video-service live-service api-gateway
if ($LASTEXITCODE -ne 0) { throw 'Microservices startup failed.' }
# Nginx resolves Docker service names when it starts. Recreate it so stale
# container IPs cannot keep producing 502 after a service/container change.
docker compose -f $composeFile -p hangyin-microservices up -d --force-recreate api-gateway
if ($LASTEXITCODE -ne 0) { throw 'Gateway restart failed.' }
Write-Host 'Frontend: http://127.0.0.1:5173'
Write-Host 'Gateway:  http://127.0.0.1:8080'
Write-Host 'Start frontend separately: Set-Location frontend\demo; npm run dev'
