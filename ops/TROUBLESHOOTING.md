# Troubleshooting

## 1. Always collect request id

The backend now returns `X-Request-Id` on HTTP responses.

When a request fails:

1. capture the response body
2. capture `X-Request-Id`
3. grep backend logs by that request id

## 2. Common failure buckets

### `UNAUTHORIZED`

Meaning:

- token missing
- token expired
- login state lost

Check:

- login request result
- browser/app storage token
- request header contains `Authorization: Bearer ...`

### `NOT_FOUND`

Meaning:

- wrong API path
- wrong static route
- session/report/media id not found

Check:

- requested URL
- whether the resource was created in the current environment

### `INVALID_BODY` / `INVALID_PARAM`

Meaning:

- request payload shape mismatch
- wrong path/query parameter type

Check:

- request JSON
- path variable formatting

### `ASR_PROVIDER_UNAVAILABLE` / `TTS_PROVIDER_UNAVAILABLE`

Meaning:

- sherpa or remote provider is unreachable
- remote service returned an error

Check:

- `/api/system/providers`
- sherpa `/healthz`
- provider base URL in env

### `MEDIA_NOT_FOUND` / `MEDIA_READ_FAILED` / `MEDIA_STORE_FAILED`

Meaning:

- local media file missing
- disk write failed
- cleanup already removed the file

Check:

- backend storage directory
- `t_media_file`
- cleanup history

## 3. Local debugging commands

### Backend

```powershell
.\ops\run-backend-dev.ps1
.\ops\smoke-check.ps1 -ApiBaseUrl http://127.0.0.1:8080
```

### Mobile H5

```powershell
.\ops\run-mobile-h5.ps1
```

### Admin

```powershell
cd .\voice-interview-admin
npm run dev
```

## 4. Linux debugging hints

### Backend service

```bash
systemctl status voice-interview-backend
journalctl -u voice-interview-backend -n 200 --no-pager
```

### sherpa service

```bash
systemctl status sherpa-onnx-lite
journalctl -u sherpa-onnx-lite -n 200 --no-pager
curl http://127.0.0.1:18100/healthz
```
