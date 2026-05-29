# Chronos Docker Deployment (Local + Free Server)

This guide gives you:
1. Local full-stack run with Docker Compose
2. Push images to Docker Hub/GHCR
3. Run on a free cloud VM (Oracle Always Free) using Docker Compose

---

## 1) Local One-Command Run

From repo root:
```bash
docker compose up -d --build
```

Check:
```bash
docker compose ps
```

Endpoints:
- UI: `http://localhost:5173`
- Scheduler health: `http://localhost:8087/actuator/health`
- Worker health: `http://localhost:8088/actuator/health`

Default admin login:
- user: `admin`
- pass: `admin12345`

Stop:
```bash
docker compose down
```

Reset (including DB data):
```bash
docker compose down -v
```

---

## 2) Build & Push Images

Replace `YOUR_DOCKERHUB_USER`:

```bash
# Scheduler
docker build -t YOUR_DOCKERHUB_USER/chronos-scheduler:latest ./job-scheduler-service
docker push YOUR_DOCKERHUB_USER/chronos-scheduler:latest

# Worker
docker build -t YOUR_DOCKERHUB_USER/chronos-worker:latest ./job-worker-service
docker push YOUR_DOCKERHUB_USER/chronos-worker:latest

# UI
docker build -t YOUR_DOCKERHUB_USER/chronos-ui:latest ./job-console-ui
docker push YOUR_DOCKERHUB_USER/chronos-ui:latest
```

---

## 3) Deploy on Free Server (Oracle Cloud Always Free VM)

## A) Provision VM
- Create Ubuntu VM in Oracle Cloud free tier.
- Open inbound ports in security list:
  - `22`, `80`, `5173`, `8087`, `8088` (or only `80` and reverse-proxy)

## B) Install Docker
```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker
```

## C) Copy compose stack
On VM:
```bash
mkdir -p ~/chronos && cd ~/chronos
```
Copy these from repo:
- `docker-compose.yml`
- `docker/postgres-init/01-init.sql`

If you pushed images and want to avoid server-side builds, replace `build:` blocks with `image:` tags.

## D) Start stack
```bash
docker compose up -d
```

## E) Verify
```bash
docker compose ps
curl http://localhost:8087/actuator/health
curl http://localhost:8088/actuator/health
```

Public test URL:
- `http://<VM_PUBLIC_IP>:5173`

---

## 4) Resume-ready production note
For stronger resume value, migrate Kafka from local container to Confluent Cloud and set these env vars on scheduler+worker:
- `KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SASL_SSL`
- `SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=PLAIN`
- `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG=...`

This shows managed cloud event streaming in your project description.
