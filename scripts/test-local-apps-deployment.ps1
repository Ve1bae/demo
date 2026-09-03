[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$localTestRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $PSScriptRoot 'deploy-local-apps.ps1'
$source = Get-Content -Raw -LiteralPath $sourcePath
$ast = [System.Management.Automation.Language.Parser]::ParseInput($source, [ref]$null, [ref]$null)
$healthFunction = $ast.Find({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Test-ReleaseHealth'
}, $true)
# Unit-test orchestration, not HTTP/process management. No Docker/Kubernetes commands execute.
$healthMock = @'
function Test-ReleaseHealth {
    $localTestState.HealthCalls++
    if ($localTestState.FailHealth -and $localTestState.HealthCalls -eq 2) { throw 'mock unhealthy release' }
}
'@
$testSource = $source.Remove($healthFunction.Extent.StartOffset, $healthFunction.Extent.EndOffset - $healthFunction.Extent.StartOffset).Insert($healthFunction.Extent.StartOffset, $healthMock)
$testSource = $testSource.Replace('$releaseRoot = Split-Path -Parent $PSScriptRoot', '$releaseRoot = $localTestRoot')
$testProgram = [scriptblock]::Create($testSource)
$testTag = 'a' * 40

function kubectl {
    $global:LASTEXITCODE = 0
    $localTestState.KubeCalls.Add(($args -join ' '))
    if (($args[0..3] -join ' ') -ne '--context docker-desktop -n hangyin') { throw 'Unexpected deployment target' }
    $operation = $args[4]
    if ($operation -eq 'get' -and $args[5] -eq 'deployment') {
        return (@{spec=@{template=@{spec=@{containers=@(@{name=$args[6];image="previous/$($args[6]):stable"})}}}} | ConvertTo-Json -Depth 8 -Compress)
    }
    if ($operation -eq 'get' -and $args[5] -eq 'service') {
        return '{"spec":{"ports":[{"port":8080,"nodePort":30080}]}}'
    }
    if ($operation -notin @('rollout', 'set')) { throw "Unexpected mutation: $operation" }
    if ($operation -eq 'set') {
        if ($args[5] -ne 'image' -or $args[6] -notin @('deployment/user-service','deployment/video-service','deployment/live-service','deployment/frontend')) {
            throw 'A release may only update application images.'
        }
    }
    if ($localTestState.FailRollout -and ($args -join ' ') -match 'rollout status deployment/video-service --timeout=240s') {
        $global:LASTEXITCODE = 1
    }
}
function docker {
    $global:LASTEXITCODE = 0
    $localTestState.DockerCalls.Add(($args -join ' '))
    if ($args[0] -eq 'info') { return 'Docker Desktop' }
    if ($args[0] -ne 'build') { throw 'Unexpected Docker operation' }
    if ($localTestState.FailBuild) { $global:LASTEXITCODE = 1 }
}

foreach ($scenario in @('plan', 'success', 'build-failure', 'rollout-failure', 'health-failure')) {
    $localTestState = @{
        KubeCalls = [System.Collections.Generic.List[string]]::new()
        DockerCalls = [System.Collections.Generic.List[string]]::new()
        HealthCalls = 0
        FailBuild = $scenario -eq 'build-failure'
        FailRollout = $scenario -eq 'rollout-failure'
        FailHealth = $scenario -eq 'health-failure'
    }
    $caught = $null
    try { & $testProgram -ImageTag $testTag -PlanOnly:($scenario -eq 'plan') *> $null } catch { $caught = $_ }
    $mutations = @($localTestState.KubeCalls | Where-Object { $_ -match ' set image ' })
    if ($scenario -in @('plan', 'success')) {
        if ($caught) { throw $caught }
    } elseif (-not $caught) { throw "$scenario did not fail" }
    if ($scenario -in @('plan','build-failure') -and $mutations.Count -ne 0) { throw "$scenario mutated applications" }
    if ($scenario -eq 'plan' -and ($localTestState.DockerCalls.Count -ne 0 -or $localTestState.HealthCalls -ne 0)) { throw 'Plan mode performed work' }
    if ($scenario -eq 'success') {
        if ($mutations.Count -ne 4) { throw 'Expected four application updates' }
        $frontendBuild = @($localTestState.DockerCalls | Where-Object { $_ -like 'build *' -and $_ -match 'hangyin/frontend:' })
        if ($frontendBuild.Count -ne 1 -or $frontendBuild[0] -notmatch 'VITE_API_PORT=30080' -or $frontendBuild[0] -notmatch 'frontend[/\\]demo$') { throw 'Wrong frontend build context or API port' }
    }
    if ($scenario -eq 'rollout-failure') {
        if ($mutations.Count -ne 4 -or $mutations[2] -notmatch 'user-service=previous/user-service:stable$' -or $mutations[3] -notmatch 'video-service=previous/video-service:stable$') { throw 'Rollout rollback did not restore both changed apps' }
    }
    if ($scenario -eq 'health-failure') {
        if ($mutations.Count -ne 8 -or @($mutations | Where-Object { $_ -match '=previous/.+:stable$' }).Count -ne 4) { throw 'Health failure did not restore all four images' }
    }
    Write-Host "PASS: $scenario"
}
$global:LASTEXITCODE = 0
