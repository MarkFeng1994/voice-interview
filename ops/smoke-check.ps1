param(
  [string]$ApiBaseUrl = 'http://127.0.0.1:8080',
  [string]$Username = 'coff0xc',
  [string]$Password = '123456',
  [string]$PresetKey = 'backend-core',
  [string]$ResumePdfPath = '',
  [switch]$IncludeLibrary
)

$ErrorActionPreference = 'Stop'

function Invoke-JsonRequest {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers,
    [object]$Body
  )

  $params = @{
    Method = $Method
    Uri = $Url
  }

  if ($Headers) {
    $params.Headers = $Headers
  }

  if ($null -ne $Body) {
    $params.ContentType = 'application/json'
    $params.Body = ($Body | ConvertTo-Json -Depth 8)
  }

  return Invoke-RestMethod @params
}

function Write-SmokePass {
  param([string]$Name)
  Write-Host "[smoke] $Name PASS"
}

Write-Host "Smoke checking $ApiBaseUrl"

$health = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/actuator/health"
Write-Host "Health:" $health.status

$unauthorized = $null
try {
  Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/api/user/profile" | Out-Null
  throw "Expected unauthorized request to fail"
} catch {
  $unauthorized = $_.ErrorDetails.Message
}
Write-Host "Unauthorized response verified"

$login = Invoke-JsonRequest -Method Post -Url "$ApiBaseUrl/api/auth/login" -Body @{
  username = $Username
  password = $Password
}

if (-not $login.success) {
  throw "Login failed: $($login.message)"
}

$token = [string]$login.data.token
$headers = @{ Authorization = "Bearer $token" }
Write-Host "Login ok as $($login.data.profile.username)"

$resumePath = $ResumePdfPath
if (-not $resumePath) {
  $defaultResume = Join-Path (Split-Path $PSScriptRoot -Parent) '项目架构技术明细.pdf'
  if (Test-Path $defaultResume) {
    $resumePath = $defaultResume
  }
}

if ($resumePath -and (Test-Path $resumePath)) {
  $resumeUpload = Invoke-WebRequest -Method Post -Uri "$ApiBaseUrl/api/resumes/upload" -Headers $headers -Form @{
    file = Get-Item $resumePath
  }
  $resumePayload = $resumeUpload.Content | ConvertFrom-Json
  if (-not $resumePayload.success) {
    throw "Resume upload failed: $($resumePayload.message)"
  }
  Write-SmokePass 'resume upload + parse'
} else {
  Write-Host '[smoke] resume upload + parse SKIP (no pdf provided)'
}

$providers = Invoke-JsonRequest -Method Get -Url "$ApiBaseUrl/api/system/providers" -Headers $headers
Write-Host "Providers:" ($providers.data.ai.provider + '/' + $providers.data.asr.provider + '/' + $providers.data.tts.provider)
Write-SmokePass 'provider runtime'

$presets = Invoke-JsonRequest -Method Get -Url "$ApiBaseUrl/api/interviews/presets" -Headers $headers
Write-Host "Preset count:" $presets.data.Count

$session = Invoke-JsonRequest -Method Post -Url "$ApiBaseUrl/api/interviews" -Headers $headers -Body @{
  presetKey = $PresetKey
  durationMinutes = 60
}
Write-Host "Session created:" $session.data.sessionId
Write-SmokePass 'interview start duration=60'

$ticket = Invoke-JsonRequest -Method Post -Url "$ApiBaseUrl/api/interviews/$($session.data.sessionId)/ws-ticket" -Headers $headers
Write-Host "wsTicket issued for session:" $ticket.data.sessionId

$answer = Invoke-JsonRequest -Method Post -Url "$ApiBaseUrl/api/interviews/$($session.data.sessionId)/answer" -Headers $headers -Body @{
  fileId = $null
  textAnswer = '这是 smoke check 的文本回答。'
}
Write-Host "Answer submitted. Status:" $answer.data.status

$report = Invoke-JsonRequest -Method Get -Url "$ApiBaseUrl/api/interviews/$($session.data.sessionId)/report" -Headers $headers
Write-Host "Report title:" $report.data.title
Write-SmokePass 'report query'

$cleanup = Invoke-JsonRequest -Method Post -Url "$ApiBaseUrl/api/system/media/cleanup" -Headers $headers
Write-Host "Cleanup deleted count:" $cleanup.data.deletedCount

if ($IncludeLibrary) {
  $categoryName = 'smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 6)
  $category = Invoke-JsonRequest -Method Post -Url "$ApiBaseUrl/api/library/categories" -Headers $headers -Body @{
    name = $categoryName
    parentId = '0'
    sortOrder = 0
  }
  Write-Host "Library category created:" $category.data.name
}

Write-Host 'Smoke check passed.'
