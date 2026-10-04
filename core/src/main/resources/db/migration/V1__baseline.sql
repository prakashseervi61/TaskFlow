-- Baseline: the TaskFlow v0.2 schema, frozen so Flyway owns the schema from here on.
--
-- Kept in the core module on purpose: both the API and the worker run Flyway and must see
-- exactly the same migrations, so there is a single source of truth on the classpath.
--
-- `if not exists` makes this safe on an existing v0.2 database, which Flyway baselines at
-- version 1 (see spring.flyway.baseline-version) and therefore skips this file.

CREATE TABLE IF NOT EXISTS jobs (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120)  NOT NULL,
    description     VARCHAR(2000) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    queued_at       TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    failure_reason  VARCHAR(500)
);