param(
  [string]$BaseUrl = 'http://127.0.0.1:8080/api',
  [string]$Concurrency = '1,10,50,100',
  [int]$DurationSeconds = 30,
  [int]$WarmupSeconds = 10,
  [int]$Runs = 3,
  [int]$VideoId = 1,
  [int]$UserId = 1,
  [string]$OutputDir = 'benchmark-results'
)

$ErrorActionPreference = 'Stop'
$env:BENCHMARK_BASE_URL = $BaseUrl
$env:BENCHMARK_CONCURRENCY = $Concurrency
$env:BENCHMARK_DURATION_SEC = $DurationSeconds
$env:BENCHMARK_WARMUP_SEC = $WarmupSeconds
$env:BENCHMARK_RUNS = $Runs
$env:BENCHMARK_VIDEO_ID = $VideoId
$env:BENCHMARK_USER_ID = $UserId
$env:BENCHMARK_OUTPUT_DIR = $OutputDir
node (Join-Path $PSScriptRoot 'benchmark-api.mjs')
if ($LASTEXITCODE -ne 0) { throw 'API benchmark failed.' }
