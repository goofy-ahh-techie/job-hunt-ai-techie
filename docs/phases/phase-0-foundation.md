# Phase 0 — Foundation & Scaffolding

**Status:** ✅ Complete
**Focus:** Stand up the monorepo, backend skeleton, and database infrastructure so
every later phase has a stable base to build on.

---

## What we built

- **Monorepo layout** — three cooperating services in one repo:
  `jobgasp-backend/` (Spring Boot), `intelligence-python/` (FastAPI), `infra/` (Docker Compose).
- **Backend skeleton** — `pom.xml` on Java 25 + Spring Boot 3.5 with all dependencies
  declared up front; a single `application.yaml`.
- **`ApiResponse<T>`** — one generic envelope every endpoint returns.
- **Global exception handler** — a `@ControllerAdvice` that owns all error-to-response mapping.
- **Database infrastructure** — `infra/docker-compose.yml` running PostgreSQL 16 alongside
  the backend and intelligence services.
- **Liquibase changesets 001–004** — `app_ping` (+ seed), `raw_resume`, `raw_jd`.

---

## Why we built it this way

- **Monorepo over multi-repo** — this is a solo portfolio project; one repo keeps the
  backend, AI service, and infra versioned together and reviewable in a single diff.
- **`ApiResponse<T>` from day one** — deciding the response contract before writing any
  endpoint means clients (and the future React frontend) never have to special-case shapes.
  Retrofitting a wrapper later would touch every controller.
- **Centralized exception handling** — keeping error mapping in one `@ControllerAdvice`
  stops try/catch from leaking into controllers and guarantees errors come back in the
  same envelope as successes.
- **Liquibase with guarded changesets** — each changeset has a `not tableExists`
  precondition with `onFail: MARK_RAN`, so re-running against an existing DB is safe and
  idempotent. Schema is versioned and reproducible, not hand-applied.
- **Docker Compose for the DB** — a pinned PostgreSQL 16 image gives every environment
  the same database, avoiding "works on my machine" drift.

---

## Problems we faced & how we fixed them

### 1. Timezone rejected at the JDBC connection layer
A Windows JVM resolves its default zone as `Asia/Calcutta`. pgjdbc sends the timezone in
its **connection startup packet**, and PostgreSQL 16's tzdata rejects that value with
`FATAL: invalid value for parameter "TimeZone"` — **before any Spring property is read**.

**Fix:** force the JVM default to UTC in code
(`JobHuntBackendApplication.applyDefaultTimeZone()`) rather than in `application.yaml`,
and set `hibernate.jdbc.time_zone` so Hibernate writes timestamps in UTC. This had to
live in Java because the failure happens during connection handshake, upstream of config.
An explicit `-Duser.timezone` still overrides it.

### 2. Native Postgres shadowing the container (local gotcha)
A native Windows **PostgreSQL 14** service also listens on 5432 and shadows the container
for host-run processes.

**Fix (workaround):** run the backend via Docker Compose, or stop the native service
before running from the host. Documented so it doesn't cost debugging time later.

---

## What we deferred to later phases

- **No dev/test/prod profile split** — `application.yaml` is the only config file for now.
- **No GitHub Actions CI pipeline** yet, despite this being on the Phase 0 wishlist.
- **`raw_resume.user_id` is nullable and unconstrained** — there are no users yet, so the
  foreign key can't exist until Phase 1 creates the `users` table.

---

## Metrics — current functionality

| Metric                        | Value                                   |
|-------------------------------|-----------------------------------------|
| Docker Compose services       | 3 (postgres, backend, intelligence)     |
| Liquibase changesets applied  | 4 / 4 (001–004)                         |
| Tables created                | `app_ping`, `raw_resume`, `raw_jd`      |
| Response envelope             | `ApiResponse<T>` on all endpoints       |
| Timezone correctness          | JVM + Hibernate forced to UTC           |
