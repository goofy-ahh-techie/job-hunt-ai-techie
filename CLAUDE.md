# Job Hunt AI Copilot — Claude Reference

> This file is the single source of truth for Claude Code sessions inside IntelliJ.
> Update it at the end of each phase before starting the next one.

---

## Project Purpose

AI-powered job search assistant built as a **solo end-to-end portfolio project**.
Primary goal: flagship piece for software engineering interviews demonstrating
backend engineering depth, system design thinking, and production-quality practices.

---

## Tech Stack

| Layer          | Technology                                              |
|----------------|---------------------------------------------------------|
| Backend        | Java 25, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Auth           | JWT (HS256) via jjwt 0.12, BCrypt password hashing      |
| Migrations     | Liquibase (YAML changesets under `db/changelog/`)        |
| Database       | PostgreSQL 15 (Docker Compose)                          |
| AI Service     | Python FastAPI                                          |
| Frontend       | React TypeScript                                        |
| Infrastructure | Docker Compose, GitHub Actions CI                       |

---

## Repository Structure (Monorepo)

```
job-hunt-ai-techie/
├── jobgasp-backend/                # Spring Boot 3 application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/jobhuntai/jobhunt_backend/
│   │   │   │   ├── auth/           # Phase 1: JWT authentication
│   │   │   │   ├── common/         # ApiResponse, exception handler, clients
│   │   │   │   ├── rawresume/      # Raw resume + JD intake
│   │   │   │   ├── resume/         # Phase 2: resume parsing & storage
│   │   │   │   └── user/           # User entity, Role, repository
│   │   │   └── resources/
│   │   │       ├── application.yaml         # Single config file (no profiles yet)
│   │   │       └── db/changelog/            # Liquibase changelog + changesets
│   │   └── test/
│   └── pom.xml
├── intelligence-python/            # Python FastAPI service
│   └── app/
├── infra/
│   ├── docker-compose.yml          # PostgreSQL 16 + backend + intelligence
│   └── .env                        # DB + service env vars (not committed)
├── README.md
└── CLAUDE.md                       # ← You are here
```

---

## Current Phase Status

### ✅ Phase 0 — Foundation (COMPLETE)

Everything below has been generated and confirmed working.

**Scaffolding**
- Monorepo directory structure
- Root `README.md` and `.gitignore`

**Backend**
- `pom.xml` — Java 25, Spring Boot 3.5, all dependencies declared
- Single `application.yaml` (profile split not done yet)
- `ApiResponse<T>` — generic wrapper for all API responses
- Global exception handler

**Database**
- `infra/docker-compose.yml` with PostgreSQL 16
- Liquibase changesets 001–004: `app_ping`, `raw_resume`, `raw_jd`

---

### ✅ Phase 1 — JWT Authentication (COMPLETE)

**Endpoints** (public; everything else now requires a Bearer token)
- `POST /api/v1/auth/register` → 201, returns token; 409 on duplicate email; 400 on validation failure
- `POST /api/v1/auth/login` → 200, returns token; 401 on bad credentials

**Components** (`auth/` package)
- `JwtService` — HS256 generation + verification; signature, issuer, and expiry are all enforced.
  Invalid tokens return empty rather than throwing, so a bad token is an auth miss, not a 500.
- `JwtAuthenticationFilter` — `OncePerRequestFilter` reading the `Authorization: Bearer` header
- `SecurityConfig` — stateless session policy, CSRF disabled, public allowlist, filter wiring
- `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` — emit `ApiResponse` for 401/403.
  Required because filter-chain rejections never reach `@ControllerAdvice`.
- `AppUserDetailsService` — loads users by email, maps `Role` → `ROLE_*` authority
- `AuthService` — BCrypt hashing; login returns an identical message for unknown email and
  wrong password so account existence is not leaked

**Security notes**
- Passwords stored as BCrypt hashes in `password_hash` — never plaintext
- Emails normalized to lowercase on register and login; unique constraint is DB-enforced
- JWT secret comes from `JWT_SECRET`; the yaml default is local-dev only and must be
  overridden in every real environment

**Tests:** `JwtServiceTest` (5) and `AuthServiceTest` (6) — all passing, no DB required.

---

### ✅ Phase 2 — Resume Parsing & Storage (COMPLETE)

Full pipeline: authenticated upload → local file storage → text extraction → section
chunking → persisted across three layers (`resume` → `resume_version` → `resume_chunk`).

