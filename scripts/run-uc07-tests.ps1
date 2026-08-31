param(
    [string]$BaseUrl = $env:UC07_E2E_BASE_URL,
    [string]$UserId = $env:UC07_E2E_USER_ID
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$node = "node"

if (-not $BaseUrl) {
    $BaseUrl = "http://localhost:8080"
}
if (-not $UserId) {
    $UserId = "42"
}

Write-Host "Running UC-07 JUnit tests..."
Push-Location $backendDir
try {
    & .\mvnw.cmd "-Dtest=Uc07CreateRoomServiceTest,Uc07CreateRoomControllerTest" test
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}

Write-Host "Running UC-07 E2E tests against $BaseUrl ..."
$env:UC07_E2E_BASE_URL = $BaseUrl
$env:UC07_E2E_USER_ID = $UserId
& $node (Join-Path $PSScriptRoot "uc07-e2e.mjs")
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "UC-07 tests passed."
