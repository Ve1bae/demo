[CmdletBinding()]
param(
    [string]$Namespace = 'hangyin',
    [string]$ImageTag = 'local',
    [string]$Registry = 'hangyin',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Kubectl {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
}

function New-RuntimeSecret {
    param([string]$Name, [hashtable]$Values)
    $args = @('create', 'secret', 'generic', $Name, '-n', $Namespace, '--dry-run=client', '-o', 'yaml')
    foreach ($entry in $Values.GetEnumerator()) { $args += "--from-literal=$($entry.Key)=$($entry.Value)" }
    $yaml = & kubectl @args
    if ($LASTEXITCODE -ne 0) { throw "Unable to create secret $Name" }
    $yaml | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw "Unable to apply secret $Name" }
}

function New-RuntimeConfigMapFromFile {
    param([string]$Name, [string]$Key, [string]$Path)
    Invoke-Kubectl @('create', 'configmap', $Name, '-n', $Namespace, "--from-file=$Key=$Path", '--dry-run=client', '-o', 'yaml') |
        & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw "Unable to apply configmap $Name" }
}

Invoke-Kubectl @('config', 'current-context')
Invoke-Kubectl @('apply', '-f', (Join-Path $root 'k8s/namespace.yaml'))
Invoke-Kubectl @('apply', '-f', (Join-Path $root 'k8s/metrics-server.yaml'))

$password = if ($env:K8S_DB_PASSWORD) { $env:K8S_DB_PASSWORD } else { [guid]::NewGuid().ToString('N') }
$videoPassword = if ($env:K8S_VIDEO_DB_PASSWORD) { $env:K8S_VIDEO_DB_PASSWORD } else { $password }
$livePassword = if ($env:K8S_LIVE_DB_PASSWORD) { $env:K8S_LIVE_DB_PASSWORD } else { $password }
$minioPassword = if ($env:K8S_MINIO_PASSWORD) { $env:K8S_MINIO_PASSWORD } else { [guid]::NewGuid().ToString('N') }

New-RuntimeSecret 'app-secrets' @{
    MYSQL_ROOT_PASSWORD = $password
    MINIO_ROOT_USER = 'minioadmin'
    MINIO_ROOT_PASSWORD = $minioPassword
    MINIO_SECRET_KEY = $minioPassword
}
New-RuntimeSecret 'video-db-secret' @{ password = $videoPassword }
New-RuntimeSecret 'live-db-secret' @{ password = $livePassword }

New-RuntimeConfigMapFromFile 'mysql-init' '01-demo.sql' (Join-Path $root 'demo.sql')
New-RuntimeConfigMapFromFile 'user-db-init' '01-user-db.sql' (Join-Path $root 'infra/mysql/user-db.sql')
New-RuntimeConfigMapFromFile 'video-db-init' '001_video_schema.sql' (Join-Path $root 'video-service/sql/001_video_schema.sql')
New-RuntimeConfigMapFromFile 'live-db-init' '001_live_schema.sql' (Join-Path $root 'live-service/sql/001_live_schema.sql')
New-RuntimeConfigMapFromFile 'gateway-config' 'nginx.conf' (Join-Path $root 'gateway/nginx.microservices.conf')

if (-not $SkipBuild) {
    docker build --tag "$Registry/user-service:$ImageTag" (Join-Path $root 'user-service')
    docker build --tag "$Registry/video-service:$ImageTag" (Join-Path $root 'video-service')
    docker build --tag "$Registry/live-service:$ImageTag" (Join-Path $root 'live-service')
    docker build --tag "$Registry/frontend:$ImageTag" (Join-Path $root 'frontend/demo')
}

# Platform dependencies and the user service.
foreach ($manifest in @('k8s/mysql.yaml', 'k8s/user-mysql.yaml', 'k8s/minio.yaml', 'k8s/srs.yaml', 'k8s/user-service.yaml', 'k8s/api-gateway.yaml', 'k8s/frontend.yaml')) {
    Invoke-Kubectl @('apply', '-f', (Join-Path $root $manifest))
}

# Video and live service manifests already include probes, resource limits and DB init ConfigMaps.
foreach ($manifest in @(
    'video-service/k8s/mysql.yml',
    'video-service/k8s/video-service.yml',
    'video-service/k8s/video-service-service.yml',
    'live-service/k8s/mysql.yml',
    'live-service/k8s/live-service.yml',
    'live-service/k8s/live-service-service.yml'
)) {
    Invoke-Kubectl @('apply', '-n', $Namespace, '-f', (Join-Path $root $manifest))
}

Invoke-Kubectl @('set', 'image', 'deployment/user-service', "user-service=$Registry/user-service`:$ImageTag", '-n', $Namespace)
Invoke-Kubectl @('set', 'image', 'deployment/video-service', "video-service=$Registry/video-service`:$ImageTag", '-n', $Namespace)
Invoke-Kubectl @('set', 'image', 'deployment/live-service', "live-service=$Registry/live-service`:$ImageTag", '-n', $Namespace)
Invoke-Kubectl @('set', 'image', 'deployment/frontend', "frontend=$Registry/frontend`:$ImageTag", '-n', $Namespace)

Invoke-Kubectl @('apply', '-f', (Join-Path $root 'k8s/microservices-hpa.yaml'))

# Dependencies must also be ready; healthy business APIs alone do not prove storage works.
foreach ($deployment in @('mysql', 'user-mysql', 'video-mysql', 'live-mysql', 'minio', 'srs', 'user-service', 'video-service', 'live-service', 'api-gateway', 'frontend')) {
    Invoke-Kubectl @('rollout', 'status', "deployment/$deployment", '-n', $Namespace, '--timeout=240s')
}

Write-Host "Microservices Kubernetes deployment completed in namespace '$Namespace'."
Write-Host "Gateway: kubectl port-forward service/api-gateway 8080:8080 -n $Namespace"
Write-Host "HPA: kubectl get hpa -n $Namespace"
