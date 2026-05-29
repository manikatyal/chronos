# Chronos Model Handoff

## 1) System Overview
Chronos is a 3-service job execution platform with real-time UI updates.

- `job-scheduler-service` (Spring Boot, default port `8087`)
- `job-worker-service` (Spring Boot, default port `8088`)
- `job-console-ui` (React + Vite, typically `5173` and sometimes additional dev ports)

High-level flow:
1. User logs in via scheduler auth API.
2. UI creates/updates/deletes jobs in scheduler.
3. Scheduler validates, persists, and schedules jobs.
4. Scheduler publishes execution messages to worker pipeline.
5. Worker executes job handlers, persists execution records.
6. Worker pushes realtime websocket events to UI clients.
7. UI updates toasts/cards/lists from websocket payloads.

## 2) Service Responsibilities

### Scheduler (`job-scheduler-service`)
Primary responsibilities:
- Authentication and authorization.
- Job CRUD and input validation.
- Schedule interpretation for `DELAYED`, `ONE_TIME`, `CRON`.
- Persistence of jobs/scheduling metadata.
- Publishing execution messages to worker.
- Tracking scheduler-side status and failure metadata.

Important API base used by UI:
- `/v1/auth/signup`
- `/v1/auth/login`
- `/v1/jobs` (create/list)
- `/v1/jobs/{jobId}` (status/delete/update)
- `/v1/jobs/{jobId}/detail`
- `/v1/jobs/failed`

Auth bootstrap defaults (unless overridden by env):
- `userId: admin`
- `password: admin12345`

### Worker (`job-worker-service`)
Primary responsibilities:
- Consuming job execution messages.
- Executing job-specific handlers.
- Persisting execution records (`/v1/executions`).
- Realtime fanout over websocket (`/ws`).

Realtime event envelope types:
- `bootstrap` (initial snapshot for connected user)
- `reminder` (notification jobs)
- `news` (news headline jobs)
- `report` (failed-jobs report jobs)

### UI (`job-console-ui`)
Primary responsibilities:
- User auth/session handling.
- Job form and payload construction.
- Jobs list + admin failed-jobs panel.
- Websocket connection to worker realtime channel.
- Rendering toast notifications and news/report result cards.

API config defaults in UI:
- Scheduler base: `VITE_SCHEDULER_API_BASE` or `/api/scheduler`
- Worker base: `VITE_WORKER_API_BASE` or `/api/worker`
- WS URL: `VITE_REALTIME_WS_URL` or `ws://127.0.0.1:8088/ws`

WebSocket connect pattern:
- `ws://.../ws?userId=<session.userId>&role=<session.role>`

## 3) Job Types and Payload Contracts

## `SEND_EMAIL`
Single recipient:
- `payload.email`
- `payload.subject`
- `payload.body`

Bulk (admin only):
- `payload.emails[]`
- `payload.subject`
- `payload.body`

## `SEND_NOTIFICATION`
Required common fields:
- `payload.title`
- `payload.message`
- `payload.senderUserId`
- `payload.senderRole`

Recipient mode must be exactly one of:
1. Single: `payload.userId`
2. Bulk list: `payload.userIds[]` (admin use)
3. Broadcast: `payload.sendToAll=true` (admin use)

Both scheduler and worker enforce mutual exclusivity across the 3 recipient modes.

## `FETCH_NEWS_HEADLINES`
Required fields:
- `payload.category` in `INDIA | GOLD | STOCKS`
- `payload.limit` (positive integer)
- `payload.requesterUserId`

Worker result contains fields like:
- `category`
- `categoryLabel`
- `articleCount`
- `sourceUrl`
- `fetchedAt`
- `articles[]`

## `GENERATE_REPORT`
Required fields:
- `payload.reportType` (`FAILED_JOBS_REPORT`)
- `payload.window` (`LAST_30_DAYS`, etc.)

Worker produces report metadata including downloadable filename.

## 4) Scheduling Modes
Supported scheduler modes:
- `DELAYED` with `schedule.delaySeconds`
- `ONE_TIME` with `schedule.runAt` (ISO datetime)
- `CRON` with `schedule.cronExpression`

UI also supports human-friendly cron presets and maps them to cron expressions.

## 5) Realtime Behavior

## Registration and targeting
- Worker websocket handler extracts `userId` and `role` query params.
- `RealtimeHub` tracks sessions by user and role.
- Reminder events are sent only to computed recipients.
- News events are sent only to `requesterUserId`.
- Report events are role-targeted to `ADMIN`.

