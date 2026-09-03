[CmdletBinding()]
param(
    [string]$Namespace = 'hangyin',
    [int]$DurationSeconds = 180,
    [int]$ScaleDownWaitSeconds = 180,
    [int]$LoadPods = 8,
    [string]$LoadImage = 'busybox:1.36',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\evidence\hpa\live-service'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidence = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

function Invoke-Kubectl {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
}

function Save-Kubectl {
    param([string]$Name, [string[]]$Arguments)
    & kubectl @Arguments | Tee-Object -FilePath (Join-Path $evidence $Name) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
}

$loadSelector = "live-hpa-load=$stamp"
Save-Kubectl 'hpa-before.txt' @('get', 'hpa', 'live-service', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'deployment-before.txt' @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'pods-before.txt' @('get', 'pods', '-n', $Namespace, '-l', 'app.kubernetes.io/name=live-service', '-o', 'wide')

for ($index = 1; $index -le [Math]::Max($LoadPods, 1); $index++) {
    $job = "live-hpa-$stamp-$index"
    Invoke-Kubectl @(
        'run', $job, '-n', $Namespace, "--image=$LoadImage", '--restart=Never',
        "--labels=app=hpa-load,$loadSelector", '--', 'sh', '-c',
        "while true; do wget -q -O- 'http://live-service:8090/api/live/rooms?page=1&pageSize=50' >/dev/null; done"
    )
}

try {
    $deadline = (Get-Date).AddSeconds($DurationSeconds)
    while ((Get-Date) -lt $deadline) {
        Save-Kubectl 'hpa-samples.txt' @('get', 'hpa', 'live-service', '-n', $Namespace, '-o', 'wide')
        Save-Kubectl 'deployment-samples.txt' @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'wide')
        Start-Sleep -Seconds 15
    }
} finally {
    Invoke-Kubectl @('delete', 'pod', '-n', $Namespace, '-l', $loadSelector, '--ignore-not-found=true', '--wait=false')
}

Save-Kubectl 'hpa-after-load.txt' @('get', 'hpa', 'live-service', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'deployment-after-load.txt' @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'wide')
Write-Host "Waiting $ScaleDownWaitSeconds seconds for HPA scale-down stabilization..."
Start-Sleep -Seconds ([Math]::Max($ScaleDownWaitSeconds, 0))
Save-Kubectl 'hpa-after-scale-down.txt' @('get', 'hpa', 'live-service', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'deployment-after-scale-down.txt' @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'live-service-hpa-describe.txt' @('describe', 'hpa', 'live-service', '-n', $Namespace)

Write-Host "live-service HPA evidence written to $evidence"
