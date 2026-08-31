param(
    [string]$MySqlRootPassword = "mkj162829",

    [Parameter(Mandatory = $true)]
    [string]$MinioRootPassword,

    [string]$MinioRootUser = "minioadmin",
    [switch]$SkipBackendBuild,
    [switch]$KeepInfrastructure
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$infrastructureStarted = $false

function Invoke-CheckedCommand {
    param([string]$Description, [scriptblock]$Command)

    Write-Host "`n==> $Description"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Wait-ForInfrastructure {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $mysqlStatus = docker inspect --format '{{.State.Health.Status}}' hangyin-mysql 2>$null
        $minioStatus = docker inspect --format '{{.State.Health.Status}}' hangyin-minio 2>$null
        if ($mysqlStatus -eq "healthy" -and $minioStatus -eq "healthy") {
            return
        }
        Start-Sleep -Seconds 2
    }

    docker compose ps
    throw "MySQL or MinIO did not become healthy within two minutes."
}

try {
    # Credentials stay in this process and are not stored in the repository.
    $env:MYSQL_ROOT_PASSWORD = $MySqlRootPassword
    $env:SPRING_DATASOURCE_USERNAME = "root"
    $env:SPRING_DATASOURCE_PASSWORD = $MySqlRootPassword
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3307/hangyin_video?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    $env:MINIO_ROOT_USER = $MinioRootUser
    $env:MINIO_ROOT_PASSWORD = $MinioRootPassword
    $env:MINIO_ACCESS_KEY = $MinioRootUser
    $env:MINIO_SECRET_KEY = $MinioRootPassword
    $env:MINIO_ENDPOINT = "http://127.0.0.1:9000"
    $env:VIDEO_TRANSCODE_ENABLED = "false"

    Invoke-CheckedCommand "Running object-level unit tests" {
        Push-Location $backendDir
        try {
            .\mvnw.cmd -B -Dtest="VideoControllerRecommendUnitTest,VideoRecommendationServiceUnitTest,VideoServiceImplRecommendationEdgeTest,VideoServiceImplTest,UC04Test`$UnitTest,UC05Test`$UnitTest,UC06Test`$UnitTest,LiveUnitTest,Uc07CreateRoomServiceTest,Uc07CreateRoomControllerTest" test
        } finally {
            Pop-Location
        }
    }

    $composeArgs = @("up", "-d")
    if (-not $SkipBackendBuild) {
        $composeArgs += "--build"
    } else {
        $composeArgs += "--no-build"
    }
    $composeArgs += @("mysql", "minio", "srs", "backend")
    try {
        Invoke-CheckedCommand "Starting MySQL, MinIO, SRS, and backend" { docker compose @composeArgs }
    } catch {
        if (-not $SkipBackendBuild -and $_.Exception.Message -match "certificate|x509|unknown authority|tls") {
            throw "Docker cannot pull the backend build image because its registry certificate is not trusted. Start/fix the Docker Desktop proxy or certificate, then rerun. If hangyin/backend:latest already exists locally, rerun with -SkipBackendBuild. Original error: $($_.Exception.Message)"
        }
        throw
    }
    $infrastructureStarted = $true
    Write-Host "Waiting for MySQL and MinIO..."
    Wait-ForInfrastructure

    Invoke-CheckedCommand "Seeding use-case system-test data" {
        Get-Content (Join-Path $repoRoot "frontend\demo\e2e\fixtures\recommendation.sql") -Raw |
            docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" hangyin_video'
    }

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:8080/api/live/rooms" -TimeoutSec 2 | Out-Null
            break
        } catch {
            if ($attempt -eq 60) {
                docker compose logs backend --tail 100
                throw "Backend container did not become ready within two minutes."
            }
            Start-Sleep -Seconds 2
        }
    }

    Invoke-CheckedCommand "Running UC-04/05/06 use-case system tests" {
        node (Join-Path $PSScriptRoot "video-e2e.mjs")
    }
    Invoke-CheckedCommand "Running UC-07 use-case system tests" {
        node (Join-Path $PSScriptRoot "uc07-e2e.mjs")
    }

    Write-Host "`nObject-level unit tests and backend-container system tests passed."
} finally {
    if ($infrastructureStarted -and -not $KeepInfrastructure) {
        docker compose down -v
    }
}
