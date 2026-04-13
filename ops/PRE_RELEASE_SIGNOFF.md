# Pre-Release Signoff

## Build

- [ ] `voice-interview-backend`: `mvn -DskipTests package`
- [ ] `voice-interview-mobile`: `npm run build:h5`
- [ ] `voice-interview-admin`: `npm run build`

## Backend Runtime

- [ ] `/actuator/health` returns `UP`
- [ ] `/api/system/providers` returns expected provider mix after login or with Bearer token
- [ ] JWT login/profile flow verified
- [ ] WebSocket session sync verified
- [ ] `POST /api/system/media/cleanup` verified
- [ ] `t_media_file` receives uploaded/generated rows

## Mobile Functional

- [ ] Login / register
- [ ] Setup page preset selection
- [ ] Start interview
- [ ] Text answer
- [ ] Voice upload and ASR
- [ ] AI audio playback
- [ ] Skip / end
- [ ] History
- [ ] History filter and session restore
- [ ] Report
- [ ] Cancelled-session report hint
- [ ] Profile update

## Admin Functional

- [ ] Login
- [ ] Category CRUD
- [ ] Question CRUD

## Device UAT

- [ ] Android browser path
- [ ] iPhone browser path
- [ ] WeChat embedded browser path if required

## Operations

- [ ] Backend service file installed
- [ ] Backend env file created from example
- [ ] Nginx config applied
- [ ] Static mobile/admin assets deployed
- [ ] Logs and restart policy confirmed
