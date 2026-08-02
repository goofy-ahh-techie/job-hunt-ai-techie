# AGENTS: How to be productive in this repository

This file gives concise, actionable guidance for AI coding agents working on the
job-hunt-ai-techie monorepo. Focus on the concrete patterns, run/debug commands,
and cross-service contracts that are required to deliver correct changes.

Key places to read first
- `CLAUDE.md` — single source of truth for architecture, conventions and phase status.
- `jobgasp-backend/src/main/java/com/jobhuntai/jobhunt_backend/` — backend code (auth, resume, jd)
- `intelligence-python/app/` — FastAPI intelligence service (client, prompt, schema)
- `infra/docker-compose.yml` — local dev composition (Postgres + backend + intel)

Big-picture architecture (short)
- Java Spring Boot backend (monolith) exposes REST API under `/api/v1/*` and owns
  persistence (Postgres + Liquibase). All controllers return `ApiResponse<T>`.
- Python FastAPI service (`intelligence-python/app`) is an external intelligence microservice
  used by the Java `JdIntelligenceClient` via HTTP. Ollama is called from Python.
- Storage is local disk under `uploads/` by default; file upload paths embed assigned UUID ids
  (see `FileStorageService` and resume/jd upload flows).

Project-specific patterns you must follow
- ApiResponse wrapper: every endpoint returns `ApiResponse<T>` (never raw DTOs).
  Files: `common/ApiResponse`, global handler in `common/`.
- Errors and status mapping: a centralized `GlobalExceptionHandler` maps domain exceptions
  to HTTP statuses. Avoid throwing controller-level status codes; raise the project exceptions.
- Assigned-UUID + Persistable pattern: new entities use manually assigned UUIDs and
  implement `Persistable<UUID>` so `save()` uses `persist()` semantics. See `resume/` and `jd/` packages.
- Store-first, persist-once: file storage happens before DB insert; if storage fails there
  must be zero DB rows. See `ResumeService` and `ResumePersistenceService` design notes in `CLAUDE.md`.
- List fields as JSON text: some entity list fields are serialized to JSON text; `JdMapper` owns
  serialization/deserialization for both the `jd/` and `matching/` packages (search `JdMapper`).
- LLM boundary (Phase 5): anything mechanical is computed in code, not asked of the model.
  Gap priority comes from which list an item came from, `quick_wins` from the week estimates,
  and no supplied gap may be dropped — all enforced in `gap_analysis_service.py` *after* the
  model replies, because the model broke all three in live runs. Leave the model only
  "why does this matter" and "how do I close it".
- Sub-scorer strategy (Phase 4): each match dimension is a `SubScorer` in `matching/scoring/`.
  Add a dimension by adding a class plus a weight in `ScoreWeights` — do not edit existing
  scorers. Scorers must not throw for ordinary data (an empty JD list is a defined score), and
  semantic scorers must catch `IntelligenceServiceUnavailableException` and degrade to keyword
  matching rather than failing the match.

Integration and runtime gotchas
- Intelligence client timeouts: Java uses two timeouts — connect=10s, read=130s. Python Ollama uses
  a 120s read timeout. Long LLM runs (30–66s) require the larger read timeout.
  Files: `IntelligenceRestClientConfig`, `intelligence-python/app/config.py`.
  Matching has a *second* RestClient bean (`matchingRestClient`, read=180s); both clients
  `@Qualifier` their injection point, so do not remove those annotations.
- Two Ollama models are required: `llama3.2:3b` (JD extraction) and `nomic-embed-text` (matching
  embeddings). A generative model cannot serve `/api/embeddings` at all.
- Similarity thresholds (`matching.semantic.*` in application.yaml) are calibrated to
  `nomic-embed-text`. Changing the embedding model means re-measuring them.
- Local Postgres port conflict: Windows often has native Postgres 14 on 5432 which will
  shadow the Dockerized Postgres 16. Either stop the native service or run everything in Compose.
  See `CLAUDE.md` warnings.
- JVM timezone: the app forces UTC in `JobHuntBackendApplication.applyDefaultTimeZone()`; tests and runs
  depend on that. On Windows, passing `-Duser.timezone` may be needed.

Developer workflows (commands)
- Run full Java tests (from repo root):
  - In Powershell (host build): `cd jobgasp-backend; .\mvnw test`
  - Or run backend+db+intel in Compose and run tests inside the container if configured.
- Run Python tests:
  - `cd intelligence-python; pytest -q`
- Start services for integration / manual testing (Powershell):
  - `cd infra; docker-compose up --build` (starts Postgres, backend, intelligence as configured)
  - Backend health: `curl http://localhost:8080/actuator/health`
  - Intelligence health: `curl http://localhost:8000/health`
- Start FastAPI locally (used during JD development):
  - `cd intelligence-python; uvicorn app.main:app --reload`

Files & locations to inspect for common edits
- Auth: `jobgasp-backend/src/main/java/.../auth/` (JwtService, SecurityConfig, JwtAuthenticationFilter)
- Resume pipeline: `.../resume/` (storage, parser, chunker, service, persistence)
- JD pipeline: `.../jd/` (controller, service, mapper, client)
- Python intelligence: `intelligence-python/app/clients/ollama_client.py`, `prompts/jd_extraction_prompt.py`

When making changes, follow these rules
- Do not edit applied Liquibase changesets; add new changesets under `db/changelog/`.
- Preserve the ApiResponse wrapper and exception mapping — changing response shapes breaks clients.
- Keep the assigned-UUID pattern for uploads — storage paths depend on IDs being known before insert.
- If changing timeouts or Ollama settings, update both Java client config and Python `config.py`.

Quick examples (how to call things)
- Post a JD paste (requires a bearer token):
  - `curl -X POST -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"rawText":"<JD text>"}' http://localhost:8080/api/v1/jds/paste`
- Upload a resume PDF (multipart): use the `/api/v1/resumes/upload` endpoint; file validation enforces
  extension `.pdf|.docx` and 10MB max.

Where to update this doc
- Keep `CLAUDE.md` and `AGENTS.md` in sync: `CLAUDE.md` is the canonical narrative; `AGENTS.md` is a focused
  quick-reference for automated agents.

---
Generated from repository state (see `CLAUDE.md` for full details).

