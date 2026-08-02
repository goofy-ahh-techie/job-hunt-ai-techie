# Job Hunt AI Copilot — Claude Reference

> This file is the single source of truth for Claude Code sessions inside IntelliJ.
> Update it at the end of each phase before starting the next one.
>
> Per-phase narratives (what we built, why, problems & fixes, deferrals, metrics) live in
> [`docs/phases/`](docs/phases/), indexed by [`PHASES.md`](PHASES.md). Finishing a phase means
> updating **three** places: the phase doc, the `PHASES.md` row, and this file.

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
│   │   │   │   ├── jd/             # Phase 3: JD intake + AI intelligence
│   │   │   │   ├── matching/       # Phase 4: six-sub-score matching engine
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
├── docs/
│   └── phases/                     # One narrative doc per completed phase
├── PHASES.md                       # Phase tracker index → docs/phases/
├── AGENTS.md                       # Quick-reference for coding agents
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
  service must know it before insert — matches the `User`/`AuthService` pattern. The three
  entities implement `Persistable<UUID>` (a `@Transient persisted` flag set on
  `@PostLoad`/`@PostPersist`) so `save()` uses `persist()`, not `merge()` — otherwise an
  assigned id makes Spring Data `merge()` (extra SELECT; audit values land on a copy, so the
  upload response came back with `createdAt: null`).
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

**Live end-to-end verified:** against the Dockerized PG16 — changesets 006–009 applied,
`ddl-auto: validate` accepted the entities, and register → upload (PDF) → parse → read returned
a resume + version + 4 section-labelled chunks (SUMMARY/EXPERIENCE/SKILLS/EDUCATION).

> ⚠️ Local run: stop the native `postgresql-x64-14` service (it shadows the Docker PG16 on
> 5432) before running the backend from the host/IntelliJ, or run the backend in Compose.

---

### ✅ Phase 3 — JD Intelligence (COMPLETE)

JD ingestion (paste or file upload) → AI-powered structured extraction via the
`intelligence-python` FastAPI service calling a local Ollama LLM → persisted across two
layers (`job_description` → `jd_intelligence`). First real exercise of the Java → Python
service boundary.

**intelligence-python (FastAPI)** — `app/`: `clients/ollama_client.py` (async httpx →
Ollama `/api/generate`), `prompts/jd_extraction_prompt.py` (single-shot, JSON-only,
sentinel-substituted template), `services/jd_extraction_service.py` (fence-strip → parse
→ validate), `schemas/jd_schemas.py` (Pydantic), `api/jd_routes.py`, `config.py`,
`exceptions.py`. Endpoint `POST /extract/jd` → `JdExtractionResponse` (503/422/422/200);
`GET /health` → `{status, model}`. Tests: `tests/test_jd_extraction_service.py` (4,
Ollama mocked). Start: `uvicorn app.main:app --reload`.

**Endpoints** (`/api/v1/jds`, all require a Bearer token; `userId` from the principal)
- `POST /paste` (`application/json`) → 201; persists raw text, extracts, persists result
- `POST /upload` (`multipart/form-data`, PDF/DOCX) → 201; stores file, extracts text
  (reuses Phase 2 `FileStorageService` + `ResumeTextExtractorService`), same pipeline
- `GET /` → 200 (newest first) · `GET /{jdId}` → 200 · `GET /{jdId}/intelligence` → 200
  (404 if not yet extracted) · `GET /{jdId}/detail` → 200 (JD + intelligence combined)

**Components** (`jd/` package) — `domain/` (`JobDescription`, `JdIntelligence` entities +
enums `JdStatus`, `JdSourceType`, `EmploymentType`, `JdExtractionStatus`), `repository/`,
`dto/` (records), `client/` (`JdIntelligenceClient` + `JdExtractionResult` record +
`IntelligenceRestClientConfig`), `mapper/JdMapper` (static; owns JSON list codec),
`service/` (`JdService` orchestration + `JdPersistenceService` transactional boundary),
`controller/JdController`.

**Design decisions worth remembering**
- **Same `Persistable<UUID>` + assigned-UUID pattern as Phase 2** (file-upload path embeds
  the id in the storage key). JD persisted as `UPLOADED` first, then `EXTRACTED` +
  `COMPLETED` intelligence saved atomically via `saveParsed`.
- **List fields stored as JSON-serialised `TEXT`** (documented tradeoff): `JdMapper`
  owns both directions — `serializeList` (service→entity) and deserialize (entity→DTO).