**Endpoints** (`/api/v1/resumes`, all require a Bearer token; `userId` comes from the
principal, never a request param)
- `POST /upload` (`multipart/form-data`) → 201; stores file, extracts, chunks, persists
- `GET /` → 200, caller's resumes (newest first)
- `GET /{resumeId}` → 200; 404 if missing or not owned
- `GET /{resumeId}/versions` → 200
- `GET /{resumeId}/versions/{versionId}/chunks` → 200

**Components** (`resume/` package)
- `domain/` — `Resume`, `ResumeVersion`, `ResumeChunk` entities + enums `ResumeStatus`,
  `ExtractionStatus`, `SectionLabel`, `FileType` (all `@Enumerated(STRING)`).
  Aggregates reference each other by `UUID` id, not JPA associations.
- `storage/FileStorageService` — validates (PDF/DOCX, ≤10MB, path-traversal guard), writes
  to `{userId}/{resumeId}/{fileName}`, returns a storage key; `resolve()` rejoins to root.
- `parser/ResumeTextExtractorService` — PDFBox (PDF) + Apache POI (DOCX).
- `parser/ResumeChunkerService` — keyword-table (`Map<SectionLabel, List<String>>`) state
  machine; header-only-if-short heuristic; `OTHER` fallback. DSA-relevant component.
- `service/ResumeService` — orchestration (not `@Transactional`: file I/O runs outside any
  DB tx); `service/ResumePersistenceService` — the transactional boundary (atomic
  `saveParsed`; independent `saveResume` for the FAILED record).
- `controller/ResumeController`, `dto/` records, `mapper/ResumeMapper` (static, all mapping).

**Design decisions worth remembering**
- **Manual UUID ids** (not `@UuidGenerator`): the storage path embeds the resume id, so the
  service must know it before insert — matches the `User`/`AuthService` pattern.
- **New tables use `TIMESTAMPTZ` + `Instant`** (legacy `users`/`raw_resume` remain `TIMESTAMP`).
- **`pdfbox`** library artifact, not `pdfbox-app` (the CLI uber-jar).
- **Store-then-persist-once**: file written first (storage failure ⇒ zero DB rows); the
  `Resume` is inserted exactly once as `PARSED`, or as `FAILED` on a processing failure.
- On processing failure after storage: resume saved `FAILED`, then `ResumeProcessingException`
  → 422. The single `try/catch` in `ResumeService` is deliberate state compensation, not HTTP
  mapping (that stays in the global handler).

**Exception → status** (wired into `GlobalExceptionHandler`)
- `ResourceNotFoundException` → 404 · `ResumeProcessingException` → 422 ·
  `FileStorageException` → 500 · `TextExtractionException` → 500

**Config:** `app.storage.resume-dir` (`RESUME_STORAGE_DIR`, default `./uploads/resumes`);
`spring.servlet.multipart` max-file-size 10MB / max-request-size 11MB.

**Tests:** `ResumeChunkerServiceTest` (6), `ResumeTextExtractorServiceTest` (4, generates its
own PDF/DOCX fixtures), `ResumeServiceTest` (3, Mockito) — 13 total, all passing, no DB required.

> ⚠️ Not yet verified against a live DB: changesets 006–009 applying and `ddl-auto: validate`
> accepting the new entities. Run via Docker Compose to confirm (native PG14 shadows 5432).

---

## Architectural Decisions (Locked)

These are intentional choices — do not suggest alternatives unless asked.

1. **Liquibase** — YAML changesets, each guarded by a `not tableExists` precondition with
   `onFail: MARK_RAN` so re-runs against an existing DB are safe
2. **Auth before any user-owned data** — resume parsing, JD analysis, etc. cannot precede authentication infrastructure
3. **Intelligence layering** — strict separation: raw input → extracted facts → derived intelligence
4. **SkillRegistry canonical layer** — skill normalization is core IP, treated as a differentiating component
5. **Resume versioning** — tailored resume versions per role/domain
6. **OutcomeSignal entity** — tracks full application funnel (apply → screen → interview → offer), closes the intelligence feedback loop
7. **Six componentized sub-scores** — match scoring is broken into six sub-scores, not a single overall score
8. **Status lifecycle enums** — for resume, JD, and match processing states

---

## Coding Conventions

- All endpoints return `ApiResponse<T>` wrapper — never return raw objects
- Global exception handler catches all errors — no try/catch scattered in controllers
- Secrets and env-specific values come from environment variables — never hardcoded
- Liquibase changesets follow naming: `{NNN}-{description}.yaml`, registered in
  `db.changelog-master.yaml`. Never edit an applied changeset — add a new one.
- FastAPI ai-service must be started via:
  ```bash
  uvicorn app.main:app --reload
  ```
  Run from the project root, not as a direct Python script.

