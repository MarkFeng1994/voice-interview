# Release Checklist

## Backend

- `mvn -DskipTests package`
- service env file prepared from `ops/voice-interview-backend.env.example`
- `systemctl status voice-interview-backend`
- `GET /actuator/health` returns `UP`
- `GET /api/system/providers` returns expected provider mix

## Mobile H5

- `npm run build:h5`
- static files deployed under the expected web root
- login works
- setup page loads presets
- start interview works
- submit text answer works
- report page opens

## Admin

- `npm run build`
- `/admin/` opens
- login works
- category CRUD works
- question CRUD works

## Voice Chain

- `GET /api/system/providers` shows `ASR = dashscope` and `TTS = dashscope`
- `reply-preview` returns playable audio URL
- audio upload + ASR transcription works

## Cleanup and Error Handling

- unauthenticated request returns JSON `UNAUTHORIZED`
- `POST /api/system/media/cleanup` executes successfully
- `t_media_file` receives new records for uploaded/generated audio
