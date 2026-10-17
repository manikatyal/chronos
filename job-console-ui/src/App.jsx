import { useEffect, useMemo, useRef, useState } from "react";
import { errorMessage, realtimeUrl, schedulerApi, workerApi } from "./api";

const JOB_OPTIONS = [
  { value: "SEND_EMAIL", label: "Send Email", adminOnly: true },
  { value: "SEND_NOTIFICATION", label: "Send Reminder", adminOnly: false },
  { value: "FETCH_NEWS_HEADLINES", label: "Fetch News (India, Gold, Stocks)", adminOnly: false },
  { value: "GENERATE_REPORT", label: "Generate Failed Jobs Report", adminOnly: true }
];

const SCHEDULE_OPTIONS = ["DELAYED", "ONE_TIME", "CRON"];
const CRON_MODES = [
  { value: "DAILY_AT_TIME", label: "Daily at a specific time" },
  { value: "EVERY_HOUR", label: "Every hour (at a minute)" },
  { value: "EVERY_N_HOURS", label: "Every N hours" },
  { value: "WEEKLY_AT_TIME", label: "Weekly on a day and time" },
  { value: "CUSTOM", label: "Advanced (custom cron)" }
];
const WEEKDAY_OPTIONS = [
  { value: "0", label: "Sunday" },
  { value: "1", label: "Monday" },
  { value: "2", label: "Tuesday" },
  { value: "3", label: "Wednesday" },
  { value: "4", label: "Thursday" },
  { value: "5", label: "Friday" },
  { value: "6", label: "Saturday" }
];
const REPORT_WINDOWS = [
  "LAST_30_DAYS",
  "LAST_15_DAYS",
  "LAST_10_DAYS",
  "LAST_5_DAYS",
  "LAST_1_DAY",
  "LAST_12_HOURS",
  "LAST_6_HOURS",
  "LAST_30_MINUTES",
  "LAST_5_MINUTES"
];

const NEWS_CATEGORIES = [
  { value: "INDIA", label: "India latest news" },
  { value: "GOLD", label: "Gold price updates" },
  { value: "STOCKS", label: "Stock updates" }
];

const defaultForm = () => ({
  jobName: "SEND_NOTIFICATION",
  scheduleType: "DELAYED",
  delaySeconds: "60",
  runAt: "",
  cronExpression: "0 10 * * *",
  cronMode: "DAILY_AT_TIME",
  cronTime: "10:00",
  cronMinute: "0",
  cronHourInterval: "2",
  cronWeekday: "1",
  maxRetries: "3",
  email: "",
  emails: "",
  userId: "",
  userIds: "",
  emailSubject: "",
  emailBody: "",
  title: "",
  message: "",
  bulkMode: false,
  sendToAll: false,
  newsLimit: "10",
  newsCategory: "INDIA",
  reportWindow: "LAST_30_DAYS"
});

