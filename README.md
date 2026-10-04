# TaskFlow — Job Processing Platform (v0.3)

A full-stack job processing app: create jobs, run them through an asynchronous Redis queue, and
watch retries and dead letters happen in real time.

| Release | What changed |
| ------- | ------------ |
| **v0.3** | Flyway migrations, retries with exponential backoff, dead-letter queue, pluggable job handlers with payloads, keyset pagination |
| v0.2 | Redis queue + separate worker service, graceful Redis/worker failure handling, Docker Compose |
| v0.1 | CRUD + simulated in-process execution |

```
React  ─▶ Backend API ─▶ PostgreSQL ──(LPUSH, after commit)──▶ Redis ──(BRPOP)──▶ Worker ─▶ PostgreSQL
                                    taskflow:jobs                       taskflow:jobs:scheduled
                                    taskflow:jobs:dlq
```

```
PENDING ──Run──▶ QUEUED ──claim(attempt+1)──▶ RUNNING ──┬─▶ COMPLETED
                   ▲                                     │
                   └──── attempts left ───────┐          └─▶ FAILED
                                               └─ exhausted ──▶ FAILED + dead letter
```

| Layer    | Stack                                            |
| -------- | ------------------------------------------------ |
| Core     | Java 21 · Spring Data JPA (shared domain model)  |
| Backend  | Java 21 · Spring Boot 3.4 · Maven · Flyway       |
| Worker   | Java 21 · Spring Boot 3.4 · Spring Data Redis    |
| Database | PostgreSQL                                      |
| Queue    | Redis 7                                         |
| Frontend | React 19 · Vite · Tailwind CSS 4                |

---

## 1. Project structure

```
taskflow/
├── pom.xml                       # aggregator: parent + modules core, backend, worker
│
├── core/                         # domain shared by the API and the worker
│   └── src/main/
│       ├── java/com/taskflow/
│       │   ├── config/           # TaskflowProperties, RetryProperties
│       │   ├── dto/              # CreateJobRequest, JobResponse, JobStatsResponse,
│       │   │                     # JobPageResponse, ApiError
│       │   ├── entity/           # Job, JobStatus, JobType
│       │   ├── exception/        # JobNotFound, InvalidJobState
│       │   ├── handler/          # JobHandler, JobExecutionContext
│       │   ├── repository/JobRepository.java
│       │   └── retry/            # BackoffPolicy, RetryConfig
│       └── resources/db/migration/   # Flyway, shared by both services
│           ├── V1__baseline.sql
│           └── V2__durable_execution.sql
│
├── backend/                      # REST API
│   └── src/main/
│       ├── java/com/taskflow/
│       │   ├── TaskflowApplication.java
│       │   ├── config/{CorsConfig,AsyncConfig}.java
│       │   ├── controller/JobController.java
│       │   ├── exception/GlobalExceptionHandler.java
│       │   ├── queue/{JobQueue,RedisJobQueue,QueueUnavailableException}.java
│       │   └── service/
│       │       ├── JobService.java             # CRUD, paging, retry rules
│       │       ├── PageCursor.java             # opaque keyset cursor
│       │       ├── JobExecutionRequested.java  # the event, unchanged since v0.2
│       │       ├── JobExecutionDispatcher.java # after-commit → LPUSH
│       │       └── JobReleaseService.java      # QUEUED → PENDING if Redis is down
│       └── resources/application.yml
│
├── worker/                       # queue consumer
│   └── src/main/
│       ├── java/com/taskflow/worker/
│       │   ├── WorkerApplication.java
│       │   ├── config/{WorkerProperties,WorkerExecutorConfig,JobHandlerConfig}.java
│       │   ├── handler/{SimulatedJobHandler,PingJobHandler,FailingJobHandler}.java
│       │   └── job/
│       │       ├── JobQueueWriter.java         # enqueue / schedule / dead letter
│       │       ├── RedisJobQueueReader.java    # BRPOP
│       │       ├── JobWorkerConsumer.java      # poll loop
│       │       ├── JobExecutor.java            # claim → run → settle
│       │       ├── AttemptOutcome.java
│       │       ├── RetryScheduler.java         # promotes due retries
│       │       └── QueuedJobRecovery.java      # startup re-publish
│       └── resources/application.yml
│
├── frontend/
│   └── src/{api,components,hooks,lib,pages}
│
├── docker/Dockerfile             # one image definition, MODULE build arg
├── docker-compose.yml            # postgres + redis + backend + worker + frontend
├── .dockerignore
├── README.md
└── .gitignore
```

