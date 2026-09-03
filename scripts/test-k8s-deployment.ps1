[CmdletBinding()]
param()

# No cluster, Docker daemon or credentials are accessed: every kubectl call is mocked.
$ErrorActionPreference = 'Stop'
$k8sTestCalls = [System.Collections.Generic.List[string]]::new()
$k8sTestFailedDeployment = ''

function kubectl {
    $command = $args -join ' '
    # Record only rollout commands, never runtime secret values.
    if ($command.StartsWith('rollout status ')) {
        $k8sTestCalls.Add($command)
    }
    $global:LASTEXITCODE = 0
    if ($k8sTestFailedDeployment -and $command.StartsWith("rollout status deployment/$k8sTestFailedDeployment ")) {
        $global:LASTEXITCODE = 1
        return
    }
    'mocked-kubectl-result'
}

$dependencies = @('mysql', 'user-mysql', 'video-mysql', 'live-mysql', 'minio', 'srs')
$deployments = $dependencies + @('user-service', 'video-service', 'live-service', 'api-gateway', 'frontend')
& "$PSScriptRoot/deploy-microservices-k8s.ps1" -SkipBuild *> $null
foreach ($deployment in $deployments) {
    if (-not $k8sTestCalls.Contains("rollout status deployment/$deployment -n hangyin --timeout=240s")) {
        throw "Deployment readiness was not checked: $deployment"
    }
}
Write-Host 'PASS: all 11 deployments must become ready.'

foreach ($dependency in $dependencies) {
    $k8sTestCalls.Clear()
    $k8sTestFailedDeployment = $dependency
    $failure = $null
    try {
        & "$PSScriptRoot/deploy-microservices-k8s.ps1" -SkipBuild *> $null
    } catch {
        $failure = $_.Exception.Message
    }
    if (-not $failure -or -not $failure.Contains("kubectl failed: kubectl rollout status deployment/$dependency ")) {
        throw "An unavailable dependency did not fail deployment: $dependency"
    }
    if ($k8sTestCalls[$k8sTestCalls.Count - 1] -ne "rollout status deployment/$dependency -n hangyin --timeout=240s") {
        throw "Deployment continued after dependency failure: $dependency"
    }
    Write-Host "PASS: unavailable $dependency stops deployment."
}
$global:LASTEXITCODE = 0
