const SCHEDULER_BASE = import.meta.env.VITE_SCHEDULER_API_BASE || "/api/scheduler";
const WORKER_BASE = import.meta.env.VITE_WORKER_API_BASE || "/api/worker";
// Same origin as the page by default, so it works behind nginx/ingress on any host.
export const REALTIME_WS_URL = import.meta.env.VITE_REALTIME_WS_URL
  || `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws`;

async function parseBody(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function normalizeError(status, body) {
  if (body && typeof body === "object") {
    const details = Array.isArray(body.details) ? body.details : [];
    return {
      status,
      error: body.error || "Request Failed",
      message: details[0] || body.message || "Something went wrong",
      details,
      body
    };
  }

  return {
    status,
    error: "Request Failed",
    message: typeof body === "string" && body ? body : "Something went wrong",
    details: [],
    body
  };
}

async function request(base, path, options = {}) {
  const response = await fetch(`${base}${path}`, options);
  const body = await parseBody(response);

  if (!response.ok) {
    throw normalizeError(response.status, body);
  }

  return body;
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export const schedulerApi = {
  signUp(payload) {
    return request(SCHEDULER_BASE, "/v1/auth/signup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  login(payload) {
    return request(SCHEDULER_BASE, "/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },
  createJob(payload, token) {
    return request(SCHEDULER_BASE, "/v1/jobs", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(token)
      },
      body: JSON.stringify(payload)
    });
  },
  updateJob(jobId, payload, token) {
    return request(SCHEDULER_BASE, `/v1/jobs/${jobId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...authHeaders(token)
      },
      body: JSON.stringify(payload)
    });
  },
  getJobStatus(jobId, token) {
    return request(SCHEDULER_BASE, `/v1/jobs/${jobId}`, {
      headers: authHeaders(token)
    });
  },
  getJobDetail(jobId, token) {
    return request(SCHEDULER_BASE, `/v1/jobs/${jobId}/detail`, {
      headers: authHeaders(token)
    });
  },
  getJobs(token, page = 0, size = 20) {
    return request(SCHEDULER_BASE, `/v1/jobs?page=${page}&size=${size}`, {
      headers: authHeaders(token)
    });
  },
  deleteJob(jobId, token) {
    return request(SCHEDULER_BASE, `/v1/jobs/${jobId}`, {
      method: "DELETE",
      headers: authHeaders(token)
    });
  },
  getFailedJobs(token, page = 0, size = 20) {
    return request(SCHEDULER_BASE, `/v1/jobs/failed?page=${page}&size=${size}`, {
      headers: authHeaders(token)
    });
  }
};

export const workerApi = {
  getAllExecutions(token) {
    return request(WORKER_BASE, "/v1/executions", { headers: authHeaders(token) });
  },
  getFailedExecutions(token) {
    return request(WORKER_BASE, "/v1/executions/failed", { headers: authHeaders(token) });
  },
  getExecution(jobId, token) {
    return request(WORKER_BASE, `/v1/executions/${jobId}`, { headers: authHeaders(token) });
  },
  // A plain link cannot send the Bearer header, so download through fetch and save the blob.
  async downloadReport(fileName, token) {
    const response = await fetch(`${WORKER_BASE}/v1/reports/failed/${encodeURIComponent(fileName)}`, {
      headers: authHeaders(token)
    });
    if (!response.ok) {
      throw normalizeError(response.status, await parseBody(response));
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }
};

export function realtimeUrl(token) {
  return `${REALTIME_WS_URL}?token=${encodeURIComponent(token)}`;
}

export function errorMessage(error) {
  if (!error) {
    return "Something went wrong";
  }

  return error.message || "Something went wrong";
}
