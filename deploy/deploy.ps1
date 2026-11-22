# TrueBuddy 空白主机一键部署（Windows + Docker Desktop）
# 前置：仅 Docker Desktop（含 docker compose）；无需 Java / Node / Python / Maven / psql。
# 用法：在仓库根目录执行 powershell -ExecutionPolicy Bypass -File deploy/deploy.ps1
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root 'deploy/docker-compose.yml'
$EnvFile = Join-Path $Root '.env'
$ExampleFile = Join-Path $Root 'deploy/.env.example'

function Get-EnvValue([string]$Name) {
    if (-not (Test-Path $EnvFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match "^\s*$Name=(.*)$") { return $Matches[1] }
    }
    return $null
}

function Set-EnvValue([string]$Name, [string]$Value) {
    $lines = Get-Content -LiteralPath $EnvFile
    $replaced = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*$Name=") {
            $lines[$i] = "$Name=$Value"
            $replaced = $true
            break
        }
    }
    if (-not $replaced) { $lines += "$Name=$Value" }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($EnvFile, [string[]]$lines, $utf8NoBom)
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw '未找到 docker，请先安装 Docker Desktop。'
}
if (-not (docker compose version 2>$null)) {
    throw '未找到 docker compose 插件，请确认 Docker Desktop 已启用 Compose。'
}

if (-not (Test-Path $EnvFile)) {
    Copy-Item -LiteralPath $ExampleFile -Destination $EnvFile
    Write-Host '[初始化] 已从 deploy/.env.example 生成根目录 .env'
}

# 补齐模板中缺失的键（保留已有值，避免旧 .env 缺 PUBLIC_HOST 等导致 http://:9000）
foreach ($line in Get-Content -LiteralPath $ExampleFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=') {
        $key = $Matches[1]
        if ($null -eq (Get-EnvValue $key)) {
            Add-Content -LiteralPath $EnvFile -Value $line -Encoding utf8
            Write-Host "[初始化] 补入缺失变量 $key"
        }
    }
}

$currentHost = Get-EnvValue 'PUBLIC_HOST'
if ([string]::IsNullOrWhiteSpace($currentHost) -or $currentHost -eq '__AUTO_IP__') {
    $ip = $null
    try {
        $ip = Get-NetIPConfiguration |
            Where-Object { $_.IPv4DefaultGateway } |
            Select-Object -First 1 -ExpandProperty IPv4Address |
            Select-Object -ExpandProperty IPAddress
    } catch { }
    if (-not $ip) {
        $ip = '127.0.0.1'
        Write-Warning '未能自动探测局域网 IP，已回落 127.0.0.1；跨机访问请手工修改 .env 的 PUBLIC_HOST。'
    }
    Set-EnvValue 'PUBLIC_HOST' $ip
    Write-Host "[初始化] PUBLIC_HOST=$ip"
}

$PublicHost = Get-EnvValue 'PUBLIC_HOST'
$FrontendPort = Get-EnvValue 'FRONTEND_PORT'
$BackendPort = Get-EnvValue 'BACKEND_PORT'
$MinioPort = Get-EnvValue 'MINIO_PORT'
$ConsolePort = Get-EnvValue 'MINIO_CONSOLE_PORT'
$PostgresUser = Get-EnvValue 'POSTGRES_USER'

$Base = @('compose', '-f', $ComposeFile, '--env-file', $EnvFile)

Write-Host '[1/5] 拉取基础镜像并构建应用镜像（首次执行较慢）...'
& docker @Base build --pull
if ($LASTEXITCODE -ne 0) { throw '镜像构建失败。' }

Write-Host '[2/5] 启动全部服务...'
& docker @Base up -d
if ($LASTEXITCODE -ne 0) { throw '容器启动失败。' }

Write-Host '[3/5] 等待数据库就绪...'
$dbOk = $false
for ($i = 0; $i -lt 60; $i++) {
    & docker @Base exec -T db pg_isready -U $PostgresUser -d tb 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $dbOk = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $dbOk) { throw '数据库未在 120 秒内就绪，请查看：docker compose -f deploy/docker-compose.yml logs db' }

Write-Host '[4/5] 等待后端 API 就绪...'
$apiOk = $false
for ($i = 0; $i -lt 90; $i++) {
    $body = & docker @Base exec -T frontend wget -qO- "http://backend:8086/api/homework/task/list" 2>$null
    if ("$body" -match '"code":0') { $apiOk = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $apiOk) { throw '后端未在 180 秒内就绪，请查看：docker compose -f deploy/docker-compose.yml logs backend' }

Write-Host '[5/5] 检查 MinIO...'
& docker @Base exec -T frontend wget -qO- "http://minio:9000/minio/health/live" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host 'MinIO 健康' } else { Write-Warning 'MinIO 未通过健康检查，请查看 minio 容器日志。' }

Write-Host ''
Write-Host '部署完成：'
Write-Host "  前端页面   http://$PublicHost`:$FrontendPort"
Write-Host "  后端 API   http://$PublicHost`:$BackendPort/api"
Write-Host "  MinIO API  http://$PublicHost`:$MinioPort"
Write-Host "  MinIO 控制台 http://$PublicHost`:$ConsolePort"
Write-Host ''
Write-Host '查看日志：docker compose -f deploy/docker-compose.yml logs -f backend'
Write-Host '停止服务：docker compose -f deploy/docker-compose.yml down'
