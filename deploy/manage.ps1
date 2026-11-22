# TrueBuddy 日常管理脚本（借鉴 Supabase run.sh 的子命令形态）
# 用法：powershell -ExecutionPolicy Bypass -File deploy/manage.ps1 <命令> [服务名...]
param(
    [Parameter(Position = 0)] [string]$Command = 'help',
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)] [string[]]$Services = @()
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = if ($env:ENV_FILE) { $env:ENV_FILE } else { Join-Path $Root '.env' }
$ComposeFile = Join-Path $Root 'deploy/docker-compose.yml'

if (-not (Test-Path $EnvFile)) {
    throw '根目录 .env 不存在，请先执行 deploy/deploy.ps1。'
}

$Base = @('compose', '-f', $ComposeFile, '--env-file', $EnvFile)

function Get-EnvValue([string]$Name) {
    foreach ($line in @(Get-Content -LiteralPath $EnvFile -Encoding utf8)) {
        if ($line -match "^\s*$Name=(.*)$") { return $Matches[1] }
    }
    return $null
}

function Show-Usage {
    $lines = @(
        'Usage: powershell -ExecutionPolicy Bypass -File deploy/manage.ps1 <command> [service...]',
        '',
        'Commands:',
        '  start       Start all services and wait for healthchecks',
        '  stop        Stop and remove containers, keep volumes',
        '  restart     Restart all or specified services',
        '  recreate    Force recreate all or specified services',
        '  logs        Follow logs of all or specified services',
        '  ps          Show service status',
        '  pull        Pull service images',
        '  env-check   List placeholder env keys only (never prints values)',
        '  help        Show this help',
        '',
        'Example: powershell -ExecutionPolicy Bypass -File deploy/manage.ps1 logs backend'
    )
    foreach ($line in $lines) { Write-Host $line }
}

switch ($Command.ToLowerInvariant()) {
    'start' {
        & docker @Base up -d --wait --wait-timeout 300
    }
    'stop' {
        & docker @Base down
    }
    'restart' {
        & docker @Base restart @Services
    }
    'recreate' {
        & docker @Base up -d --force-recreate --wait --wait-timeout 300 @Services
    }
    'logs' {
        & docker @Base logs -f @Services
    }
    'ps' {
        & docker @Base ps
    }
    'pull' {
        & docker @Base pull
    }
    'env-check' {
        $keys = @(
            'SPRING_AI_OPENAI_API_KEY', 'OPENAI_EMBEDDING_API_KEY',
            'DOCMIND_ACCESS_KEY_ID', 'DOCMIND_ACCESS_KEY_SECRET',
            'DASHSCOPE_API_KEY', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD'
        )
        foreach ($key in $keys) {
            $v = Get-EnvValue $key
            if ([string]::IsNullOrWhiteSpace($v) -or $v -in @('change-me', 'change-me-please', 'postgres')) {
                Write-Host "[占位] $key"
            }
        }
        Write-Host '说明：以上仅为键名；本地数据库/MinIO/LiveKit 凭据由 deploy.ps1 自动生成。'
    }
    'help' { Show-Usage }
    default { Show-Usage; exit 1 }
}

if ($LASTEXITCODE -ne 0 -and $Command.ToLowerInvariant() -ne 'help') {
    exit $LASTEXITCODE
}
