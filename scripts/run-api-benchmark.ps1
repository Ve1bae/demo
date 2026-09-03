param(
  [string]$BaseUrl = 'http://127.0.0.1:8080/api',
  [string]$Concurrency = '1,10,50,100',
  [int]$DurationSeconds = 30,
  [int]$WarmupSeconds = 10,
  [int]$Runs = 3,
  [int]$VideoId = 1,
  [int]$UserId = 1,
  [string]$OutputDir = 'benchmark-results',
  [string]$Container = '',
  [switch]$Seed
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
$env:BENCHMARK_CONTAINER = $Container
if ($Seed) {
  $seedDataFile = Join-Path $OutputDir 'benchmark-data.json'
  $env:BENCHMARK_DATA_FILE = $seedDataFile
  node (Join-Path $PSScriptRoot 'seed-benchmark-data.mjs') | Out-Host
  if ($LASTEXITCODE -ne 0) { throw 'Benchmark data seeding failed.' }
  $seedJson = [string](Get-Content -LiteralPath $seedDataFile -Raw)
  $seedData = ConvertFrom-Json -InputObject $seedJson
  $env:BENCHMARK_VIDEO_ID = [string]$seedData.videoId
  $env:BENCHMARK_USER_ID = [string]$seedData.userId
  Write-Host "Seeded video=$($seedData.videoId), comments=$($seedData.commentIds.Count)"
}
node (Join-Path $PSScriptRoot 'benchmark-api.mjs')
if ($LASTEXITCODE -ne 0) { throw 'API benchmark failed.' }
