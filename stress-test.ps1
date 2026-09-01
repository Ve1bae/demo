# Video-Service HPA Stress Test Script (PS 5.1 compatible)
# Usage: .\stress-test.ps1 -Url http://localhost:18082/api/videos/recommend -Concurrency 200 -DurationSeconds 120

param(
    [string]$Url = "http://localhost:18082/api/videos/recommend",
    [int]$Concurrency = 200,
    [int]$DurationSeconds = 120
)

Add-Type -AssemblyName System.Net.Http
[System.Net.ServicePointManager]::DefaultConnectionLimit = 9999
[System.Net.ServicePointManager]::Expect100Continue = $false

$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.MaxConnectionsPerServer = $Concurrency
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(5)

$endTime = (Get-Date).AddSeconds($DurationSeconds)
$totalRequests = 0
$totalErrors = 0
$allLatencies = New-Object 'System.Collections.Generic.List[double]'

Write-Host "===== Stress Test Start =====" -ForegroundColor Cyan
Write-Host "Target: $Url"
Write-Host "Concurrency: $Concurrency, Duration: ${DurationSeconds}s"
Write-Host "=================================" -ForegroundColor Cyan

while ((Get-Date) -lt $endTime) {
    $tasks = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]'

    for ($i = 0; $i -lt $Concurrency; $i++) {
        $tasks.Add($client.GetAsync($Url))
    }

    [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())

    foreach ($task in $tasks) {
        $totalRequests++
        if (-not $task.IsCompletedSuccessfully) {
            $totalErrors++
        } else {
            $resp = $task.Result
            $allLatencies.Add($resp.Headers.Date.Ticks)
        }
    }

    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Total $totalRequests req, errors $totalErrors"
}

Write-Host ""
Write-Host "===== Stress Test End =====" -ForegroundColor Green

$duration = $DurationSeconds
$rps = [math]::Round($totalRequests / $duration, 1)
$errorRate = if ($totalRequests -gt 0) { [math]::Round($totalErrors / $totalRequests * 100, 2) } else { 0 }

Write-Host "Total Requests:  $totalRequests"
Write-Host "Errors:          $totalErrors"
Write-Host "Throughput:      $rps req/s"
Write-Host "Error Rate:      ${errorRate}%"

$client.Dispose()
