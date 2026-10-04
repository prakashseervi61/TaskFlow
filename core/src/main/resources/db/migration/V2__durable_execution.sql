-- Durable execution: attempts, backoff scheduling, job types and payloads.

-- Attempt tracking. attempt_count is incremented by the worker's claim, so it counts
-- attempts actually started rather than attempts requested.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS attempt_count  INTEGER      NOT NULL DEFAULT 0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS max_attempts   INTEGER      NOT NULL DEFAULT 3;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS last_error     VARCHAR(1000);

-- Backoff gate: a retried job waits in QUEUED until this instant.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;

-- Job types select the handler that runs the job; payload is its input.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_type VARCHAR(40) NOT NULL DEFAULT 'SIMULATED';
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS payload   JSONB;

-- Serves the keyset-paginated list (status filter + created_at/id ordering).
CREATE INDEX IF NOT EXISTS idx_jobs_status_created
    ON jobs (status, created_at DESC, id DESC);

-- Find retried jobs whose backoff has elapsed (sweeper / startup recovery).
CREATE INDEX IF NOT EXISTS idx_jobs_next_attempt
    ON jobs (next_attempt_at)
    WHERE status = 'QUEUED';