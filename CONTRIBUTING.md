# Contributing to TaskFlow

Thanks for considering it. This is a small project with the reasoning written down, so there's
not much hidden context to work around — and contributions of every size are welcome, including
your first open-source PR.

Two things help more than anything else: **a focused pull request** and **a test that proves the
behaviour you changed**. Everything else is details.

---

## Quick start

```bash
git clone https://github.com/<your-username>/TaskFlow.git
cd TaskFlow
docker compose up --build
```

That starts PostgreSQL, Redis, the API, the worker and the frontend. Open
<http://localhost:5173>.

```bash
mvn clean package        # build all three Java modules
mvn test                 # 76 tests
cd frontend && npm install
```

---

## The dev loop

| Task | Command |
|---|---|
| Start everything | `docker compose up --build` |
| Run the Java tests | `mvn test` |
| Run the frontend checks | `cd frontend && npm run lint && npm run build` |
| Rebuild one Java module | `mvn -pl worker -am package` |
| Tail worker logs | `docker compose logs -f worker` |
| Inspect the queue | `docker compose exec -T redis redis-cli LLEN taskflow:jobs` |
| Full reset, including data | `docker compose down -v` |

**Run both test commands before every PR.** It's the fastest way to get a review.

### Running without Docker

Needs JDK 21+, Maven, PostgreSQL and Redis.

```bash
psql -U postgres -c "CREATE ROLE taskflow LOGIN PASSWORD 'taskflow';"
psql -U postgres -c "CREATE DATABASE taskflow OWNER taskflow;"

mvn clean package

export DB_HOST=localhost DB_NAME=taskflow DB_USERNAME=taskflow DB_PASSWORD=taskflow
export REDIS_HOST=localhost
java -jar backend/target/taskflow-backend-0.3.0.jar   # terminal 1
java -jar worker/target/taskflow-worker-0.3.0.jar     # terminal 2
cd frontend && npm install && npm run dev             # terminal 3
```

Flyway creates the schema on first boot. There is no seeding step.

---

## Where things live

```
taskflow/
├── core/       domain model, repository queries, JobHandler contract, Flyway migrations
├── backend/    REST API — saves jobs and publishes ids, never executes work
├── worker/     queue consumer — claims jobs, runs handlers, settles the outcome
├── frontend/   React dashboard
└── docs/images/ README assets
```

`core` is shared by `backend` and `worker` on purpose: both must agree on the table, the statuses
and the claim queries. See **Design decisions** in the [README](README.md) for why.

---

## Good first contributions

### Add a job type ⭐

The best way to learn the codebase. Four small edits, no new concepts:

**1. Add the constant** — `core/src/main/java/com/taskflow/entity/JobType.java`

```java
public enum JobType {
    SIMULATED,
    PING,
    FAIL,
    HTTP_CALL     // ← yours
}
```

**2. Implement the handler** — `worker/src/main/java/com/taskflow/worker/handler/HttpCallJobHandler.java`

```java
public class HttpCallJobHandler implements JobHandler {

    @Override
    public JobType type() {
        return JobType.HTTP_CALL;
    }

    @Override
    public void execute(JobExecutionContext context) throws Exception {
        // context gives you jobId, payload, attempt, maxAttempts, attemptsLeft
    }
}
```

**3. Register it** — `worker/src/main/java/com/taskflow/worker/config/JobHandlerConfig.java`

```java
@Bean
public JobHandler httpCallJobHandler(WorkerProperties properties) {
    return new HttpCallJobHandler(/* ... */);
}
```

**4. Make it selectable** — `frontend/src/lib/status.js`

```js
export const JOB_TYPES = ['SIMULATED', 'PING', 'FAIL', 'HTTP_CALL']
```

Optionally add a one-line description in `frontend/src/components/CreateJobForm.jsx`.

**Throwing from `execute()` is how you report failure.** The worker decides whether that attempt
retries or dead-letters, so you don't implement retry logic yourself.

Start from `SimulatedJobHandler` — it's about fifteen lines.

### Other easy wins

| Contribution | Notes |
|---|---|
| Add a status colour or empty state | Frontend-only, instantly visible |
| Add a config option to `docker-compose.yml` | Isolated and low risk |
| Cover an untested edge case | Self-contained and genuinely useful |
| Improve an error message | Small changes are welcome, not filler |
| Add a metric or a log field | See the Observability roadmap items |

---

## Working on a bigger item

The [roadmap](README.md#roadmap) lists what needs doing, grouped by category. A few notes:

**The lease/reclaim work** is the top correctness gap: a worker that dies after claiming leaves a
job in `RUNNING` forever, because recovery only scans `QUEUED`. If you take this on, please read
the two related limitations first — reclaim without a fencing token just converts a stuck job into
duplicate side effects, so the two are really one piece of work.

**Redis Streams** would replace at-least-once delivery with ack semantics. It touches the queue
reader and the retry scheduler, so it's a bigger change than it looks — worth discussing in an
issue before you start.

**Please open an issue before large changes.** A short conversation is cheaper than a PR that
doesn't fit the design.

---

## Code style

Match what's already there. The codebase is deliberately plain:

- **Java 21, Spring Boot 3.4.** Records over classes for small value types. Constructor injection,
  no field injection.
- **Comments explain *why*, not *what*.** The code leans on its own naming. If you find yourself
  commenting what a line does, the line probably needs a better name instead.
- **Javadoc on anything non-obvious**, especially a constraint someone would otherwise "clean up"
  and break.
- **Frontend is plain React** — function components and hooks, Tailwind for styling. No state
  library; `useState` and a polling hook are enough at this size.
- **No new dependencies without discussion.** They're hard to justify in a queue that's meant to
  teach how queues work.

### Tests

- JUnit 5 and AssertJ. `mvn test` must pass — all 76.
- Cover the **unhappy path**. Most of the suite exists because an outage or a duplicate delivery
  broke something at least once.
- Name the test after the behaviour, not the method:
  `scheduleRetry_rejectsUpdate_whenJobIsNoLongerRunning`, not `testScheduleRetry2`.

---

## Pull requests

- One concern per PR. Drive-by refactors in a feature PR make review harder, not easier.
- Add a line to the README if you change behaviour, config or an API response.
- Add a roadmap checkbox only when the work is genuinely done.
- Write the description for someone who hasn't read the issue: what changed, why, and how you
  verified it.
- Screenshots for anything visible in the UI.

Maintainers may ask for changes. That's normal and not a sign the work was unwanted — it's how the
code stays consistent.

---

## Reporting a bug

Open an issue with:

- **What you expected** and **what happened**
- **Steps to reproduce** — a failing test or a `curl` command is ideal
- Your OS, JDK version, and whether you used Docker

If you found a security issue, please don't open a public issue — email
**prakashseervi1503@gmail.com** instead.

---

## Code of conduct

Be decent. Assume good faith, critique the code rather than the person, and help newcomers get
their first PR merged. Harassment or personal attacks aren't tolerated and will result in being
blocked.

---

## License

**TaskFlow does not have a license file yet.** Until one is added, default copyright applies:
nobody may legally reuse the code, and contributions land in a legal grey area.

MIT is the obvious intent for a project like this, and adding it is a small, high-value PR that
anyone is welcome to send.
