[CmdletBinding()]
param(
    [string]$ComposeFile = '',
    [string]$ProjectName = 'hangyin-microservices'
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path $PSScriptRoot '..\docker-compose.microservices.yml'
}
foreach ($name in @('MYSQL_ROOT_PASSWORD', 'MYSQL_VIDEO_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
        throw "Set $name before seeding browser E2E data."
    }
}

function Invoke-Seed {
    param([string]$Service, [string]$Database, [string]$Password, [string]$SqlPath)
    Get-Content -Raw -Encoding UTF8 $SqlPath |
        docker compose -f $ComposeFile -p $ProjectName exec -T -e "MYSQL_PWD=$Password" $Service mysql --default-character-set=utf8mb4 -uroot $Database
    if ($LASTEXITCODE -ne 0) { throw "Failed to seed $Database." }
}

Invoke-Seed 'mysql-user' 'user_db' $env:MYSQL_ROOT_PASSWORD (Join-Path $PSScriptRoot '..\infra\mysql\user-browser-e2e.sql')
Invoke-Seed 'mysql-video' 'video_db' $env:MYSQL_VIDEO_PASSWORD (Join-Path $PSScriptRoot '..\video-service\sql\005_browser_e2e_test_data.sql')
Write-Host 'Microservices browser E2E data seeded.'
