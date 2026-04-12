# UAT Execution Order

## Goal

Run UAT in a fixed order so failures are easier to isolate.

## Step 1. Environment Checks

Verify first:

- backend service is running
- `/actuator/health` is `UP`
- `/api/system/providers` matches the expected provider mix

## Step 2. Authentication

Run these before touching interview flows:

1. Login with a known account
2. Refresh page / reopen app
3. Logout and verify protected routes redirect to login

If auth fails, stop here. Do not continue to session testing.

## Step 3. Interview Configuration

1. Open setup page
2. Confirm presets load
3. Start one interview with `backend-core`
4. Start one interview with `microservice-troubleshooting`

If presets or start-session fail, check:

- `/api/interviews/presets`
- `/api/interviews`

## Step 4. Session Core Loop

1. Start session
2. Submit text answer
3. Confirm WebSocket live update
4. Try skip
5. Try manual end

If session updates do not appear in real time, check:

- `/api/interviews/{id}/ws-ticket`
- `/ws/interview`

## Step 5. Voice Loop

1. Upload audio
2. Run ASR
3. Submit voice answer
4. Play AI audio

If this step fails, inspect:

- `/api/system/providers`
- `t_media_file`
- `/api/system/media/cleanup`

## Step 6. History and Report

1. Open history page
2. Resume in-progress session
3. Open report page
4. Verify strengths / weaknesses / suggestions
5. Verify history filter and cancelled-session report hint

## Step 7. Profile and Admin

1. Update nickname
2. Login to admin
3. Category CRUD
4. Question CRUD

## Stop Rules

If any step fails:

- record the exact page and action
- record the `X-Request-Id` from the failing response
- check backend logs with that request id first
- do not continue to downstream cases until the blocker is resolved
