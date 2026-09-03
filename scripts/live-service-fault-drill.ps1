[CmdletBinding()]
param(
    [string]$Namespace = 'hangyin',
    [int]$LocalPort = 18090,
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\evidence\fault-drill\live-service'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidence = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Force -Path $evidence | Out-Null

function Invoke-KubectlText {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $result = & kubectl @Arguments | Out-String
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
    return $result.Trim()
}

function Save-Kubectl {
    param([string]$Name, [string[]]$Arguments)
    Invoke-KubectlText $Arguments | Set-Content -Encoding UTF8 (Join-Path $evidence $Name)
}

function Save-Http {
    param([string]$Name, [string]$Uri)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 5
        [pscustomobject]@{
            name = $Name
            status = [int]$response.StatusCode
            body = $response.Content
        } | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $evidence "$Name.json")
        return $true
    } catch {
        [pscustomobject]@{ name = $Name; error = $_.Exception.Message } |
            ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $evidence "$Name.json")
        return $false
    }
}

function Wait-Healthy {
    param([string]$Uri, [int]$TimeoutSeconds = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Save-Http 'health-probe' $Uri) { return }
        Start-Sleep -Seconds 3
    }
    throw "live-service did not recover within $TimeoutSeconds seconds"
}

$originalReplicas = [int](Invoke-KubectlText @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'jsonpath={.spec.replicas}'))
$portForwardOutput = Join-Path $evidence 'port-forward.stdout.log'
$portForwardError = Join-Path $evidence 'port-forward.stderr.log'
$portForward = $null
$baseUrl = "http://127.0.0.1:$LocalPort"

try {
    # Use two replicas so the Service can route traffic while one Pod is deliberately removed.
    Invoke-KubectlText @('scale', 'deployment', 'live-service', '-n', $Namespace, '--replicas=2') | Set-Content -Encoding UTF8 (Join-Path $evidence 'scale-to-two.txt')
    Invoke-KubectlText @('rollout', 'status', 'deployment/live-service', '-n', $Namespace, '--timeout=180s') | Set-Content -Encoding UTF8 (Join-Path $evidence 'rollout-before.txt')
    Save-Kubectl 'pods-before.txt' @('get', 'pods', '-n', $Namespace, '-l', 'app.kubernetes.io/name=live-service', '-o', 'wide')
    Save-Kubectl 'deployment-before.txt' @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'wide')

    $portForward = Start-Process kubectl -ArgumentList @('port-forward', 'service/live-service', "$LocalPort`:8090", '-n', $Namespace) `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $portForwardOutput -RedirectStandardError $portForwardError
    Wait-Healthy "$baseUrl/actuator/health"
    Save-Http 'before-health' "$baseUrl/actuator/health" | Out-Null
    Save-Http 'before-live-rooms' "$baseUrl/api/live/rooms?page=1&pageSize=12" | Out-Null

    $victim = Invoke-KubectlText @('get', 'pods', '-n', $Namespace, '-l', 'app.kubernetes.io/name=live-service', '-o', 'jsonpath={.items[0].metadata.name}')
    if ([string]::IsNullOrWhiteSpace($victim)) { throw 'No live-service Pod found for fault drill' }
    Set-Content -Encoding UTF8 -Path (Join-Path $evidence 'victim-pod.txt') -Value $victim
    Invoke-KubectlText @('delete', 'pod', $victim, '-n', $Namespace, '--wait=false') | Set-Content -Encoding UTF8 (Join-Path $evidence 'delete-victim.txt')
    Save-Kubectl 'pods-during-failover.txt' @('get', 'pods', '-n', $Namespace, '-l', 'app.kubernetes.io/name=live-service', '-o', 'wide')

    Wait-Healthy "$baseUrl/actuator/health"
    if (-not (Save-Http 'during-failover-health' "$baseUrl/actuator/health")) { throw 'Health endpoint did not remain available during failover' }
    if (-not (Save-Http 'during-failover-live-rooms' "$baseUrl/api/live/rooms?page=1&pageSize=12")) { throw 'Live rooms endpoint did not remain available during failover' }

    Invoke-KubectlText @('rollout', 'status', 'deployment/live-service', '-n', $Namespace, '--timeout=180s') | Set-Content -Encoding UTF8 (Join-Path $evidence 'rollout-after.txt')
    Save-Kubectl 'pods-after-recovery.txt' @('get', 'pods', '-n', $Namespace, '-l', 'app.kubernetes.io/name=live-service', '-o', 'wide')
    Save-Kubectl 'deployment-after-recovery.txt' @('get', 'deployment', 'live-service', '-n', $Namespace, '-o', 'wide')
    Save-Http 'after-recovery-health' "$baseUrl/actuator/health" | Out-Null
    Save-Http 'after-recovery-live-rooms' "$baseUrl/api/live/rooms?page=1&pageSize=12" | Out-Null
    Write-Host "live-service fault drill passed. Evidence: $evidence"
} finally {
    if ($portForward -and -not $portForward.HasExited) { Stop-Process -Id $portForward.Id -Force }
    Invoke-KubectlText @('scale', 'deployment', 'live-service', '-n', $Namespace, "--replicas=$originalReplicas") | Set-Content -Encoding UTF8 (Join-Path $evidence 'restore-original-replicas.txt')
}