- **`employment_type` is a typed `EmploymentType` enum** on the entity; the Python service
  normalises free-text to an enum name or null before it crosses the boundary.
- **Failure-visible-through-API**: on extraction failure (422) the JD is `FAILED` *and* a
  `jd_intelligence` row records `extraction_status=FAILED` + `extraction_error`; on service
  unavailable (503) the JD is `FAILED` with no intelligence row. `JdService` is not
  `@Transactional` so these compensating writes survive (mirrors `ResumeService`).
- **Two client timeouts, not one**: connect=10s (availability), read=130s. A single 10s
  timeout — as first specced — would false-503 every real extraction, since the local 3B
  model takes 30–66s. Same lesson forced the Python-side Ollama timeout to 120s.
- **Ollama request tuning** (deviates from a bare `{model,prompt,stream}` body):
  `options.temperature=0` (deterministic extraction — otherwise `raw_summary` and skill
  splits vary run-to-run) and `options.num_ctx=8192` (default 2048 silently truncates
  longer JDs). Model `llama3.2:3b`; `mistral:7b` is the documented quality-upgrade path
  (swap `OLLAMA_MODEL`, no code change).

**Exception → status** (wired into `GlobalExceptionHandler`)
- `JdExtractionFailedException` → 422 · `IntelligenceServiceUnavailableException` → 503 ·
  `JdIntelligenceNotFoundException` → 404 · (`ResourceNotFoundException` → 404 for
  wrong-owner/missing JD, reused from Phase 2)