### Why a `core` module

The API and the worker must agree on the `jobs` table, the `JobStatus` values, the job types
and the conditional claim queries. `core` holds those once; both services depend on it, and the
Flyway migrations live in its resources so both services run the same DDL.

---

## 2. Quick start

```bash
docker compose up --build
```

| Service    | URL                                  |
| ---------- | ------------------------------------ |
| Frontend   | http://localhost:5173                |
| API        | http://localhost:8080/api/jobs       |
| API health | http://localhost:8080/actuator/health |

The worker publishes no host port — it is a consumer, and replicas must not fight over one.
Watch it with `docker compose logs -f worker`.

Scale out; the database claim decides which replica owns a given job:

```bash
docker compose up -d --scale worker=3
```

Teardown: `docker compose down` (add `-v` to drop volumes).

If a host port is already taken, override it:

```bash
REDIS_PORT=6380 POSTGRES_PORT=5434 docker compose up -d
```

---

## 3. Running locally without Docker

```bash
psql -U postgres -c "CREATE ROLE taskflow LOGIN PASSWORD 'taskflow';"
psql -U postgres -c "CREATE DATABASE taskflow OWNER taskflow;"

mvn clean package        # builds core, backend, worker
```

**Backend** — port 8080

```bash
export DB_HOST=localhost DB_PORT=5432 DB_NAME=taskflow
export DB_USERNAME=taskflow DB_PASSWORD=taskflow
export REDIS_HOST=localhost REDIS_PORT=6379

java -jar backend/target/taskflow-backend-0.3.0.jar
```

**Worker** — health endpoint on 8081

```bash
# same DB_* and REDIS_* variables
export JOB_SIMULATED_DURATION_MS=2500 WORKER_CONCURRENCY=2

java -jar worker/target/taskflow-worker-0.3.0.jar
```

**Frontend** — port 5173

```bash
cd frontend && npm install && npm run dev
```

**Tests**

```bash
mvn test            # 77 tests across core, backend, worker
cd frontend && npm run lint && npm run build
```

---

## 4. Configuration

### Backend

| Variable                   | Required | Default                 |
| -------------------------- | -------- | ----------------------- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` | no | `localhost` / `5432` / `taskflow` / `postgres` | |
| `DB_PASSWORD`              | **yes**  | —                       |
| `REDIS_HOST` / `REDIS_PORT` | no     | `localhost` / `6379`    |
| `REDIS_PASSWORD` / `REDIS_DATABASE` | no | empty / `0`   |
| `REDIS_TIMEOUT`            | no       | `2s`                    |
| `JOB_QUEUE_KEY`            | no       | `taskflow:jobs`         |
| `JOB_DEFAULT_MAX_ATTEMPTS` | no       | `3`                     |
| `JOB_RETRY_BASE_DELAY`     | no       | `5s`                    |
| `JOB_RETRY_MAX_DELAY`      | no       | `5m`                    |
| `SERVER_PORT`              | no       | `8080`                  |
| `CORS_ALLOWED_ORIGINS`     | no       | `http://localhost:5173` |

### Worker

