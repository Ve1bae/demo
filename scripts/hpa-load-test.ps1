[CmdletBinding()]
param(
    [string]$Namespace = 'hangyin',
    [string]$ServiceUrl = 'http://video-service:8082',
    [int]$DurationSeconds = 120,
    [int]$ScaleDownWaitSeconds = 90,
    [int]$LoadPods = 8,
    [string]$LoadImage = 'busybox:1.36',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\evidence\hpa'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidence = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

function Invoke-Kubectl {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
}

Invoke-Kubectl @('get', 'hpa', '-n', $Namespace, '-o', 'wide') | Out-File -Encoding UTF8 (Join-Path $evidence 'hpa-before.txt')
Invoke-Kubectl @('get', 'deployment', '-n', $Namespace, '-o', 'wide') | Out-File -Encoding UTF8 (Join-Path $evidence 'deployments-before.txt')

$jobPrefix = "hpa-load-$stamp"
for ($index = 1; $index -le [Math]::Max($LoadPods, 1); $index++) {
    $job = "$jobPrefix-$index"
    Invoke-Kubectl @('run', $job, '-n', $Namespace, "--image=$LoadImage", '--restart=Never', '--labels=app=hpa-load', '--', 'sh', '-c', "while true; do wget -q -O- '$ServiceUrl/api/videos/recommend?page=1&pageSize=12' >/dev/null; done")
}
try {
    $deadline = (Get-Date).AddSeconds($DurationSeconds)
    while ((Get-Date) -lt $deadline) {
        Invoke-Kubectl @('get', 'hpa', '-n', $Namespace, '-o', 'wide') | Tee-Object -FilePath (Join-Path $evidence 'hpa-samples.txt') -Append | Out-Null
        Invoke-Kubectl @('get', 'deployment', '-n', $Namespace, '-o', 'wide') | Tee-Object -FilePath (Join-Path $evidence 'deployment-samples.txt') -Append | Out-Null
        Start-Sleep -Seconds 15
    }
} finally {
    Invoke-Kubectl @('delete', 'pod', '-n', $Namespace, '-l', 'app=hpa-load', '--ignore-not-found=true', '--wait=false')
}

Invoke-Kubectl @('get', 'hpa', '-n', $Namespace, '-o', 'wide') | Out-File -Encoding UTF8 (Join-Path $evidence 'hpa-after-load.txt')
Invoke-Kubectl @('get', 'deployment', '-n', $Namespace, '-o', 'wide') | Out-File -Encoding UTF8 (Join-Path $evidence 'deployments-after-load.txt')

Write-Host "Waiting $ScaleDownWaitSeconds seconds for the HPA scale-down stabilization window..."
Start-Sleep -Seconds ([Math]::Max($ScaleDownWaitSeconds, 0))

Invoke-Kubectl @('get', 'hpa', '-n', $Namespace, '-o', 'wide') | Out-File -Encoding UTF8 (Join-Path $evidence 'hpa-after-scale-down.txt')
Invoke-Kubectl @('get', 'deployment', '-n', $Namespace, '-o', 'wide') | Out-File -Encoding UTF8 (Join-Path $evidence 'deployments-after-scale-down.txt')
Invoke-Kubectl @('describe', 'hpa', 'video-service', '-n', $Namespace) | Out-File -Encoding UTF8 (Join-Path $evidence 'video-service-hpa-describe.txt')

Write-Host "HPA load evidence written to $evidence"
