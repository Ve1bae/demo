$ErrorActionPreference = 'Stop'
# Docker writes harmless password warnings to stderr; use its exit code for failure detection.
$PSNativeCommandUseErrorActionPreference = $false
$composeFile = Join-Path $PSScriptRoot '..\docker-compose.user-service-e2e.yml'
$composeArgs = @('-f', $composeFile, '-p', 'user-service-e2e')
$env:USER_SERVICE_E2E_BASE_URL = if ($env:USER_SERVICE_E2E_BASE_URL) { $env:USER_SERVICE_E2E_BASE_URL } else { 'http://127.0.0.1:18082' }
$reportDirectory = Join-Path $PSScriptRoot '..\user-service\target\e2e-reports'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null

docker compose @composeArgs up -d mysql
try {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        docker compose @composeArgs exec -T -e MYSQL_PWD=e2e-password mysql mysqladmin ping -h 127.0.0.1 -uroot --silent 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 60) { throw 'MySQL did not become ready in time.' }
        Start-Sleep -Seconds 2
    }

    Get-Content -Raw -Encoding utf8 infra/mysql/user-db.sql | docker compose @composeArgs exec -T -e MYSQL_PWD=e2e-password mysql mysql --default-character-set=utf8mb4 -uroot
    if ($LASTEXITCODE -ne 0) { throw 'user_db initialization failed.' }
    Get-Content -Raw -Encoding utf8 infra/mysql/migrate-user-db.sql | docker compose @composeArgs exec -T -e MYSQL_PWD=e2e-password mysql mysql --default-character-set=utf8mb4 -uroot
    if ($LASTEXITCODE -ne 0) { throw 'User data migration failed.' }

    docker compose @composeArgs up -d --build user-service
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "$env:USER_SERVICE_E2E_BASE_URL/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { break }
        } catch {
            if ($attempt -eq 60) { throw 'user-service health check timed out.' }
            Start-Sleep -Seconds 2
        }
    }
    node scripts/user-service-e2e.mjs
} finally {
    docker compose @composeArgs logs --no-color user-service | Out-File -Encoding utf8 (Join-Path $reportDirectory 'user-service.log')
    docker compose @composeArgs down -v
}
