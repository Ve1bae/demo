[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$ImageTag,
    [switch]$PlanOnly
)

$ErrorActionPreference = 'Stop'
$releaseRoot = Split-Path -Parent $PSScriptRoot
$releaseContext = 'docker-desktop'
$releaseNamespace = 'hangyin'
$releaseApps = @('user-service', 'video-service', 'live-service', 'frontend')
$previousImages = @{}
$changedApps = [System.Collections.Generic.List[string]]::new()

function Invoke-ReleaseKubectl {
    param([string[]]$Arguments)
    $result = & kubectl --context $releaseContext -n $releaseNamespace @Arguments
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: $($Arguments -join ' ')" }
    $result
}

function Test-ReleaseHealth {
    # Use our own short-lived forward; do not stop existing user port-forwards.
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    $forward = $null
    $logDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('uc03-health-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $logDirectory | Out-Null
    try {
        $forward = Start-Process -FilePath (Get-Command kubectl -CommandType Application).Source `
            -ArgumentList @('--context', $releaseContext, '-n', $releaseNamespace, 'port-forward', 'service/api-gateway', "${port}:8080", '--address=127.0.0.1') `
            -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logDirectory 'stdout.log') `
            -RedirectStandardError (Join-Path $logDirectory 'stderr.log')
        $deadline = [DateTime]::UtcNow.AddSeconds(90)
        do {
            if ($forward.HasExited) { throw "Health port-forward exited; see $logDirectory" }
            try {
                $base = "http://127.0.0.1:$port"
                $gateway = Invoke-RestMethod "$base/gateway/health" -TimeoutSec 5
                $rooms = Invoke-RestMethod "$base/api/live/rooms" -TimeoutSec 10
                $videos = Invoke-RestMethod "$base/api/videos/recommend?page=1&pageSize=1" -TimeoutSec 10
                $storage = Invoke-RestMethod "$base/api/minio/test" -TimeoutSec 10
                if ($gateway.status -ne 'UP' -or $rooms.code -ne 200 -or $videos.code -ne 200 -or
                    $storage.code -ne 200 -or $storage.data.connected -ne $true) {
                    throw 'Gateway, business API or MinIO health assertion failed.'
                }
                return
            } catch {
                if ([DateTime]::UtcNow -ge $deadline) { throw }
                Start-Sleep -Seconds 2
            }
        } while ($true)
    } finally {
        if ($null -ne $forward -and -not $forward.HasExited) { Stop-Process -Id $forward.Id -ErrorAction SilentlyContinue }
        Write-Host "Health-check logs: $logDirectory"
    }
}

# This is an update-only CD path: never apply DB manifests, change secrets or migrate data.
foreach ($app in $releaseApps) {
    $deployment = Invoke-ReleaseKubectl @('get', 'deployment', $app, '-o', 'json') | ConvertFrom-Json
    $container = @($deployment.spec.template.spec.containers | Where-Object { $_.name -eq $app })
    if ($container.Count -ne 1) { throw "Expected one container named $app; bootstrap must be completed manually." }
    $previousImages[$app] = $container[0].image
}
$gatewayService = Invoke-ReleaseKubectl @('get', 'service', 'api-gateway', '-o', 'json') | ConvertFrom-Json
$gatewayPort = @($gatewayService.spec.ports | Where-Object { $_.port -eq 8080 })[0].nodePort
if (-not $gatewayPort -or $gatewayPort -lt 30000 -or $gatewayPort -gt 32767) {
    throw 'An existing API gateway NodePort is required for the local frontend.'
}
foreach ($dependency in @('user-mysql', 'video-mysql', 'live-mysql', 'minio', 'srs', 'api-gateway')) {
    Invoke-ReleaseKubectl @('rollout', 'status', "deployment/$dependency", '--timeout=60s') | Out-Host
}
foreach ($app in $releaseApps) {
    Write-Host "$app : $($previousImages[$app]) -> hangyin/${app}:$ImageTag"
}
if ($PlanOnly) {
    Write-Host 'PLAN ONLY: no builds, image changes, secret changes or database changes performed.'
    return
}

# Refuse to deploy on an already-unhealthy baseline; avoid blaming a new release for it.
Test-ReleaseHealth
$dockerOperatingSystem = & docker info --format '{{.OperatingSystem}}'
if ($LASTEXITCODE -ne 0 -or $dockerOperatingSystem -notmatch 'Docker Desktop') {
    throw 'Refusing to build images on a Docker engine other than Docker Desktop.'
}
foreach ($app in $releaseApps) {
    $buildArguments = @('build', '--tag', "hangyin/${app}:$ImageTag")
    if ($app -eq 'frontend') { $buildArguments += @('--build-arg', "VITE_API_PORT=$gatewayPort") }
    $buildDirectory = if ($app -eq 'frontend') { 'frontend/demo' } else { $app }
    $buildArguments += (Join-Path $releaseRoot $buildDirectory)
    & docker @buildArguments
    if ($LASTEXITCODE -ne 0) { throw "Docker build failed for $app; running applications have not been changed." }
}

try {
    foreach ($app in $releaseApps) {
        $changedApps.Add($app)
        Invoke-ReleaseKubectl @('set', 'image', "deployment/$app", "$app=hangyin/${app}:$ImageTag") | Out-Host
        Invoke-ReleaseKubectl @('rollout', 'status', "deployment/$app", '--timeout=240s') | Out-Host
    }
    Test-ReleaseHealth
    Write-Host "Local application release $ImageTag passed. Database resources and secrets were preserved."
} catch {
    $releaseError = $_
    foreach ($app in $changedApps) {
        try {
            Invoke-ReleaseKubectl @('set', 'image', "deployment/$app", "$app=$($previousImages[$app])") | Out-Host
            Invoke-ReleaseKubectl @('rollout', 'status', "deployment/$app", '--timeout=180s') | Out-Host
        } catch {
            Write-Warning "Image rollback failed for $app; inspect deployment manually."
        }
    }
    throw $releaseError
}
