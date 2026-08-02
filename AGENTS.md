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
  serialization/deserialization (search `JdMapper`).

Integration and runtime gotchas
- Intelligence client timeouts: Java uses two timeouts — connect=10s, read=130s. Python Ollama uses
  a 120s read timeout. Long LLM runs (30–66s) require the larger read timeout.
  Files: `IntelligenceRestClientConfig`, `intelligence-python/app/config.py`.
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