function readSession() {
  try {
    const raw = localStorage.getItem("chronos.console.session");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function formatDate(value) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

function toLocalDateTimeInput(value) {
  if (!value) return "";
  const date = new Date(value);
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60 * 1000);
  return local.toISOString().slice(0, 16);
}

function buildSchedule(form) {
  if (form.scheduleType === "DELAYED") {
    return { delaySeconds: Number(form.delaySeconds) };
  }
  if (form.scheduleType === "ONE_TIME") {
    return { runAt: new Date(form.runAt).toISOString() };
  }
  return { cronExpression: buildCronExpression(form) };
}

function splitList(raw) {
  if (!raw) return [];
  return raw
    .split(/[,\n]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildCronExpression(form) {
  const minute = Number(form.cronMinute || "0");
  const safeMinute = Number.isFinite(minute) ? Math.min(59, Math.max(0, minute)) : 0;

  if (form.cronMode === "EVERY_HOUR") {
    return `${safeMinute} * * * *`;
  }

  if (form.cronMode === "EVERY_N_HOURS") {
    const interval = Number(form.cronHourInterval || "2");
    const safeInterval = Number.isFinite(interval) ? Math.min(23, Math.max(1, interval)) : 2;
    return `${safeMinute} */${safeInterval} * * *`;
  }

  if (form.cronMode === "WEEKLY_AT_TIME") {
    const [hour, minutePart] = (form.cronTime || "10:00").split(":");
    return `${Number(minutePart || "0")} ${Number(hour || "10")} * * ${form.cronWeekday || "1"}`;
  }

  if (form.cronMode === "CUSTOM") {
    return form.cronExpression.trim();
  }

  const [hour, minutePart] = (form.cronTime || "10:00").split(":");
  return `${Number(minutePart || "0")} ${Number(hour || "10")} * * *`;
}

function parseCronExpression(cronExpression) {
  const cron = (cronExpression || "").trim();

  let match = cron.match(/^(\d{1,2}) \* \* \* \*$/);
  if (match) {
    return {
      cronMode: "EVERY_HOUR",
      cronMinute: match[1]
    };
  }

  match = cron.match(/^(\d{1,2}) \*\/(\d{1,2}) \* \* \*$/);
  if (match) {
    return {
      cronMode: "EVERY_N_HOURS",
      cronMinute: match[1],
      cronHourInterval: match[2]
    };
  }

  match = cron.match(/^(\d{1,2}) (\d{1,2}) \* \* ([0-6])$/);
  if (match) {
    return {
      cronMode: "WEEKLY_AT_TIME",
      cronWeekday: match[3],
      cronTime: `${String(match[2]).padStart(2, "0")}:${String(match[1]).padStart(2, "0")}`
    };
  }

  match = cron.match(/^(\d{1,2}) (\d{1,2}) \* \* \*$/);
  if (match) {
    return {
      cronMode: "DAILY_AT_TIME",
      cronTime: `${String(match[2]).padStart(2, "0")}:${String(match[1]).padStart(2, "0")}`
    };
  }

  return {
    cronMode: "CUSTOM",
    cronExpression: cron || "0 10 * * *"
  };
}

function formatTimeLabel(timeValue) {
  if (!timeValue || !timeValue.includes(":")) {
    return "10:00 AM";
  }
  const [rawHour, rawMinute] = timeValue.split(":");
  const hour = Number(rawHour);
  const minute = Number(rawMinute);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return "10:00 AM";
  }
  const normalizedHour = ((hour % 24) + 24) % 24;
  const normalizedMinute = Math.min(59, Math.max(0, minute));
  const ampm = normalizedHour >= 12 ? "PM" : "AM";
  const hour12 = normalizedHour % 12 || 12;
  return `${hour12}:${String(normalizedMinute).padStart(2, "0")} ${ampm}`;
}

function cronScheduleSummary(form) {
  if (form.scheduleType !== "CRON") {
    return "";
  }

  if (form.cronMode === "EVERY_HOUR") {
    const minute = Number(form.cronMinute || "0");
    const safeMinute = Number.isFinite(minute) ? Math.min(59, Math.max(0, minute)) : 0;
    return `Runs every hour at minute ${String(safeMinute).padStart(2, "0")} (UTC).`;
  }

  if (form.cronMode === "EVERY_N_HOURS") {
    const interval = Number(form.cronHourInterval || "2");
    const safeInterval = Number.isFinite(interval) ? Math.min(23, Math.max(1, interval)) : 2;
    const minute = Number(form.cronMinute || "0");
    const safeMinute = Number.isFinite(minute) ? Math.min(59, Math.max(0, minute)) : 0;
    return `Runs every ${safeInterval} hour(s) at minute ${String(safeMinute).padStart(2, "0")} (UTC).`;
  }

  if (form.cronMode === "WEEKLY_AT_TIME") {
    const selectedDay = WEEKDAY_OPTIONS.find((option) => option.value === String(form.cronWeekday || "1"));
    return `Runs every ${selectedDay?.label || "Monday"} at ${formatTimeLabel(form.cronTime)} (UTC).`;
  }

  if (form.cronMode === "CUSTOM") {
    const cron = form.cronExpression.trim();
    if (!cron) {
      return "Enter a valid 5 or 6 field cron expression.";
    }
    return `Custom cron expression: ${cron} (evaluated in UTC)`;
  }

  return `Runs every day at ${formatTimeLabel(form.cronTime)} (UTC).`;
}

function buildPayload(form, session) {
  switch (form.jobName) {
    case "SEND_EMAIL": {
      const subject = form.emailSubject?.trim();
      const body = form.emailBody?.trim();
      if (session?.role === "ADMIN" && form.bulkMode) {
        const payload = {
          emails: splitList(form.emails)
        };
        if (subject) {
          payload.subject = subject;
        }
        if (body) {
          payload.body = body;
        }
        return payload;
      }
      const payload = {
        email: form.email.trim()
      };
      if (subject) {
        payload.subject = subject;
      }
      if (body) {
        payload.body = body;
      }
      return payload;
    }
    case "SEND_NOTIFICATION": {
      const payload = {
        title: form.title.trim(),
        message: form.message.trim(),
        senderUserId: session?.userId,
        senderRole: session?.role
      };
      if (session?.role === "ADMIN" && form.bulkMode) {
        if (form.sendToAll) {
          return {
            ...payload,
            sendToAll: true
          };
        }
        return {
          ...payload,
          userIds: splitList(form.userIds)
        };
      }
      return {
        ...payload,
        userId: session?.role === "ADMIN" ? form.userId.trim() : session?.userId
      };
    }
    case "FETCH_NEWS_HEADLINES":
      return {
        category: form.newsCategory,
        limit: Number(form.newsLimit),
        requesterUserId: session?.userId
      };
    case "GENERATE_REPORT":
      return {
        reportType: "FAILED_JOBS_REPORT",
        window: form.reportWindow
      };
    default:
      return {};
  }
}

function buildRequest(form, session) {
  return {
    jobName: form.jobName,
    scheduleType: form.scheduleType,
    schedule: buildSchedule(form),
    payload: buildPayload(form, session),
    maxRetries: Number(form.maxRetries)
  };
}

function hydrateForm(detail, session) {
  const schedule = detail.schedule || {};
  const payload = detail.payload || {};
  const parsedCron = parseCronExpression(schedule.cronExpression);
  return {
    ...defaultForm(),
    jobName: detail.jobName,
    scheduleType: detail.scheduleType,
    delaySeconds: schedule.delaySeconds ? String(schedule.delaySeconds) : "60",
    runAt: schedule.runAt ? toLocalDateTimeInput(schedule.runAt) : "",
    cronExpression: parsedCron.cronExpression || schedule.cronExpression || "0 10 * * *",
    cronMode: parsedCron.cronMode || "DAILY_AT_TIME",
    cronTime: parsedCron.cronTime || "10:00",
    cronMinute: parsedCron.cronMinute || "0",
    cronHourInterval: parsedCron.cronHourInterval || "2",
    cronWeekday: parsedCron.cronWeekday || "1",
    maxRetries: String(detail.maxRetries),
    email: payload.email || "",
    emails: Array.isArray(payload.emails) ? payload.emails.join("\n") : "",
    userId: payload.userId || (detail.jobName === "SEND_NOTIFICATION" && session?.role !== "ADMIN" ? session?.userId || "" : ""),
    userIds: Array.isArray(payload.userIds) ? payload.userIds.join("\n") : "",
    emailSubject: payload.subject || "",
    emailBody: payload.body || "",
    title: payload.title || "",
    message: payload.message || "",
    bulkMode: Array.isArray(payload.emails) || Array.isArray(payload.userIds),
    newsLimit: payload.limit ? String(payload.limit) : "10",
    newsCategory: payload.category || "INDIA",
    reportWindow: payload.window || "LAST_30_DAYS"
  };
}

const MAX_ITEMS = 10;

// Live events from the worker: reminder toasts, news batches and (for admins) reports.
function useRealtime(session) {
  const [status, setStatus] = useState("offline");
  const [reminders, setReminders] = useState([]);
  const [news, setNews] = useState([]);
  const [reports, setReports] = useState([]);
  const [toasts, setToasts] = useState([]);
  const socketRef = useRef(null);

  useEffect(() => {
    if (!session?.accessToken) {
      setStatus("offline");
      setReminders([]);
      setNews([]);
      setReports([]);
      return undefined;
    }

    let closedByUs = false;
    let retryTimer = null;
    let heartbeatTimer = null;

    const pushToast = (item) => {
      setToasts((current) => [item, ...current].slice(0, 3));
      setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== item.id)), 6000);
    };

    const connect = () => {
      setStatus("connecting");
      const socket = new WebSocket(realtimeUrl(session.accessToken));
      socketRef.current = socket;

      socket.onopen = () => {
        setStatus("live");
        // Heartbeat so proxies and load balancers never see the connection as idle.
        clearInterval(heartbeatTimer);
        heartbeatTimer = setInterval(() => {
          if (socket.readyState === WebSocket.OPEN) socket.send("ping");
        }, 30000);
      };
      socket.onmessage = (event) => {
        let envelope;
        try {
          envelope = JSON.parse(event.data);
        } catch {
          return;
        }
        const data = envelope.data || {};
        if (envelope.type === "bootstrap") {
          setReminders((data.notifications || []).slice(0, MAX_ITEMS));
          setNews((data.news || []).slice(0, MAX_ITEMS));
          setReports((data.reports || []).slice(0, MAX_ITEMS));
        } else if (envelope.type === "reminder") {
          setReminders((current) => [data, ...current].slice(0, MAX_ITEMS));
          pushToast(data);
        } else if (envelope.type === "news") {
          setNews((current) => [data, ...current].slice(0, MAX_ITEMS));
        } else if (envelope.type === "report") {
          setReports((current) => [data, ...current].slice(0, MAX_ITEMS));
        }
      };
      socket.onclose = () => {
        clearInterval(heartbeatTimer);
        setStatus("offline");
        if (!closedByUs) {
          retryTimer = setTimeout(connect, 5000);
        }
      };
    };

    connect();
    return () => {
      closedByUs = true;
      clearTimeout(retryTimer);
      clearInterval(heartbeatTimer);
      socketRef.current?.close();
    };
  }, [session?.accessToken]);

  return { status, reminders, news, reports, toasts };
}

