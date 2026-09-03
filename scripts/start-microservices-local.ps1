param(
    # 日常启动复用已有镜像；需要重新打包时显式传入 -Build。
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.microservices.yml'
$composeArgs = @('-f', $composeFile, '-p', 'hangyin-microservices', 'up', '-d', '--remove-orphans')
if ($Build) {
    $composeArgs += '--build'
}
$composeArgs += @('mysql-user', 'mysql-video', 'mysql-live', 'minio', 'srs', 'user-service', 'video-service', 'live-service', 'api-gateway')
docker compose @composeArgs
if ($LASTEXITCODE -ne 0) { throw 'Microservices startup failed.' }
# Nginx resolves Docker service names when it starts. Recreate it so stale
# container IPs cannot keep producing 502 after a service/container change.
docker compose -f $composeFile -p hangyin-microservices up -d --force-recreate api-gateway
if ($LASTEXITCODE -ne 0) { throw 'Gateway restart failed.' }
Write-Host 'Frontend: http://127.0.0.1:5173'
Write-Host 'Gateway:  http://127.0.0.1:8080'
Write-Host 'Start frontend separately: Set-Location frontend\demo; npm run dev'
