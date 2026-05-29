# Job Console UI

Small React + Bootstrap console for the Chronos scheduler and worker services.

## What it covers

- signup and login
- create delayed, one-time, and cron jobs
- admin-only bulk email and reminder payloads (subject + body editable)
- tracked job status lookup
- update and delete using saved request drafts
- worker execution visibility
- admin failed-jobs view from the scheduler
- lightweight activity feed for job lifecycle updates
- news fetch jobs with India, gold price, and stock update categories
- admin report panel exposes a downloadable CSV link for generated files

## Run

```bash
cd /Users/mihir/Documents/job-console-ui
npm install
npm run dev
```

Vite runs on `http://localhost:5173`.

## Backend assumptions

- scheduler service on `http://localhost:8087`
- worker service on `http://localhost:8088`

The dev server proxies:

- `/api/scheduler/*` -> scheduler
- `/api/worker/*` -> worker

- `/api/worker/v1/reports/failed/*` -> worker report downloads

If you want different targets, set:

- `VITE_SCHEDULER_API_BASE`
- `VITE_WORKER_API_BASE`