| Variable                    | Default          | Notes                                 |
| --------------------------- | ---------------- | ------------------------------------- |
| `DB_*`, `REDIS_*`           | —                | Must match the API's                  |
| `JOB_QUEUE_KEY`              | `taskflow:jobs`  | Must match the API's                  |
| `JOB_SIMULATED_DURATION_MS`  | `2500`           | Stands in for real work; `0` is instant |
| `JOB_DEFAULT_MAX_ATTEMPTS`   | `3`              |                                         |
| `JOB_RETRY_BASE_DELAY`       | `5s`             | Doubles per failed attempt             |
| `JOB_RETRY_MAX_DELAY`        | `5m`             | Ceiling for the doubling               |
| `WORKER_CONCURRENCY`         | `2`              | Jobs per instance at once              |
| `WORKER_POLL_TIMEOUT`        | `5s`             | Blocking read timeout                  |
| `WORKER_RETRY_SCAN_INTERVAL_MS` | `1000`       | How often due retries are promoted     |
| `WORKER_FAILURE_MESSAGE`     | `Job failed on purpose` | Message from the FAIL type       |
| `WORKER_RECOVER_ON_STARTUP`  | `true`           | Re-publish stranded QUEUED jobs        |
| `WORKER_SERVER_PORT`         | `8081`           | Serves `/actuator/health`              |

Copy `backend/.env.example` and `worker/.env.example` as a starting point. No password is
hardcoded.

---

## 5. API

Base URL `http://localhost:8080`.

| Method   | Path                      | Success | Errors      | Description                       |
| -------- | ------------------------- | ------- | ----------- | --------------------------------- |
| `POST`   | `/api/jobs`               | `201`   | `400`       | Create a job (`PENDING`)          |
| `GET`    | `/api/jobs`               | `200`   | —           | One page, newest first            |
| `GET`    | `/api/jobs/{id}`          | `200`   | `404`       | Job details                       |
| `GET`    | `/api/jobs/stats`         | `200`   | —           | Counts per status                 |
| `GET`    | `/api/jobs/dead-letter`   | `200`   | —           | Jobs whose retries were exhausted |
| `POST`   | `/api/jobs/{id}/execute`  | `200`   | `404`, `409`| Queue a PENDING or retryable FAILED job |
| `POST`   | `/api/jobs/{id}/requeue`  | `200`   | `404`, `409`| Reset a FAILED job to PENDING with a fresh budget |
| `DELETE` | `/api/jobs/{id}`          | `204`   | `404`       | Delete a job                      |

**Create request**

| Field         | Type   | Rules                                              |
| ------------- | ------ | -------------------------------------------------- |
| `name`        | string | required, 1–120 chars                              |
| `description` | string | required, 1–2000 chars                             |
| `jobType`     | string | optional; `SIMULATED` (default), `PING`, `FAIL`    |
| `payload`     | object | optional free-form handler input                   |
| `maxAttempts` | int    | optional 1–10, defaults to 3                       |

An unknown `jobType` is a `400` listing the supported types.

**List query parameters**

| Parameter | Type | Default | Notes                                        |
| --------- | ---- | ------- | -------------------------------------------- |
| `limit`   | int  | `20`    | clamped to 100                              |
| `cursor`  | string | —      | `nextCursor` from a previous page           |

**Job response**

```json
{
  "id": 1,
  "name": "nightly-report",
  "description": "Builds and emails the daily report",
  "status": "QUEUED",
  "jobType": "SIMULATED",
  "payload": { "region": "eu" },
  "createdAt": "2026-01-01T10:00:00Z",
  "queuedAt": "2026-01-01T10:00:01Z",
  "startedAt": null,
  "completedAt": null,
  "nextAttemptAt": "2026-01-01T10:00:06Z",
  "attemptCount": 1,
  "maxAttempts": 3,
  "attemptsLeft": 2,
  "failureReason": "IllegalStateException: upstream timeout",
  "lastError": "IllegalStateException: upstream timeout"
}
```

**List response** (breaking change in v0.3 — it was a bare array in v0.2)

```json
{
  "items": [ /* JobResponse */ ],
  "nextCursor": "MTc2NzIyNjQ0MDAwMHw0Mg",
  "hasMore": true,
  "total": 39
}
```

**Stats response**

```json
{ "total": 39, "pending": 2, "queued": 0, "running": 0,
  "completed": 36, "failed": 1, "retrying": 0, "activeCount": 0 }
```

`activeCount` is `queued + running`; the UI polls while it is non-zero.

**Error body** and status codes are unchanged from v0.2: `400` validation or unknown job type,
`404` missing job, `409` wrong state for the transition, `500` unexpected.

