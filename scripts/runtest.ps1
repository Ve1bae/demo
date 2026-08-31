[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [string]$MySqlPassword = 'mkj162829',

    [Parameter(Mandatory = $false)]
    [string]$MinioUser = 'minioadmin',

    [Parameter(Mandatory = $false)]
    [string]$MinioPassword = '3dfbf77d62bf4fd8b7463e62fcea4800dd51f8fc20264f03a1542256ae0da5bc',

    # 仅用于本地测试：删除 MySQL 数据卷并按当前密码重新初始化。
    [switch]$ResetMySqlData
)

# 遇到任何未捕获错误立即终止执行
$ErrorActionPreference = "Stop"

# 脚本位于 scripts 目录，项目根目录是其上一级。
$RootDir = Split-Path -Parent $PSScriptRoot
if (-not $PSScriptRoot) { $RootDir = Get-Location }
$BackendProcess = $null
$BackendLog = Join-Path $RootDir 'backend-test.log'

function Wait-ForBackend {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/api/live/rooms' -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    Get-Content $BackendLog -Tail 100 -ErrorAction SilentlyContinue
    throw '后端未能在两分钟内启动。'
}

function Wait-ForInfrastructure {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $mysqlStatus = docker inspect --format '{{.State.Health.Status}}' hangyin-mysql 2>$null
        $minioStatus = docker inspect --format '{{.State.Health.Status}}' hangyin-minio 2>$null
        if ($mysqlStatus -eq 'healthy' -and $minioStatus -eq 'healthy') {
            return
        }
        Start-Sleep -Seconds 2
    }

    docker compose ps
    throw 'MySQL 或 MinIO 未能在两分钟内就绪。'
}

