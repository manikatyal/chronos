# Deploy Chronos on Free Tier (Resume-Ready)

This setup deploys all 3 parts publicly:
- `chronos-scheduler` (Java, Render Web Service)
- `chronos-worker` (Java, Render Web Service)
- `chronos-ui` (React static site on Render)

It also provisions:
- Render Postgres (`chronos-db`)
- Render Key Value (`chronos-redis`)

Kafka uses Confluent Cloud free tier.

## 1) Push this repo to GitHub
If not already:
```bash
git init
git add .
git commit -m "Deploy config"
git branch -M main
git remote add origin <your-github-repo-url>
git push -u origin main
```

## 2) Create free Kafka (Confluent Cloud)
1. Create a free cluster on Confluent Cloud.
2. Create topic: `job-execution`.
3. Create API key/secret.
4. Note bootstrap server, e.g. `pkc-xxxxx.ap-south-1.aws.confluent.cloud:9092`.

Important for Java/SASL (set these in Render env vars for **both** scheduler and worker):
- `KAFKA_BOOTSTRAP_SERVERS=<bootstrap-host:port>`
- `SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL`
- `SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN`
- `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username='<api-key>' password='<api-secret>';`

## 3) Deploy via Render Blueprint
1. Go to Render Dashboard -> New -> Blueprint.
2. Connect your GitHub repo.
3. Render detects `render.yaml` at repo root.
4. Click Apply.

## 4) Set/Update required env vars in Render

### `chronos-scheduler`
- `CHRONOS_ALLOWED_ORIGINS=https://chronos-ui.onrender.com`
- `CHRONOS_BOOTSTRAP_ADMIN_PASSWORD=<strong-password>`
- Kafka vars listed in step 2

### `chronos-worker`
- Kafka vars listed in step 2
- Optional real SMTP for actual emails:
  - `MAIL_USERNAME`
  - `MAIL_PASSWORD`
  - `CHRONOS_WORKER_EMAIL_FROM`

## 5) Update cross-service URLs once service names are final
In `render.yaml` these are set with default names:
- `https://chronos-scheduler.onrender.com`
- `https://chronos-worker.onrender.com`
- `https://chronos-ui.onrender.com`

If Render auto-renames due name collision, update env vars accordingly and redeploy.

## 6) Smoke test
1. Open UI URL.
2. Login with bootstrap admin user/password.
3. Create one delayed `SEND_NOTIFICATION` job.
4. Create one `FETCH_NEWS_HEADLINES` with category `STOCKS`.
5. Verify scheduler health:
   - `https://<scheduler>/actuator/health`
6. Verify worker health:
   - `https://<worker>/actuator/health`

## 7) Resume line suggestion
"Deployed a distributed job scheduling platform (Spring Boot microservices + React UI) on Render with managed Postgres/Redis and Kafka event streaming (Confluent Cloud), including JWT auth, real-time WebSocket updates, and resilient async processing."

---

## Notes
- Free web services may sleep on inactivity (cold starts).
- This is expected for free-tier demos.
- For interview/live demos, warm endpoints 1-2 minutes before presenting.
