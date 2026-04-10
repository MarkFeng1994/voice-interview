param(
  [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$backendDir = Join-Path $repoRoot 'voice-interview-backend'
$runtimeFile = Join-Path $PSScriptRoot 'runtime.local.ps1'

if (Test-Path $runtimeFile) {
  . $runtimeFile
}

if (-not $env:SPRING_PROFILES_ACTIVE) {
  $env:SPRING_PROFILES_ACTIVE = 'dev,openai,dashscope'
}

$required = @(
  'SPRING_DATASOURCE_URL',
  'SPRING_DATASOURCE_USERNAME',
  'SPRING_DATASOURCE_PASSWORD',
  'APP_AI_PROVIDER',
  'APP_ASR_PROVIDER',
  'APP_TTS_PROVIDER'
)

$missing = @()
foreach ($name in $required) {
  if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue)) {
    $missing += $name
  }
}

if ($missing.Count -gt 0) {
  throw "Missing required env vars: $($missing -join ', '). Copy ops/runtime.local.example.ps1 to ops/runtime.local.ps1 and fill in real values."
}

Write-Host "Starting backend on port $Port with profiles: $env:SPRING_PROFILES_ACTIVE"
Set-Location $backendDir
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port"
