# Job Scheduler Demo

Spring Boot 3.5.6 + Java 21 project that exposes a single `POST /v1/jobs` API for six realistic production-style jobs:

- `SEND_EMAIL`
- `SEND_NOTIFICATION`
- `RETRY_PAYMENT`
- `ORDER_EXPIRY`
- `HOTEL_NIGHT_AUDIT`
- `DATA_SYNC`
- `FETCH_NEWS_HEADLINES` (India/gold/stock categories via payload.category)

## What is implemented

- One API contract with `jobName`, `scheduleType`, `schedule`, `payload`, and `maxRetries`
- Foolproof schedule validation:
  - `DELAYED` only allows `delaySeconds`
  - `ONE_TIME` only allows future `runAt`
  - `CRON` only allows `cronExpression`
- Handler registry so payload validation stays extensible per job type
- Persistent job storage using JPA entities and PostgreSQL
- Request data stored as schedule JSON plus payload JSON for downstream processing
- Payload validation per job handler
- `FETCH_NEWS_HEADLINES` leverages a `category` field (India latest news, gold price updates, or stock updates) alongside the `limit` parameter.

## Run

```bash
mvn spring-boot:run
```

Configure your database with environment variables:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/job_scheduler
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

Main runtime configuration now lives in `src/main/resources/application.properties`.
Logging configuration now lives in `src/main/resources/logback-spring.xml`.

## Test

```bash
mvn test
```

## Sample request

```json
{
  "jobName": "SEND_EMAIL",
  "scheduleType": "DELAYED",
  "schedule": {
    "delaySeconds": 600
  },
  "payload": {
    "email": "mihir@test.com",
    "template": "WELCOME_EMAIL",
    "userId": "U123"
  },
  "maxRetries": 3
}
```
