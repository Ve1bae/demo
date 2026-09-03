[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8090',
    [Parameter(Mandatory = $true)]
    [string]$RoomId,
    [string]$UserId = '910001',
    [int]$Runs = 3,
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
$serviceRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $serviceRoot 'pressure-results'
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

try {
    $health = Invoke-WebRequest -UseBasicParsing -Uri "$($BaseUrl.TrimEnd('/'))/actuator/health" -TimeoutSec 5
    if ($health.StatusCode -ne 200) { throw "live-service health check returned $($health.StatusCode)" }
} catch {
    throw "live-service is not ready at $BaseUrl. Start the service and provide an online LIVE_ROOM_ID. $($_.Exception.Message)"
}

Push-Location $serviceRoot
try {
    for ($run = 1; $run -le $Runs; $run++) {
        foreach ($scenario in @(
            @{ Name = 'rest'; Script = 'pressure/live-rest.js' },
            @{ Name = 'websocket'; Script = 'pressure/live-websocket.js' }
        )) {
            $prefix = "run-{0:D2}-{1}" -f $run, $scenario.Name
            $summary = Join-Path $OutputDirectory "$prefix.json"
            $log = Join-Path $OutputDirectory "$prefix.txt"
            Write-Host "Starting k6 $($scenario.Name) run $run/$Runs against $BaseUrl"
            k6 run --summary-export $summary `
                --env BASE_URL=$BaseUrl --env WS_BASE_URL=$BaseUrl `
                --env ROOM_ID=$RoomId --env USER_ID=$UserId `
                $scenario.Script | Tee-Object -FilePath $log
            if ($LASTEXITCODE -ne 0) {
                throw "k6 $($scenario.Name) run $run failed with exit code $LASTEXITCODE"
            }
        }
    }
} finally {
    Pop-Location
}

Write-Host "Completed $Runs REST and WebSocket k6 runs. Results: $OutputDirectory"
