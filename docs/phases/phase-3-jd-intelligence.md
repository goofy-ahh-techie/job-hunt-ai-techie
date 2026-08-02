# Phase 3 — JD Intelligence

**Status:** ✅ Complete — code, unit tests (Java + Python), **and** a live end-to-end run
verified against Dockerized PostgreSQL 16, a real `ollama/ollama` container, and the FastAPI
service (paste + PDF upload → structured extraction, plus the service-down failure path).
**Focus:** The first real exercise of the **Java → Python service boundary** — the Java backend
owns persistence and API contracts; the `intelligence-python` FastAPI service owns AI reasoning,
calling a local Ollama LLM. This is the `extracted facts → derived intelligence` layer for JDs.

---

## What we built

**intelligence-python (FastAPI)** — `app/`
- `clients/ollama_client.py` — async httpx wrapper over Ollama `POST /api/generate`; every
  transport failure normalised to `OllamaUnavailableError`
- `prompts/jd_extraction_prompt.py` — single-shot, JSON-only prompt; a `{{JD_TEXT}}` sentinel +
  `str.replace` (the body is a JSON schema full of literal braces, so f-string/`.format` are out)
- `services/jd_extraction_service.py` — build prompt → call LLM → strip markdown fences → parse →
  validate; raises `ExtractionParseError` / `ExtractionValidationError`
- `schemas/jd_schemas.py` — Pydantic request/result/response; normalises `employment_type` and
  coerces null lists / null `raw_summary`
- `api/jd_routes.py` — `POST /extract/jd` → `JdExtractionResponse`, mapping errors to 503/422/422/200
- `config.py`, `exceptions.py`, health endpoint `GET /health` → `{status, model}`

**Endpoints** (`/api/v1/jds`, all require a Bearer token; owning `userId` from the principal)
- `POST /paste` (`application/json`) → 201 — persist raw text, extract, persist result
- `POST /upload` (`multipart/form-data`, PDF/DOCX) → 201 — store file + extract text (reusing
  Phase 2 `FileStorageService` + `ResumeTextExtractorService`), then the same pipeline as paste
- `GET /` → 200 (newest first) · `GET /{jdId}` → 200 · `GET /{jdId}/intelligence` → 200 (404 if
  not yet extracted) · `GET /{jdId}/detail` → 200 (JD + intelligence combined)

**Persistence — two layers** (changesets 010–011)
- `job_description` — one row per pasted/uploaded JD (owner, `source_type`, file metadata,
  `status` lifecycle, the `raw_text`)
- `jd_intelligence` — the extracted intelligence, 1:1 with the JD (UNIQUE `job_description_id`);
  list fields stored as JSON-serialised `TEXT`; `extraction_status` + `extraction_error`

**Code** (`jd/` package)
- `domain/` — `JobDescription`, `JdIntelligence` + enums `JdStatus`, `JdSourceType`,
  `EmploymentType`, `JdExtractionStatus`
- `repository/` — `JobDescriptionRepository`, `JdIntelligenceRepository`
- `dto/` — `JdPasteRequest`, `JdFileRequest`, `JdResponse`, `JdIntelligenceResponse`, `JdDetailResponse`
- `client/` — `JdIntelligenceClient` (RestClient), `JdExtractionResult` record, `IntelligenceRestClientConfig`
- `mapper/JdMapper` — static; owns the JSON list codec (both directions)
- `service/` — `JdService` (orchestration) + `JdPersistenceService` (transaction boundary)
- `controller/JdController`

---

## Why we built it this way

- **Reused the Phase 2 patterns wholesale.** Same `Persistable<UUID>` + assigned-UUID insert
  (the file-upload path embeds the id in the storage key), same `Service` vs
  `PersistenceService` split, same identity-by-UUID (not JPA association) between the two
  aggregates, same `TIMESTAMPTZ`↔`Instant` mapping. Phase 3 is a new domain, not new plumbing.
- **The Java client owns the boundary.** `JdIntelligenceClient` normalises every RestClient
  outcome into two domain exceptions (503 → unavailable, 422 → failed) so nothing above it ever
  sees an HTTP type. Callers reason in domain terms; the global handler maps back to status codes.
- **List fields as JSON `TEXT`, codec in one place.** A documented tradeoff (no array column
  type, human-readable). `JdMapper` owns both directions — `serializeList` for the service
  writing the entity, deserialize for building the response — so the JSON concern lives nowhere else.
- **`employment_type` is a typed enum on the entity.** The Python service normalises free-text
  ("Full-time") to an enum name or null before it crosses the wire, so the enum is safe and the
  DB column is never an arbitrary string.
- **Failure is visible through the API, by design.** Extraction failure (422) records the JD as
  `FAILED` *and* a `jd_intelligence` row with `extraction_status=FAILED` + the error; service
  unavailable (503) records a `FAILED` JD with no intelligence row. `JdService` is deliberately
  **not** `@Transactional` so these compensating writes survive the re-thrown exception — the
  exact Phase 2 rollback-trap lesson, reapplied.
- **Determinism over sampling for extraction.** `temperature=0` makes the same JD yield the same
  structured output — the right trade for an extraction task (vs. the model's default 0.8, which
  reshuffled skill lists and dropped `raw_summary` run-to-run).

---

## Problems we faced & how we fixed them

### 1. Ollama not installed / not reachable (blocked the Step 6 gate)
The hard gate — "verify the Python service against real Ollama before any Java work" — couldn't
run: Ollama wasn't on the host (no process, nothing on 11434, not on PATH, no `~/.ollama`).
**Fix:** ran Ollama in Docker against a persistent volume
(`docker run -d --name ollama -p 11434:11434 -v ollama:/root/.ollama ollama/ollama`, then
`docker exec ollama ollama pull llama3.2:3b`). `OLLAMA_BASE_URL`/`OLLAMA_MODEL` were already
config-driven, so no code changed.

