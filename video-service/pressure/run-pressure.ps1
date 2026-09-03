param(
    [string]$BaseUrl = 'http://127.0.0.1:8082',
    [int]$Runs = 3,
    [string]$OutputDirectory = 'pressure-results'
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

for ($run = 1; $run -le $Runs; $run++) {
    $json = Join-Path $OutputDirectory ("run-{0:D2}.json" -f $run)
    $summary = Join-Path $OutputDirectory ("run-{0:D2}.txt" -f $run)
    Write-Host "Starting k6 run $run/$Runs against $BaseUrl"
    k6 run --summary-export $json --env BASE_URL=$BaseUrl pressure/recommendation.js | Tee-Object -FilePath $summary
    if ($LASTEXITCODE -ne 0) {
        throw "k6 run $run failed with exit code $LASTEXITCODE"
    }
}

Write-Host "Completed $Runs runs. Keep the k6 summaries together with CPU and memory observations."