## Bootstrap
On connect, worker sends `bootstrap` containing recent in-memory data relevant to user/role.
UI uses this to hydrate recent reminders/news/reports.

## 6) Current UX/Product Decisions
- User-visible job IDs are intentionally hidden in UI text.
- Internal IDs still exist and are used for API calls/state keys.
- Success copy avoids exposing raw job IDs to end users.

## 7) Important Known Failure Modes

1. Validation failures (`400`) from scheduler
- Missing required payload fields.
- Invalid recipient mode for notification jobs.
- Unauthorized bulk actions by non-admin.

2. Notification appears as "not delivered"
- Recipient user IDs must exactly match websocket `userId` values.
- Recipient must have active websocket session for immediate toast delivery.
- If relying on bootstrap history, behavior depends on current in-memory retention and UI filtering policy.

3. News returns empty headlines
- External feed may be empty/stale.
- Worker now includes fallback feed URLs (notably for `STOCKS`) and returns first non-empty result.

4. Worker health endpoint reports degraded
- `/actuator/health` may show `503` due to optional dependency health checks (for example mail auth), while core job execution can still function.

## 8) Happy Path Sequences

## A) Notification job
1. UI submits `SEND_NOTIFICATION` create request.
2. Scheduler validates payload + permissions, stores job.
3. Scheduler dispatches execution message.
4. Worker executes notification handler.
5. `RealtimeHub` computes recipients and emits `reminder` event.
6. Recipient UIs show toast.

## B) News fetch job
1. UI submits `FETCH_NEWS_HEADLINES` with category/limit/requester.
2. Scheduler validates and schedules.
3. Worker fetches/parses feed (with fallback candidates).
4. Worker emits `news` event to requester.
5. UI updates latest news result card.

## C) Report generation job
1. Admin submits report job.
2. Worker generates failed jobs report artifact.
3. Worker emits `report` event to admin sessions.
4. UI shows report metadata + download link.

## 9) Key Files for Fast Onboarding

UI:
- `/Users/mihir/Documents/Chronos/job-console-ui/src/App.jsx`
- `/Users/mihir/Documents/Chronos/job-console-ui/src/api.js`

Scheduler:
- `/Users/mihir/Documents/Chronos/job-scheduler-service/src/main/java/com/mihir/jobscheduler/service/JobService.java`
- `/Users/mihir/Documents/Chronos/job-scheduler-service/src/main/java/com/mihir/jobscheduler/service/handler/NotificationJobHandler.java`
- `/Users/mihir/Documents/Chronos/job-scheduler-service/src/main/java/com/mihir/jobscheduler/service/handler/NewsHeadlinesJobHandler.java`
- `/Users/mihir/Documents/Chronos/job-scheduler-service/src/main/java/com/mihir/jobscheduler/entity/JobEntity.java`

Worker:
- `/Users/mihir/Documents/Chronos/job-worker-service/src/main/java/com/mihir/jobworker/service/handler/NotificationJobHandler.java`
- `/Users/mihir/Documents/Chronos/job-worker-service/src/main/java/com/mihir/jobworker/service/handler/NewsHeadlinesJobHandler.java`
- `/Users/mihir/Documents/Chronos/job-worker-service/src/main/java/com/mihir/jobworker/service/NewsFeedService.java`
- `/Users/mihir/Documents/Chronos/job-worker-service/src/main/java/com/mihir/jobworker/service/RealtimeHub.java`
- `/Users/mihir/Documents/Chronos/job-worker-service/src/main/java/com/mihir/jobworker/service/RealtimeWebSocketHandler.java`

## 10) Local Run/Check Cheatsheet
- Scheduler health: `GET http://localhost:8087/actuator/health`
- Worker health: `GET http://localhost:8088/actuator/health`
- Worker executions: `GET http://localhost:8088/v1/executions/{jobId}`
- UI dev: `http://localhost:5173` (or other active Vite ports)

## 11) Assumptions and Notes for Future Models
- This codebase is actively iterated; confirm runtime env vars before changing auth/feed behavior.
- Treat scheduler validation rules as source of truth for request shape.
- For realtime bugs, inspect both websocket connection identity (`userId`, `role`) and `RealtimeHub` targeting logic.
- For "job succeeded but UI empty", compare scheduler detail vs worker execution result payloads.