### 2. 30s Ollama timeout false-503'd every real extraction (found at Step 6)
Measured: `llama3.2:3b` on CPU emits the ~400-token JSON at ~13 tok/s → **32–66s per call**. The
spec's 30s guaranteed a timeout on every JD.
**Fix:** raised the Python-side Ollama timeout to **120s** (env-overridable), with the measurement
recorded in a config comment.

### 3. The same lesson, again, on the Java client (found at Step 15/17)
The spec's Java client `timeout-seconds: 10` would time out reading the (30–66s) response.
**Fix:** split into **connect=10s** (availability) and **read=130s** (just above the Python 120s
ceiling). A single 10s timeout would have made the client unusable.

### 4. `raw_summary: null` failed schema validation (found at Step 6)
The model sometimes emits the key with a literal `null`; a Pydantic default only fills an *absent*
key, so `None` failed the `str` type → a spurious 422.
**Fix:** a `before` validator coerces null→`""`, matching how null list entries are already dropped.

### 5. `"6 to 9 years"` mis-parsed to null/null (found at Step 6)
The prompt only gave hyphen/plus examples, so the "N to M years" phrasing wasn't recognised.
**Fix:** added explicit examples (`"6 to 9 years" → min 6, max 9`, `"at least N"`, `"minimum N,
up to M"`) to the prompt. Verified: `experience_years_min/max = 6/9` after the change.

### 6. Non-determinism made runs irreproducible (found at Step 6)
At the default temperature the same JD gave different splits and an intermittently-empty
`raw_summary`.
**Fix:** added `options.temperature=0` (deterministic) and `options.num_ctx=8192` (the default
2048 silently truncates the *front* of the context on a long JD — losing instructions, no error).
Two identical calls now return byte-for-byte identical output.

### 7. `FileStorageService` (resume-dir) vs. the new `app.storage.jd-dir`
The spec says "reuse Phase 2 `FileStorageService`" *and* adds an `app.storage.jd-dir` config —
but that service binds a single root (`resume-dir`).
**Fix (documented tradeoff):** reused `FileStorageService` unchanged (zero risk to Phase 2 — JD
files land under the shared root at `{userId}/{jdId}/{file}`) and added `jd-dir` as a *reserved*
key. Splitting JD storage to its own root would mean parameterising a Phase 2 class, deferred.

### 8. Full-context test fails on the pg14 shadow + timezone (environment, not code)
`JobgaspBackendApplicationTests.contextLoads` boots the whole context → Liquibase → DB. It failed
first on `password authentication failed` (native pg14 squats on 5432 with no `jobhunt` user), then
on `invalid value for parameter "TimeZone": "Asia/Calcutta"` — the documented gotcha: the test
bootstraps the context directly, bypassing `main()` where `applyDefaultTimeZone()` forces UTC.
**Fix:** ran it against the PG16 sidecar with `-DargLine="-Duser.timezone=UTC"` → passes, proving
all new beans wire in a full context. The plain `mvn test` failure is purely environmental; the
34 real unit tests need no DB/context.

---

## What we deferred to later phases

- **Async extraction** — ingestion is fully synchronous; the request blocks 30–66s on the LLM.
  `PROCESSING` / `IN_PROGRESS` states exist in the enums, reserved for offloaded extraction.
- **Extraction quality** — `llama3.2:3b` leaves `experience_years_max` and the prose
  `raw_summary` empty on some JDs. `mistral:7b` is the upgrade path (swap `OLLAMA_MODEL`, no code).
- **`app.storage.jd-dir`** — reserved but not wired; JD uploads reuse the resume storage root.
- **Orphan-file cleanup** — a failed extraction leaves the stored JD file on disk (same as Phase 2).
- **Skill normalisation** — skills are extracted verbatim; `SkillRegistry` (core IP) is a later phase.

> **Local run note:** JD ingestion needs Ollama running (Docker container above) and the
> intelligence service reachable at `INTELLIGENCE_SERVICE_URL` (default `:8000`). The backend also
> needs the Dockerized PG16 on 5432 — stop the native `postgresql-x64-14` service (elevated) or run
> the sidecar/Compose, since it shadows the container for host-run processes.

---

## Metrics — current functionality

| Metric                          | Value                                                        |
|---------------------------------|--------------------------------------------------------------|
| New endpoints                   | 6 (`/api/v1/jds` …) + 1 Python (`POST /extract/jd`)          |
| Persistence layers              | 2 (`job_description` → `jd_intelligence`, 1:1)               |
| Liquibase changesets added      | 2 (010–011) — **applied on live PG16**                       |
| Service boundary                | Java `RestClient` → Python FastAPI → Ollama `llama3.2:3b`    |
| Supported inputs                | pasted text (≥ 50 chars); PDF/DOCX upload (reuses Phase 2)   |
| Ownership enforcement           | `findByIdAndUserId`; wrong/missing → 404                     |
| Exception → status              | 422 / 503 / 404, all via `ApiResponse` (never a raw 500)     |
| Determinism                     | `temperature=0` — identical JD → byte-identical extraction   |
| New Java unit tests passing     | 10 / 10 (client 4, service 6) — no DB / no context           |
| New Python tests passing        | 4 / 4 (Ollama mocked)                                        |
| Prior tests after changes       | 24 / 24 (no regression)                                      |
| Live end-to-end verified        | register → paste → EXTRACTED; PDF upload → EXTRACTED; GETs; 404; **503 + FAILED-persisted when service down** |
| Live-discovered fixes           | Ollama 30→120s; Java read 10→130s; null `raw_summary`; years prompt; `temperature=0`/`num_ctx=8192` |
