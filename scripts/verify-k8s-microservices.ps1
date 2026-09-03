[CmdletBinding()]
param(
    [string]$Namespace = 'hangyin',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\evidence\k8s'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidence = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

function Save-Kubectl {
    param([string]$Name, [string[]]$Arguments)
    & kubectl @Arguments | Tee-Object -FilePath (Join-Path $evidence $Name) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
}

Save-Kubectl 'pods.txt' @('get', 'pods', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'services.txt' @('get', 'svc', '-n', $Namespace)
Save-Kubectl 'deployments.txt' @('get', 'deployment', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'hpa.txt' @('get', 'hpa', '-n', $Namespace, '-o', 'wide')
Save-Kubectl 'events.txt' @('get', 'events', '-n', $Namespace, '--sort-by=.lastTimestamp')

foreach ($deployment in @('user-service', 'video-service', 'live-service', 'api-gateway', 'frontend')) {
    Save-Kubectl "$deployment-rollout.txt" @('rollout', 'status', "deployment/$deployment", '-n', $Namespace, '--timeout=30s')
}

Write-Host "Kubernetes verification evidence written to $evidence"
