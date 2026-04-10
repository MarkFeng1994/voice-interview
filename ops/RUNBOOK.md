# Local Runbook

## 1. Prepare runtime config

Copy:

```powershell
Copy-Item .\ops\runtime.local.example.ps1 .\ops\runtime.local.ps1
```

Then edit `ops/runtime.local.ps1` and fill in:

- MySQL URL / username / password
- LLM proxy base URL / API key / model
- DashScope realtime API key

## 2. Start backend

```powershell
.\ops\run-backend-dev.ps1
```

Custom port:

```powershell
.\ops\run-backend-dev.ps1 -Port 18080
```

## 3. Start mobile H5

```powershell
.\ops\run-mobile-h5.ps1
```

Custom API base URL:

```powershell
.\ops\run-mobile-h5.ps1 -ApiBaseUrl http://127.0.0.1:18080
```

## 4. Smoke check

After backend starts, verify:

- `GET /actuator/health`
- `GET /api/system/providers`
- login -> create interview -> answer -> report

Or run:

```powershell
.\ops\smoke-check.ps1 -ApiBaseUrl http://127.0.0.1:8080
```

If you also want to validate category/question CRUD:

```powershell
.\ops\smoke-check.ps1 -ApiBaseUrl http://127.0.0.1:8080 -IncludeLibrary
```

## Notes

- `ops/runtime.local.ps1` should stay local and must not be committed.
- Current recommended mixed runtime:
  - `AI -> OpenAI-compatible proxy`
  - `ASR -> qwen3-asr-flash-realtime`
  - `TTS -> qwen3-tts-flash-realtime`
- Linux deployment templates:
  - `ops/voice-interview-backend.service`
  - `ops/voice-interview-backend.env.example`
  - `ops/nginx.voice-interview.conf.example`
  - `ops/RELEASE_CHECKLIST.md`
  - `ops/MOBILE_UAT_MATRIX.md`
  - `ops/PRE_RELEASE_SIGNOFF.md`
  - `ops/UAT_EXECUTION_ORDER.md`
  - `ops/UAT_STEP_BY_STEP.md`
  - `ops/TROUBLESHOOTING.md`