---

## 6. Example requests

```bash
BASE=http://localhost:8080/api/jobs

# Create
curl -i -X POST "$BASE" -H "Content-Type: application/json" \
  -d '{"name":"nightly-report","description":"Builds the daily report"}'

# Create a job that always fails, so retries and the dead letter queue can be exercised
curl -X POST "$BASE" -H "Content-Type: application/json" \
  -d '{"name":"doomed","description":"always fails","jobType":"FAIL","maxAttempts":3}'

# Unknown job type -> 400
curl -i -X POST "$BASE" -H "Content-Type: application/json" \
  -d '{"name":"x","description":"y","jobType":"NOPE"}'

# Run, then watch it progress
curl -X POST "$BASE/1/execute"     # -> QUEUED, then RUNNING, then COMPLETED
curl "$BASE/1"

# Paging
curl "$BASE?limit=10"
curl "$BASE?limit=10&cursor=MTc2NzIyNjQ0MDAwMHw0Mg"

# Counts and dead letters
curl "$BASE/stats"
curl "$BASE/dead-letter"

# Give a dead-lettered job a fresh budget, then run it
curl -X POST "$BASE/8/requeue"
curl -X POST "$BASE/8/execute"

# Errors and deletion
curl -i "$BASE/999999"                  # 404
curl -i -X POST "$BASE/1/execute"       # 409 once no longer PENDING/retryable
curl -i -X DELETE "$BASE/1"             # 204
```

Inspect the queue directly:

```bash
redis-cli LLEN   taskflow:jobs             # waiting for a worker
redis-cli ZCARD  taskflow:jobs:scheduled   # waiting out a backoff
redis-cli LRANGE taskflow:jobs:dlq 0 -1    # dead lettered ids
```

---

## 7. How it works

### Retries

1. `JobExecutor` claims the job with a conditional UPDATE that also increments `attempt_count`,
   so the count reflects attempts actually started.
2. It resolves a `JobHandler` from the job's `jobType` and runs it. Throwing is how a handler
   reports failure.
3. On failure, `BackoffPolicy` computes `min(base × 2^(attempt-1), max)` and the job is either
   - **attempts left** → `scheduleRetry`: back to `QUEUED` with `next_attempt_at`, and the id is
     scored into the `taskflow:jobs:scheduled` sorted set;
   - **attempts exhausted** → `markFailed` and the id is pushed to `taskflow:jobs:dlq`.
4. `RetryScheduler` ticks every second and promotes entries whose score has passed onto the main
   list. A waiting retry therefore occupies Redis, not a consumer slot.

Backoff is measured from attempt 1: `5s`, `10s`, `20s`, … capped at `5m`.

### Preventing duplicate execution

Two conditional UPDATEs, both atomic:

| Guard | Where | Prevents |
| ----- | ----- | -------- |
| `claimForQueueing` — `WHERE status='PENDING' OR (status='FAILED' AND attemptCount < maxAttempts)` | API | the same job being queued twice |
| `claimForExecution` — `WHERE status='QUEUED'`, increments `attempt_count` | Worker | the same job being executed twice |

Redis gives at-least-once delivery and a blocking pop removes the id before the work is done, so
neither guard is optional. Verified: 12 jobs across 3 worker replicas produced exactly 12
executions and zero double-runs.

### Why a sorted set for the schedule

Redis has no "push at time T". A sorted set scored by the eligible instant gives an ordered,
atomically removable due-set in one call (`ZRANGEBYSCORE`), which is exactly what promoting due
retries needs.

### Graceful degradation

| Failure | Behaviour |
| ------- | --------- |
| **Redis down, API side** | The publish fails, the job is released back to `PENDING` with `failureReason`, and the user can press Run again. The response is still `200` — an after-commit callback cannot change an HTTP status, so the outcome is reported in the data and the logs. |
| **Redis down, worker side** | The consume loop logs, backs off 5s and keeps polling. `RetryScheduler` tolerates it too. Nothing crashes. |
| **Worker down** | Jobs sit in `QUEUED` with their ids in Redis. `QueuedJobRecovery` re-publishes every `QUEUED` row on worker startup; the claim guard makes a duplicate push harmless. |
| **Worker dies mid-job** | Same recovery path on the next startup; `attempt_count` prevents the attempt from being counted twice. |
| **Dead-letter push fails** | The row is already terminally `FAILED`, and the API lists dead letters from PostgreSQL, so only the Redis-side view is affected. |
| **Unknown `jobType` in the DB** | Fails terminally rather than retrying forever on a condition that cannot change. |

