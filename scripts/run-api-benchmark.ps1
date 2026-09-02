param(
  [string]$BaseUrl = 'http://127.0.0.1:8080/api',
  [string]$Concurrency = '1,10,50,100',
  [int]$DurationSeconds = 30,
  [int]$WarmupSeconds = 10,
  [int]$Runs = 3
)

$ErrorActionPreference = 'Stop'
$env:BENCHMARK_BASE_URL = $BaseUrl
$env:BENCHMARK_CONCURRENCY = $Concurrency
$env:BENCHMARK_DURATION_SEC = $DurationSeconds
$env:BENCHMARK_WARMUP_SEC = $WarmupSeconds
$env:BENCHMARK_RUNS = $Runs
node (Join-Path $PSScriptRoot 'benchmark-api.mjs')
if ($LASTEXITCODE -ne 0) { throw 'API benchmark failed.' }