function Test-MySqlCredentials {
    param([string]$Password)
    docker exec -e "MYSQL_PWD=$Password" hangyin-mysql mysql --protocol=TCP -h 127.0.0.1 -u root -e 'SELECT 1' 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

function Seed-E2eData {
    $fixturePath = Join-Path $RootDir 'frontend\demo\e2e\fixtures\recommendation.sql'
    if (-not (Test-Path $fixturePath)) {
        throw "未找到 E2E 测试数据文件: $fixturePath"
    }

    Write-Host "导入 E2E 测试数据..." -ForegroundColor Cyan
    docker cp $fixturePath 'hangyin-mysql:/tmp/recommendation.sql'
    if ($LASTEXITCODE -ne 0) { throw '复制 E2E 测试数据文件失败。' }
    docker exec -e "MYSQL_PWD=$MySqlPassword" hangyin-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot hangyin_video < /tmp/recommendation.sql'
    if ($LASTEXITCODE -ne 0) { throw '导入 E2E 测试数据失败。' }
}

function Clear-E2eData {
    Write-Host "清理旧的 E2E 测试数据..." -ForegroundColor Cyan
    $cleanupSql = @'
DELETE FROM user_follow WHERE user_id BETWEEN 930001 AND 930004 OR follow_user_id BETWEEN 930001 AND 930004;
DELETE FROM user_interest WHERE user_id BETWEEN 930001 AND 930004;
DELETE FROM view_history WHERE user_id BETWEEN 930001 AND 930004 OR video_id BETWEEN 931001 AND 931005;
DELETE FROM user_video WHERE user_id BETWEEN 930001 AND 930004 OR video_id BETWEEN 931001 AND 931005;
DELETE FROM comment WHERE video_id BETWEEN 931001 AND 931005;
DELETE FROM danmaku WHERE video_url IN ('uc03-e2e-hot', 'uc03-e2e-low', 'uc03-e2e-private', 'uc03-e2e-followed', 'uc03-e2e-music');
DELETE FROM video WHERE id BETWEEN 931001 AND 931005 OR title LIKE 'UC03 E2E %';
DELETE FROM sys_user WHERE id BETWEEN 930001 AND 930004 OR username LIKE 'uc03_e2e_%';
'@
    $cleanupSql | docker exec -i -e "MYSQL_PWD=$MySqlPassword" hangyin-mysql mysql --default-character-set=utf8mb4 -uroot hangyin_video
    if ($LASTEXITCODE -ne 0) { throw '清理旧 E2E 测试数据失败。' }
}

function Reset-MySqlDatabase {
    $mounts = docker inspect hangyin-mysql --format '{{range .Mounts}}{{println .Name .Destination}}{{end}}'
    $volumeName = ($mounts | Where-Object { $_ -match '^(?<name>\S+)\s+/var/lib/mysql$' } |
        ForEach-Object { $Matches['name'] } |
        Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($volumeName)) {
        throw '未找到 MySQL 数据卷，无法重置。'
    }

    Write-Host "重置 MySQL 测试数据卷..." -ForegroundColor Yellow
    docker compose stop mysql
    if ($LASTEXITCODE -ne 0) { throw '停止 MySQL 容器失败。' }

    docker compose rm -f mysql
    if ($LASTEXITCODE -ne 0) { throw '移除 MySQL 容器失败。' }

    docker volume rm $volumeName
    if ($LASTEXITCODE -ne 0) { throw '删除 MySQL 数据卷失败。' }
}

try {
    Write-Host "==> [1/5] 配置环境变量..." -ForegroundColor Cyan
    $env:MYSQL_ROOT_PASSWORD        = $MySqlPassword
    $env:SPRING_DATASOURCE_USERNAME = 'root'
    $env:SPRING_DATASOURCE_PASSWORD = $env:MYSQL_ROOT_PASSWORD
    $env:SPRING_DATASOURCE_URL      = 'jdbc:mysql://127.0.0.1:3307/hangyin_video?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
    $env:MINIO_ROOT_USER            = $MinioUser
    $env:MINIO_ROOT_PASSWORD        = $MinioPassword
    $env:MINIO_ACCESS_KEY           = $env:MINIO_ROOT_USER
    $env:MINIO_SECRET_KEY           = $env:MINIO_ROOT_PASSWORD
    $env:MINIO_ENDPOINT             = 'http://127.0.0.1:9000'
    $env:RUN_UC03_API_TESTS         = 'true'
    $env:E2E_API_BASE               = 'http://127.0.0.1:8080/api'
    $env:E2E_BASE_URL               = 'http://127.0.0.1:5173'

    Write-Host "==> [2/5] 启动 Docker 依赖服务 (MySQL, MinIO, SRS)..." -ForegroundColor Cyan
    Set-Location $RootDir
    if ($ResetMySqlData) { Reset-MySqlDatabase }
    docker compose up -d mysql minio srs
    if ($LASTEXITCODE -ne 0) { throw "Docker 容器启动失败。" }

    Write-Host "等待 MySQL 和 MinIO 就绪..." -ForegroundColor Cyan
    Wait-ForInfrastructure
    if (-not (Test-MySqlCredentials -Password $MySqlPassword)) {
        throw 'MySQL 已启动，但当前密码无法登录。该数据库数据卷由旧密码初始化；请使用旧密码运行脚本，或确认无需保留本地数据库后添加 -ResetMySqlData。'
    }
    Clear-E2eData

    Write-Host "==> [3/5] 运行后端测试..." -ForegroundColor Cyan
    Set-Location "$RootDir\backend"
    .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw "后端 Maven 测试未通过。" }

    Remove-Item $BackendLog -ErrorAction SilentlyContinue
    Write-Host "==> [4/5] 启动本机后端服务..." -ForegroundColor Cyan
    $BackendProcess = Start-Process -FilePath '.\mvnw.cmd' -ArgumentList 'spring-boot:run' -WorkingDirectory "$RootDir\backend" -RedirectStandardOutput $BackendLog -RedirectStandardError "$BackendLog.err" -PassThru -WindowStyle Hidden
    Wait-ForBackend
    Seed-E2eData

    Write-Host "==> [5/5] 执行前端依赖安装、构建与 E2E 测试..." -ForegroundColor Cyan
    Set-Location "$RootDir\frontend\demo"
    
    npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci 依赖安装失败。" }

    npx playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw "Playwright 浏览器安装失败。" }

    npm run build
    if ($LASTEXITCODE -ne 0) { throw "前端构建失败。" }

    Write-Host "--> 运行 E2E 测试: UC03..." -ForegroundColor Yellow
    npm run test:e2e:uc03
    if ($LASTEXITCODE -ne 0) { throw "E2E UC03 测试失败。" }

    Write-Host "--> 运行 E2E 测试: UC04-06..." -ForegroundColor Yellow
    npm run test:e2e:uc04-06
    if ($LASTEXITCODE -ne 0) { throw "E2E UC04-06 测试失败。" }

    Write-Host "--> 运行 E2E 测试: UC07-10..." -ForegroundColor Yellow
    npm run test:e2e:uc07-10
    if ($LASTEXITCODE -ne 0) { throw "E2E UC07-10 测试失败。" }

    Write-Host "`n✅ 全部服务构建与测试执行成功！" -ForegroundColor Green
}
catch {
    Write-Error "`n❌ 执行过程中发生错误: $_"
    exit 1
}
finally {
    if ($null -ne $BackendProcess -and -not $BackendProcess.HasExited) {
        Stop-Process -Id $BackendProcess.Id -Force
    }
    # 恢复工作目录
    Set-Location $RootDir
}