### Schema management

Flyway owns the schema; Hibernate runs with `ddl-auto: validate`, so it can verify the mapping but
never alter it. Migrations live in `core/src/main/resources/db/migration`, so the API and the
worker apply the same DDL.

`baseline-on-migrate: true` with `baseline-version: 1` adopts a database created by v0.2 (which
had no Flyway history): `V1__baseline` is treated as applied and only
`V2__durable_execution` runs. A fresh database runs both.

### Pagination

Keyset (cursor) pagination on `(created_at DESC, id DESC)`. The cursor is an opaque
base64 `epochMillis:id` pair; the id tiebreaker keeps rows with identical timestamps from being
skipped or repeated when new jobs arrive mid-scroll. The service over-fetches one row to know
whether a further page exists, so no extra count query is needed.

Two consequences that are easy to get wrong, and are handled:

- **Counts come from the server.** Deriving them from the loaded page would report "20 of 500".
  `JobStatistics` now renders `/api/jobs/stats`.
- **Polling follows `activeCount`, not the page contents.** Otherwise a `QUEUED` job on page 3
  would stop the refresh loop.

### Job handlers

`JobHandler` and `JobExecutionContext` live in `core` so the API can validate a type without
depending on the worker that implements it. `JobType` is an enum — `SIMULATED` (sleeps),
`PING` (instant), `FAIL` (always throws) — so validation needs no registry wiring.

Adding a type is: an enum constant, a `JobHandler` bean, a line in `JobHandlerConfig`.

This replaces the v0.1/v0.2 convention of failing any job whose name started with `fail:`.

---

## 8. Tests

```bash
mvn test
```

| Suite | Covers |
| ----- | ------ |
| `BackoffPolicyTest` | doubling, cap, overflow safety, non-positive attempts |
| `JobServiceTest` | create/trim/type validation, queueing rules, requeue, cursor paging, stats |
| `JobControllerTest` | status codes, page envelope, `400` on bad `jobType`, new endpoints |
| `RedisJobQueueTest` | LPUSH, `QueueUnavailableException` on failure |
| `JobExecutionDispatcherTest` | publishes after commit, releases on queue outage |
| `JobReleaseServiceTest` | `QUEUED → PENDING` with a reason |
| `JobExecutorTest` | claim, handler context, retry vs dead letter, unknown type, Redis outages |
| `RetrySchedulerTest` | promotes due retries, survives Redis outage, tolerates a lost race |
| `QueuedJobRecoveryTest` | re-publishes stranded jobs, reschedules waiting retries |
| `RedisJobQueueReaderTest` | BRPOP, timeout, malformed entry, poll-timeout cap |
| `JobWorkerConsumerTest` | dispatch, survives queue outage and executor crash, clean stop |

---

## 9. Upgrading a v0.1/v0.2 database

Run once if your database predates v0.3:

```sql
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_status_check;
```

v0.1 let Hibernate generate a CHECK constraint freezing the old enum values into the database.
Fresh databases are unaffected — v0.2 onward maps the column explicitly so it is not regenerated.

---

## 10. Roadmap

- [ ] Redis Streams with consumer groups and ack, replacing the BRPOP loop
- [ ] Per-job retry policy (fixed delay, custom cap, no retry)
- [ ] Prometheus metrics and a dashboard for queue depth, latency and failure rate
- [ ] Structured JSON logging with `jobId` context
- [ ] Testcontainers integration tests against real PostgreSQL and Redis
- [ ] Job cancellation while queued or running
- [ ] Authentication and per-user job ownership
- [ ] Kubernetes / cloud deployment