# Mobile UAT Matrix

## Scope

This matrix targets the current MVP:

- Mobile H5 / uni-app client
- Backend with JWT + MySQL
- `AI -> OpenAI-compatible proxy`
- `ASR -> qwen3-asr-flash-realtime`
- `TTS -> qwen3-tts-flash-realtime`

## Devices

At minimum, test on:

- Android Chrome latest
- Android WeChat built-in browser
- iPhone Safari latest
- iPhone WeChat built-in browser

If available, also test:

- Android packaged app build
- iOS packaged app build

## Required Test Cases

### A. Login and User State

| ID | Scenario | Expected Result |
|---|---|---|
| `A-01` | Login with valid account | Redirect to home page |
| `A-02` | Login with wrong password | Toast shows error, no login state persisted |
| `A-03` | Register a new account | Account created and redirected to home |
| `A-04` | Refresh app after login | User remains logged in |
| `A-05` | Logout | Redirect to login, protected pages blocked |

### B. Interview Setup

| ID | Scenario | Expected Result |
|---|---|---|
| `B-01` | Open setup page | Preset list loads successfully |
| `B-02` | Choose different preset | Preset highlight changes |
| `B-03` | Start interview with backend-core | Session opens with backend-core first question |
| `B-04` | Start interview with microservice preset | Session opens with troubleshooting first question |

### C. Session Core Flow

| ID | Scenario | Expected Result |
|---|---|---|
| `C-01` | Start a new session | Status becomes `IN_PROGRESS`, first AI question visible |
| `C-02` | Submit text answer | Messages update and next AI turn arrives |
| `C-03` | Click play AI audio | Audio plays successfully |
| `C-04` | Use skip | Session advances or ends correctly |
| `C-05` | End session manually | Confirmation dialog appears, then session ends and report opens |
| `C-06` | Reopen an in-progress session from history | Session restores correctly |

### D. Voice Flow

| ID | Scenario | Expected Result |
|---|---|---|
| `D-01` | Record audio and upload | Upload succeeds and draft state updates |
| `D-02` | Transcribe latest audio | Transcript appears and provider is correct |
| `D-03` | Submit voice answer | Backend uses ASR result and advances session |
| `D-04` | Play generated AI voice | Audio loads and plays successfully |

### E. WebSocket / Realtime

| ID | Scenario | Expected Result |
|---|---|---|
| `E-01` | Start session | WebSocket connects automatically |
| `E-02` | Submit answer | Session updates arrive without manual refresh |
| `E-03` | Restore session page | WebSocket reconnects successfully |
| `E-04` | Disconnect network briefly and recover | Manual refresh and reconnect still work; no permanent page lock |

### F. Report and History

| ID | Scenario | Expected Result |
|---|---|---|
| `F-01` | Open history list | Session list loads |
| `F-02` | Open report page | Score, strengths, weaknesses, suggestions render |
| `F-03` | Open report without sessionId | Friendly error shown |
| `F-04` | Resume session from history | Correct session opens |
| `F-05` | Filter history by status | In-progress / completed / cancelled sessions can be filtered |
| `F-06` | Open cancelled report | Cancelled-session hint and context banner render |

### G. Profile

| ID | Scenario | Expected Result |
|---|---|---|
| `G-01` | Refresh profile | Profile data loads |
| `G-02` | Update nickname | Nickname persists and reload shows new value |

## Required Environment Checks

Before running UAT:

- `GET /actuator/health` returns `UP`
- `GET /api/system/providers` matches expected provider mix after login or with Bearer token
- backend and static frontend are both reachable from the device

## Release Gate

The MVP can be considered ready for trial only if:

- All `A` through `G` cases pass on at least one Android and one iPhone path
- Voice flow passes on at least one real Android device and one real iPhone device
- No blocking issue remains in login, session, voice, history, or report