export default function App() {
  const [session, setSession] = useState(readSession);
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({ userId: "", password: "" });
  const [jobForm, setJobForm] = useState(defaultForm());
  const [editingJobId, setEditingJobId] = useState(null);
  const [infoMessage, setInfoMessage] = useState("");
  const [loading, setLoading] = useState({
    auth: false,
    submit: false
  });

  const realtime = useRealtime(session);

  async function downloadReport(fileName) {
    try {
      await workerApi.downloadReport(fileName, session.accessToken);
    } catch (error) {
      setInfoMessage(errorMessage(error));
    }
  }

  const visibleJobOptions = useMemo(
    () => JOB_OPTIONS.filter((option) => session?.role === "ADMIN" || !option.adminOnly),
    [session]
  );

  useEffect(() => {
    if (session) {
      localStorage.setItem("chronos.console.session", JSON.stringify(session));
    } else {
      localStorage.removeItem("chronos.console.session");
    }
  }, [session]);

  useEffect(() => {
    if (!session) {
      return;
    }
  }, [session]);

  useEffect(() => {
    if (session && session.role !== "ADMIN" && jobForm.jobName === "SEND_NOTIFICATION") {
      setJobForm((current) => ({ ...current, userId: session.userId }));
    }
  }, [session, jobForm.jobName]);

  function schedulerMessage(error, fallback) {
    const message = errorMessage(error);
    if (error?.status === 409 || /not editable|not deletable|claimed|processing/i.test(message)) {
      return "This task is currently in processing.";
    }
    return message || fallback;
  }

  async function handleAuthSubmit(event) {
    event.preventDefault();
    setLoading((current) => ({ ...current, auth: true }));
    try {
      const response = authMode === "signup"
        ? await schedulerApi.signUp(authForm)
        : await schedulerApi.login(authForm);
      setSession({ ...response, loggedInAt: Date.now() });
      setAuthForm({ userId: "", password: "" });
      setJobForm(defaultForm());
      setInfoMessage(`${response.userId} is signed in and ready.`);
    } catch (error) {
      setInfoMessage(errorMessage(error));
    } finally {
      setLoading((current) => ({ ...current, auth: false }));
    }
  }

  function signOut() {
    setSession(null);
    setEditingJobId(null);
    setJobForm(defaultForm());
    setInfoMessage("Session cleared.");
  }

  function handleFieldChange(event) {
    const { name, value, type, checked } = event.target;
    setJobForm((current) => {
      const next = { ...current, [name]: type === "checkbox" ? checked : value };
      if (session?.role !== "ADMIN" && next.jobName === "SEND_NOTIFICATION") {
        next.userId = session?.userId || "";
      }
      if (next.jobName !== "SEND_EMAIL" && next.jobName !== "SEND_NOTIFICATION") {
        next.bulkMode = false;
        next.sendToAll = false;
      }
      if (next.jobName === "SEND_NOTIFICATION" && !next.bulkMode) {
        next.sendToAll = false;
      }
      return next;
    });
  }

  async function handleJobSubmit(event) {
    event.preventDefault();
    if (!session) {
      setInfoMessage("Sign in first to create jobs.");
      return;
    }
    if (jobForm.scheduleType === "DELAYED") {
      const delaySeconds = Number(jobForm.delaySeconds);
      if (!Number.isFinite(delaySeconds) || delaySeconds < 1) {
        setInfoMessage("Delay seconds must be at least 1.");
        return;
      }
    }
    if (jobForm.scheduleType === "ONE_TIME" && !jobForm.runAt) {
      setInfoMessage("Run at time is required for one-time schedules.");
      return;
    }
    if (jobForm.scheduleType === "CRON" && jobForm.cronMode === "CUSTOM") {
      const fields = jobForm.cronExpression.trim().split(/\s+/).filter(Boolean);
      if (fields.length !== 5 && fields.length !== 6) {
        setInfoMessage("Custom cron must have 5 or 6 fields.");
        return;
      }
    }
    if (jobForm.jobName === "SEND_EMAIL") {
      const subject = jobForm.emailSubject?.trim();
      const body = jobForm.emailBody?.trim();
      if (!subject || !body) {
        setInfoMessage("Email subject and body are required.");
        return;
      }
    }

    setLoading((current) => ({ ...current, submit: true }));
    try {
      const request = buildRequest(jobForm, session);
      const response = editingJobId
        ? await schedulerApi.updateJob(editingJobId, request, session.accessToken)
        : await schedulerApi.createJob(request, session.accessToken);

      setInfoMessage(editingJobId
        ? "Job updated successfully."
        : "Job created successfully.");
      setEditingJobId(null);
      setJobForm(defaultForm());
    } catch (error) {
      setInfoMessage(schedulerMessage(error, "Unable to save job."));
    } finally {
      setLoading((current) => ({ ...current, submit: false }));
    }
  }

  const selectedJob = jobForm.jobName;
  const adminBulkAllowed = session?.role === "ADMIN" && (selectedJob === "SEND_EMAIL" || selectedJob === "SEND_NOTIFICATION");


  return (
    <div className="app-shell">
      <div className="container py-4">
        <div className="row g-4 align-items-start">
          <div className="col-lg-4">
            <div className="panel hero-card">
              <div className="eyebrow">Chronos Console</div>
              <h1 className="h3 mb-3">Simple scheduler UI</h1>
              <p className="text-secondary mb-3">
                {session
                  ? "You're signed in. Create jobs and monitor results below."
                  : "Sign in/login to create real email, notification, news."}
              </p>
              {session ? (
                <>
                  <div className="small text-secondary mb-2">Signed in as</div>
                  <div className="fw-semibold">{session.userId}</div>
                  <div className="text-secondary mb-3">{session.role}</div>
                  <button className="btn btn-outline-secondary btn-sm" onClick={signOut}>Sign out</button>
                </>
              ) : (
                <form className="auth-panel" onSubmit={handleAuthSubmit}>
                  <div className="btn-group mb-3">
                    <button type="button" className={`btn btn-sm ${authMode === "login" ? "btn-dark" : "btn-outline-dark"}`} onClick={() => setAuthMode("login")}>Login</button>
                    <button type="button" className={`btn btn-sm ${authMode === "signup" ? "btn-dark" : "btn-outline-dark"}`} onClick={() => setAuthMode("signup")}>Signup</button>
                  </div>
                  <div className="mb-3">
                    <label className="form-label">User ID</label>
                    <input className="form-control" value={authForm.userId} onChange={(e) => setAuthForm((current) => ({ ...current, userId: e.target.value }))} required />
                  </div>
                  <div className="mb-3">
                    <label className="form-label">Password</label>
                    <input type="password" className="form-control" value={authForm.password} onChange={(e) => setAuthForm((current) => ({ ...current, password: e.target.value }))} required />
                  </div>
                  <button className="btn btn-dark w-100" disabled={loading.auth}>
                    {loading.auth ? "Working..." : authMode === "signup" ? "Create account" : "Login"}
                  </button>
                </form>
              )}
              {infoMessage && <div className="alert alert-light border mt-3 mb-0">{infoMessage}</div>}
            </div>
          </div>

          <div className="col-lg-8">
            <div className="panel">
              <div className="eyebrow">{editingJobId ? "Update Job" : "Create Job"}</div>
              <h2 className="h4 mb-3">Scheduler request</h2>
              <form onSubmit={handleJobSubmit}>
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label">Job Type</label>
                    <select className="form-select" name="jobName" value={jobForm.jobName} onChange={handleFieldChange} disabled={!session}>
                      {visibleJobOptions.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">Schedule Type</label>
                    <select className="form-select" name="scheduleType" value={jobForm.scheduleType} onChange={handleFieldChange} disabled={!session}>
                      {SCHEDULE_OPTIONS.map((option) => <option key={option} value={option}>{option}</option>)}
                    </select>
                  </div>
                  {jobForm.scheduleType === "DELAYED" && (
                    <div className="col-12">
                      <label className="form-label">Delay Seconds</label>
                      <input className="form-control" name="delaySeconds" value={jobForm.delaySeconds} onChange={handleFieldChange} disabled={!session} />
                    </div>
                  )}
                  {jobForm.scheduleType === "ONE_TIME" && (
                    <div className="col-12">
                      <label className="form-label">Run At</label>
                      <input type="datetime-local" className="form-control" name="runAt" value={jobForm.runAt} onChange={handleFieldChange} disabled={!session} />
                    </div>
                  )}
                  {jobForm.scheduleType === "CRON" && (
                    <>
                      <div className="col-12">
                        <label className="form-label">Repeat</label>
                        <select className="form-select" name="cronMode" value={jobForm.cronMode} onChange={handleFieldChange} disabled={!session}>
                          {CRON_MODES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                        </select>
                      </div>

                      {jobForm.cronMode === "DAILY_AT_TIME" && (
                        <div className="col-12">
                          <label className="form-label">Time (UTC)</label>
                          <input type="time" className="form-control" name="cronTime" value={jobForm.cronTime} onChange={handleFieldChange} disabled={!session} />
                        </div>
                      )}

                      {jobForm.cronMode === "EVERY_HOUR" && (
                        <div className="col-12">
                          <label className="form-label">Minute of the hour</label>
                          <input type="number" min="0" max="59" className="form-control" name="cronMinute" value={jobForm.cronMinute} onChange={handleFieldChange} disabled={!session} />
                        </div>
                      )}

                      {jobForm.cronMode === "EVERY_N_HOURS" && (
                        <>
                          <div className="col-md-6">
                            <label className="form-label">Every how many hours</label>
                            <input type="number" min="1" max="23" className="form-control" name="cronHourInterval" value={jobForm.cronHourInterval} onChange={handleFieldChange} disabled={!session} />
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Minute of the hour</label>
                            <input type="number" min="0" max="59" className="form-control" name="cronMinute" value={jobForm.cronMinute} onChange={handleFieldChange} disabled={!session} />
                          </div>
                        </>
                      )}

                      {jobForm.cronMode === "WEEKLY_AT_TIME" && (
                        <>
                          <div className="col-md-6">
                            <label className="form-label">Day</label>
                            <select className="form-select" name="cronWeekday" value={jobForm.cronWeekday} onChange={handleFieldChange} disabled={!session}>
                              {WEEKDAY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                            </select>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Time (UTC)</label>
                            <input type="time" className="form-control" name="cronTime" value={jobForm.cronTime} onChange={handleFieldChange} disabled={!session} />
                          </div>
                        </>
                      )}

                      {jobForm.cronMode === "CUSTOM" && (
                        <div className="col-12">
                          <label className="form-label">Cron Expression</label>
                          <input className="form-control" name="cronExpression" value={jobForm.cronExpression} onChange={handleFieldChange} disabled={!session} />
                          <div className="small text-secondary mt-2">
                            Format: `minute hour day-of-month month day-of-week`
                          </div>
                          <div className="small text-secondary">
                            Example: `5 * * * *` (runs at minute 5 every hour)
                          </div>
                          <pre className="small text-secondary mb-0 mt-2">
{`│ │ │ │ │
│ │ │ │ └── Day of week (0–7)
│ │ │ └──── Month (1–12)
│ │ └────── Day of month (1–31)
│ └──────── Hour (0–23)
└────────── Minute (0–59)`}
                          </pre>
                        </div>
                      )}
                      <div className="col-12">
                        <div className="alert alert-light border mb-0 small">
                          {cronScheduleSummary(jobForm)}
                        </div>
                      </div>
                    </>
                  )}
                  <div className="col-md-6">
                    <label className="form-label">Max Retries</label>
                    <input className="form-control" name="maxRetries" value={jobForm.maxRetries} onChange={handleFieldChange} disabled={!session} />
                  </div>
                </div>

                {adminBulkAllowed && (
                  <div className="form-check form-switch mt-3">
                    <input className="form-check-input" type="checkbox" name="bulkMode" checked={jobForm.bulkMode} onChange={handleFieldChange} />
                    <label className="form-check-label">Admin bulk mode</label>
                  </div>
                )}

                {selectedJob === "SEND_EMAIL" && (
                  <div className="mt-3">
                    {session?.role === "ADMIN" && jobForm.bulkMode ? (
                      <>
                        <label className="form-label">Recipient Emails</label>
                        <textarea className="form-control textarea-mid" name="emails" value={jobForm.emails} onChange={handleFieldChange} disabled={!session} />
                      </>
                    ) : (
                      <>
                        <label className="form-label">Recipient Email</label>
                        <input className="form-control" name="email" value={jobForm.email} onChange={handleFieldChange} disabled={!session} />
                      </>
                    )}
                    <label className="form-label mt-3">Subject</label>
                    <input className="form-control" name="emailSubject" value={jobForm.emailSubject} onChange={handleFieldChange} disabled={!session} />
                    <label className="form-label mt-3">Email body</label>
                    <textarea className="form-control textarea-mid" name="emailBody" value={jobForm.emailBody} onChange={handleFieldChange} disabled={!session} />
                  </div>
                )}

                {selectedJob === "SEND_NOTIFICATION" && (
                  <div className="mt-3">
                    {session?.role === "ADMIN" && jobForm.bulkMode ? (
                      <>
                        <div className="form-check form-switch mb-2">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            name="sendToAll"
                            checked={jobForm.sendToAll}
                            onChange={handleFieldChange}
                          />
                          <label className="form-check-label">Send to all users</label>
                        </div>
                        <label className="form-label">Recipient User IDs</label>
                        <textarea
                          className="form-control textarea-mid"
                          name="userIds"
                          value={jobForm.userIds}
                          onChange={handleFieldChange}
                          disabled={!session || jobForm.sendToAll}
                        />
                      </>
                    ) : (
                      <>
                        <label className="form-label">Recipient User ID</label>
                        <input className="form-control" name="userId" value={session?.role === "ADMIN" ? jobForm.userId : session?.userId || ""} onChange={handleFieldChange} disabled={!session || session?.role !== "ADMIN"} />
                      </>
                    )}
                    <label className="form-label mt-3">Title</label>
                    <input className="form-control" name="title" value={jobForm.title} onChange={handleFieldChange} disabled={!session} />
                    <label className="form-label mt-3">Message</label>
                    <textarea className="form-control textarea-mid" name="message" value={jobForm.message} onChange={handleFieldChange} disabled={!session} />
                  </div>
                )}

                {selectedJob === "FETCH_NEWS_HEADLINES" && (
                  <div className="mt-3">
                    <label className="form-label">Category</label>
                    <select className="form-select" name="newsCategory" value={jobForm.newsCategory} onChange={handleFieldChange} disabled={!session}>
                      {NEWS_CATEGORIES.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                    <label className="form-label">Headline Count</label>
                    <input className="form-control" name="newsLimit" value={jobForm.newsLimit} onChange={handleFieldChange} disabled={!session} />
                  </div>
                )}

                {selectedJob === "GENERATE_REPORT" && (
                  <div className="mt-3">
                    <label className="form-label">Window</label>
                    <select className="form-select" name="reportWindow" value={jobForm.reportWindow} onChange={handleFieldChange} disabled={!session}>
                      {REPORT_WINDOWS.map((option) => <option key={option} value={option}>{option}</option>)}
                    </select>
                  </div>
                )}

                <div className="d-flex gap-2 mt-4">
                  <button className="btn btn-dark" disabled={!session || loading.submit}>
                    {loading.submit ? "Saving..." : editingJobId ? "Update Job" : "Create Job"}
                  </button>
                  {editingJobId && (
                    <button type="button" className="btn btn-outline-secondary" onClick={() => { setEditingJobId(null); setJobForm(defaultForm()); }}>
                      Cancel edit
                    </button>
                  )}
                </div>
              </form>
            </div>

            {session && (
              <div className="panel mt-4">
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <div>
                    <div className="eyebrow">Live activity</div>
                    <h2 className="h5 mb-0">Results from the worker</h2>
                  </div>
                  <span className={`badge ${realtime.status === "live" ? "text-bg-success" : "text-bg-secondary"}`}>
                    {realtime.status}
                  </span>
                </div>

                <h3 className="h6">Reminders</h3>
                {realtime.reminders.length === 0 ? (
                  <p className="small text-secondary">No reminders yet. Create a reminder job to see it here.</p>
                ) : (
                  <ul className="list-group mb-3">
                    {realtime.reminders.map((item) => (
                      <li key={item.id} className="list-group-item">
                        <div className="fw-semibold">{item.title}</div>
                        <div className="small">{item.message}</div>
                        <div className="small text-secondary">{formatDate(item.receivedAt)}</div>
                      </li>
                    ))}
                  </ul>
                )}

                <h3 className="h6">News</h3>
                {realtime.news.length === 0 ? (
                  <p className="small text-secondary">No headlines yet. Create a news job to see them here.</p>
                ) : (
                  realtime.news.slice(0, 1).map((batch) => (
                    <div key={batch.id} className="mb-3">
                      <div className="small text-secondary mb-1">
                        {batch.categoryLabel} · {batch.articleCount} headlines · {formatDate(batch.fetchedAt)}
                      </div>
                      <ul className="small mb-0">
                        {(batch.articles || []).map((article, index) => (
                          <li key={`${batch.id}-${index}`}>
                            <a href={article.link} target="_blank" rel="noreferrer">{article.headline}</a>
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))
                )}

                {session.role === "ADMIN" && (
                  <>
                    <h3 className="h6">Reports</h3>
                    {realtime.reports.length === 0 ? (
                      <p className="small text-secondary mb-0">No reports yet.</p>
                    ) : (
                      <ul className="list-group">
                        {realtime.reports.map((report) => (
                          <li key={report.id} className="list-group-item d-flex justify-content-between align-items-center">
                            <span className="small">{report.window} · {report.rowCount} rows · {formatDate(report.generatedAt)}</span>
                            {report.fileName && (
                              <button className="btn btn-outline-dark btn-sm" onClick={() => downloadReport(report.fileName)}>Download</button>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                  </>
                )}
              </div>
            )}

          </div>
        </div>
      </div>

      <div className="toast-container position-fixed top-0 end-0 p-3">
        {realtime.toasts.map((toast) => (
          <div key={toast.id} className="toast show" role="status">
            <div className="toast-header">
              <strong className="me-auto">{toast.title}</strong>
            </div>
            <div className="toast-body">{toast.message}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