**Config:** `intelligence.service.url` (`INTELLIGENCE_SERVICE_URL`, default
`http://localhost:8000`), `.connect-timeout-seconds` (10), `.read-timeout-seconds` (130);
`app.storage.jd-dir` (reserved — JD uploads currently reuse the resume storage root).
Python: `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `INTELLIGENCE_PORT`, plus timeout/temperature/
num_ctx settings in `app/config.py`.

**Tests:** `JdIntelligenceClientTest` (4, `MockRestServiceServer`), `JdServiceTest` (6,
Mockito) — 10 new, all passing, no DB/Spring context. Python: 4 pytest, Ollama mocked.

**Live end-to-end verified** (Dockerized PG16 + `ollama/ollama` container + FastAPI):
changesets 010–011 applied, `validate` accepted the entities, and register → paste JD →
`EXTRACTED` with populated `required_skills`/`must_have`/`responsibilities`, → PDF upload →
`EXTRACTED`, → all GETs, → 404 on bad id, → **503 with `FAILED` JD (no intel row) when the
intelligence service is down** (confirmed in DB), all worked.

> ⚠️ Ollama must be running for JD ingestion. Local dev used a container:
> `docker run -d --name ollama -p 11434:11434 -v ollama:/root/.ollama ollama/ollama`
> then `docker exec ollama ollama pull llama3.2:3b`. The intelligence service defaults to
> port 8000; if run elsewhere, set `INTELLIGENCE_SERVICE_URL` on the backend.

---

### ✅ Phase 4 — Matching Engine (COMPLETE)

Resume chunks (Phase 2) + JD intelligence (Phase 3) → six componentised sub-scores → one
weighted, explainable match, upserted per resume-version/JD pair. First phase that *derives*
rather than extracts. Hybrid: Java owns four deterministic sub-scores, the Python service
provides embedding-based semantic similarity for the other two (plus must-have rescue).

**Scoring model** (locked weights): Must-Have 30% · Required Skills 25% · Responsibilities 20%
· Experience 12% · Qualifications 8% · Preferred Skills 5% (bonus, never a penalty).
Overall = Σ(sub-score × weight), 0–100 at two decimals.

**intelligence-python additions** — `schemas/match_schemas.py`, `services/semantic_similarity_service.py`,
`api/match_routes.py`; `OllamaClient.embed()` over `/api/embeddings`; `numpy` added.
Endpoint `POST /match/semantic-similarity` → `{results, match_count, match_percentage}`
(200/503; **no `success/data/error` envelope** — unlike extraction there is no partial-success
shape to carry). Tests: `tests/test_semantic_similarity_service.py` (6, Ollama mocked).

**Endpoints** (`/api/v1/matches`, Bearer token; `userId` from the principal)
- `POST /` → 201 (calculate, or recalculate in place) · `GET /{matchId}` → 200 ·
  `GET /resume/{resumeId}` → 200 · `GET /jd/{jdId}` → 200 (both list `MatchSummaryResponse`)

**Components** (`matching/` package) — `domain/` (`MatchResult`, `MatchStatus`), `repository/`,
`scoring/` (`SubScorer` + six scorers, `SubScoreOrchestrator`, `WeightedScoreCalculator`,
`ScoreWeights`, `ScoreExplanationBuilder`, `KeywordMatcher`, `TextPassages`), `client/`
(`MatchingIntelligenceClient`, `SemanticSimilarityResult`, `PhraseMatchResult`,
`MatchingRestClientConfig`), `dto/`, `mapper/MatchMapper`, `service/` (`MatchService` +
`MatchPersistenceService`), `controller/MatchController`.

**Design decisions worth remembering**
- **Strategy pattern per sub-score.** Six `SubScorer`s behind one interface; the orchestrator
  runs them, the calculator weights them. A seventh dimension is a new class plus a weight.
  Scorers are pure functions over `ScoringContext`, so every one unit-tests without Spring or a DB.
- **Cheap pass, then expensive pass.** Skills *and* must-haves keyword-match first and send only
  the misses for embedding — a keyword hit and a semantic hit both mean "present".
- **Degrade, never 503.** The semantic scorers catch `IntelligenceServiceUnavailableException`
  themselves and fall back to keyword-only with a note in the explanation. Verified live: with
  the Python service down the match still returns **201 COMPLETED** at a lower score.
- **Per-scorer failure isolation.** A throwing scorer is recorded as 0.0 with the reason and the
  other five still run — the dimensions are independent, so one defect must not discard five
  correct answers.
- **Embed each side once (N+M), never pairwise (N×M).** Vectors are compared in numpy; a Python
  test asserts the call count.
- **Two RestClient beans.** Semantic matching gets its own 180s read timeout (it embeds every
  passage plus every unmatched phrase). Both clients now `@Qualifier` their injection point so
  the second bean cannot make the Phase 3 wiring ambiguous.
- **`nomic-embed-text`, not `OLLAMA_MODEL`.** A generative model answers `/api/embeddings` with
  "This server does not support embeddings" (verified, llama3.2:3b on Ollama 0.32.3).
- **Thresholds are measured, and skills sit *below* responsibilities.** Short phrase vs. paragraph
  scores lower than paragraph vs. paragraph — length asymmetry, not meaning. See gaps below.
- **`TextPassages` splits chunks before embedding.** One vector for a 158-word chunk is the
  *average* of its topics; splitting to sentences is what makes specific matches land.

**Exception → status** (wired into `GlobalExceptionHandler`)
- `MatchCalculationFailedException` → 500 · `SemanticMatchingFailedException` → 422 ·
  `ResumeVersionNotFoundException` → 404 · (`ResourceNotFoundException` → 404,
  `JdIntelligenceNotFoundException` → 404, both reused)

**Config:** `intelligence.service.match-read-timeout-seconds` (180);
`matching.semantic.skills-threshold` (0.50), `.responsibilities-threshold` (0.60),
`.must-have-threshold` (0.60). Python: `OLLAMA_EMBEDDING_MODEL` (`nomic-embed-text`),
`OLLAMA_EMBEDDING_TIMEOUT_SECONDS` (120), `SEMANTIC_DEFAULT_THRESHOLD`.

**Tests:** 66 new Java (6 scorer classes, `TextPassagesTest`, `WeightedScoreCalculatorTest`,
`SubScoreOrchestratorTest`, `MatchServiceTest`, `MatchingIntelligenceClientTest`) — 100 Java
total, all passing, no DB/Spring context. Python: 10 total (6 new), Ollama mocked.

**Live end-to-end verified** (Dockerized PG16 + `ollama/ollama` + FastAPI): changeset 012
applied, `validate` accepted the entity, and register → upload DOCX resume → paste JD →
`POST /matches` returned **74.50** with all six sub-scores and a readable explanation;
recalculation upserted the same row (1 row after 4 runs) and advanced `lastCalculatedAt`;
all GETs, 404s and 401 behaved; and with the intelligence service stopped the match still
completed at 43.33 with keyword-only notes.

> ⚠️ Matching needs **two** Ollama models pulled: `llama3.2:3b` (JD extraction) and
> `nomic-embed-text` (matching). `docker exec ollama ollama pull nomic-embed-text`.

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
| 006-create-resume       | `resume`       | ✅ Applied |
| 007-create-resume-version | `resume_version` | ✅ Applied |
| 008-create-resume-chunk | `resume_chunk` | ✅ Applied |
| 009-add-raw-resume-user-fk | FK on `raw_resume.user_id` | ✅ Applied |
| 010-create-job-description | `job_description` | ✅ Applied |
| 011-create-jd-intelligence | `jd_intelligence` | ✅ Applied |
| 012-create-match-result | `match_result` | ✅ Applied |

> Applied against the live PG16. `resume` FKs `users(id)` ON DELETE CASCADE; 009 adds the
> previously-missing FK on `raw_resume.user_id` → `users(id)` ON DELETE SET NULL. 009 also
> nulls pre-existing orphaned `raw_resume.user_id` values (legacy stub data) before adding
> the constraint, or the FK is rejected.

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
| 3     | JD intelligence                    | ✅ Complete  |
| 4     | Matching engine                    | ✅ Complete  |
| 5     | Skill gap analysis                 | 🔜 Next     |
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
  - Resume upload/parse is fully synchronous; extraction is not yet offloaded (async +
    `PROCESSING`/`PENDING`/`IN_PROGRESS` states are reserved for a later phase)
  - A FAILED-processing attempt leaves the stored file on disk (no orphan cleanup yet)
  - No file-content (magic-byte) sniffing — file type is resolved by extension only
  - `RawResumeController` still doesn't scope by authenticated user (legacy intake module)
- **Phase 3 carry-forwards:**
  - JD ingestion is fully synchronous; extraction blocks the request for 30–66s (the LLM
    call). Async offload + `PROCESSING`/`IN_PROGRESS` states are reserved for a later phase.
  - `llama3.2:3b` extraction quality is the ceiling: `experience_years_max` and the prose
    `raw_summary` come back empty on some JDs. `mistral:7b` is the upgrade path.
  - JD file uploads reuse the resume storage root; `app.storage.jd-dir` is reserved but
    not yet wired (would need `FileStorageService` parameterised for a second root).
  - No orphan cleanup of a stored JD file when extraction fails (same gap as Phase 2).
  - Skill strings are extracted verbatim, not yet normalised — `SkillRegistry` (core IP)
    is a later phase.
- **Phase 4 carry-forwards:**
  - Match calculation is synchronous; ~12s of embedding calls block the request. `CALCULATING`
    is already persisted before scoring starts, so the async offload has its state waiting.
  - **Acronyms embed badly** — "CI/CD" scores 0.371 against "build and deployment pipelines in
    Jenkins and GitHub Actions", a true positive the model misses. Clearest remaining accuracy
    gap and squarely `SkillRegistry` territory.
  - Keyword matching is plain substring containment, so a one- or two-character skill ("R", "Go")
    matches inside unrelated words and "Kubernetes" never matches "K8s". Same root cause: skills
    compared as raw strings.
  - **Similarity thresholds are model-specific** and were calibrated against `nomic-embed-text`.
    Swapping the embedding model requires re-measuring `matching.semantic.*`; the values are
    config keys, not constants, for exactly this reason.
  - Only the three keyword dimensions persist matched/missing lists; a `GET` reconstructs the
    other three dimensions' prose from `score_explanation` and returns empty lists for them.
  - `ScoreWeights` is a value object but is still only ever constructed as `DEFAULT` — per-user
    or per-role weighting is wiring that has not been done.
  - The `FAILED`-status path is unit-tested but was never triggered live (it needs an injected
    fault; the intelligence service being down is deliberately *not* a failure).

---

_Last updated: Phase 4 (Matching engine) complete — Java + Python code, 66 new Java unit tests
(100 total) + 6 new Python pytest (10 total), all passing with no DB/Spring context/Ollama, and
a live end-to-end run verified: changeset 012 applied, register → upload resume → paste JD →
`POST /matches` = **74.50** with all six sub-scores and a readable explanation, recalculation
upserting the same row, all GETs/404s/401, and a **201 COMPLETED keyword-only match with the
intelligence service stopped** (never a 503). Live-discovered fixes: `nomic-embed-text` (a
generative model cannot embed at all), skills threshold 0.65→0.50 with the skills/responsibilities
ordering **inverted** against the spec, a semantic pass added to must-have coverage, and
`TextPassages` chunk splitting — the last two together moved the same pair from 51.50 to 74.50.
Phase 5 (Skill gap analysis) is next, and `SkillRegistry` is the fix for the acronym and
short-token gaps this phase surfaced._
