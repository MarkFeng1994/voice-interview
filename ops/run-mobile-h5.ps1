param(
  [string]$ApiBaseUrl = $env:VITE_API_BASE_URL
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$mobileDir = Join-Path $repoRoot 'voice-interview-mobile'
$runtimeFile = Join-Path $PSScriptRoot 'runtime.local.ps1'

if (Test-Path $runtimeFile) {
  . $runtimeFile
}

if (-not $ApiBaseUrl) {
  $ApiBaseUrl = 'http://127.0.0.1:8080'
}

$env:VITE_API_BASE_URL = $ApiBaseUrl

Write-Host "Starting mobile H5 with API base: $env:VITE_API_BASE_URL"
Set-Location $mobileDir
& npm run dev:h5