---

## Database State

**PostgreSQL 16** running via `infra/docker-compose.yml`.

| Changeset               | Tables Created | Status     |
|-------------------------|----------------|------------|
| 001-create-app-ping     | `app_ping`     | ✅ Applied |
| 002-seed-app-ping       | (seed data)    | ✅ Applied |
| 003-create-raw-resume   | `raw_resume`   | ✅ Applied |
| 004-create-raw-jd       | `raw_jd`       | ✅ Applied |
| 005-create-users        | `users`        | ✅ Applied |
| 006-create-resume       | `resume`       | 🔜 Pending live run |
| 007-create-resume-version | `resume_version` | 🔜 Pending live run |
| 008-create-resume-chunk | `resume_chunk` | 🔜 Pending live run |
| 009-add-raw-resume-user-fk | FK on `raw_resume.user_id` | 🔜 Pending live run |

> Changesets 006–009 are written and registered but not yet run against a live DB.
> `resume` FKs `users(id)` ON DELETE CASCADE; 009 adds the previously-missing FK on
> `raw_resume.user_id` → `users(id)` ON DELETE SET NULL.

> ⚠️ Local gotcha: a native Windows **PostgreSQL 14** service also listens on 5432 and
> shadows the container for host-run processes. Run the backend via Docker Compose, or
> stop that service before running it from the host.

**Timezone:** the JVM default is forced to UTC in `JobHuntBackendApplication.applyDefaultTimeZone()`,
and Hibernate writes timestamps in UTC via `hibernate.jdbc.time_zone`. The JVM-level part cannot
live in `application.yaml`: pgjdbc sends the timezone in its connection startup packet, so a
Windows JVM resolving `Asia/Calcutta` is rejected by PostgreSQL 16 tzdata with
`FATAL: invalid value for parameter "TimeZone"` before any Spring property is read.
An explicit `-Duser.timezone` still overrides it.

---

## Planned Phases (Do Not Implement Ahead)

Only work on the current active phase unless explicitly instructed otherwise.

| Phase | Focus                              | Status      |
|-------|------------------------------------|-------------|
| 0     | Foundation & scaffolding           | ✅ Complete  |
| 1     | Authentication (JWT)               | ✅ Complete  |
| 2     | Resume parsing & storage           | ✅ Complete  |
| 3     | JD intelligence                    | 🔜 Next     |
| 4     | Matching engine                    | Pending     |
| 5     | Skill gap analysis                 | Pending     |
| 6     | Application tracking               | Pending     |
| 7     | Interview preparation              | Pending     |
| 8     | Feedback loop (OutcomeSignal)      | Pending     |
| 9     | Frontend integration               | Pending     |
| 10    | Production hardening               | Pending     |

---

## Key Principles (Portfolio Context)

- **Auth before data:** Any user-owned data module must follow authentication, not precede it
- **Intelligence layering matters:** The raw → facts → intelligence separation is architecturally significant
- **Skill normalization is core IP:** `SkillRegistry` is a differentiating component, not just a lookup table
- **Feedback loops add depth:** `OutcomeSignal` closes the intelligence loop and demonstrates production thinking
- **Design patterns and DSA** are intentionally demonstrated throughout — this is an interview artifact

---

## Known Gaps (carried into later phases)

- ✅ *(fixed in Phase 2)* `RawResumeService` missing-id now returns 404 via
  `ResourceNotFoundException` instead of 500
- ✅ *(fixed in Phase 2)* `raw_resume.user_id` now has a FK to `users.id` (changeset 009)
- No dev/test/prod profile split yet; `application.yaml` is the only config file
- No GitHub Actions CI pipeline in the repo yet, despite the Phase 0 notes
- Token refresh / logout (revocation) is intentionally out of scope for Phase 1
- **Phase 2 carry-forwards:**
  - Changesets 006–009 not yet verified against a live DB (Docker Compose run pending)
  - Resume upload/parse is fully synchronous; extraction is not yet offloaded (async +
    `PROCESSING`/`PENDING`/`IN_PROGRESS` states are reserved for a later phase)
  - A FAILED-processing attempt leaves the stored file on disk (no orphan cleanup yet)
  - No file-content (magic-byte) sniffing — file type is resolved by extension only
  - `RawResumeController` still doesn't scope by authenticated user (legacy intake module)

---

_Last updated: Phase 2 (Resume parsing & storage) complete — code + unit tests done
(13 new tests passing), live-DB migration verification pending. Phase 3 (JD intelligence) is next._
