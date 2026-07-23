# Phase 2 — Resume Parsing & Storage

**Status:** ✅ Complete — code, unit tests, **and** a live end-to-end run verified
against the Dockerized PostgreSQL 16 (migrations applied, upload → parse → read all pass).
**Focus:** The first user-owned data module — turn an authenticated user's uploaded
resume into stored, owned, structured data. This is the first real run of the
`raw input → extracted facts → derived intelligence` layering.

---

## What we built

**Endpoints** (`/api/v1/resumes`, all require a Bearer token; the owning `userId` is
resolved from the security principal, never from a request param)
- `POST /upload` (`multipart/form-data`) → 201 — stores the file, extracts text, chunks it, persists all three layers
- `GET /` → 200 — the caller's resumes, newest first
- `GET /{resumeId}` → 200 — 404 if missing or not owned
- `GET /{resumeId}/versions` → 200
- `GET /{resumeId}/versions/{versionId}/chunks` → 200

**Persistence — three layers** (changesets 006–009)
- `resume` — one row per uploaded file (owner, file metadata, `status` lifecycle)
- `resume_version` — an extracted snapshot (`raw_text`, word/char counts, `extraction_status`); supports tailored versions later
- `resume_chunk` — section-labelled slices of a version's text (what matching/skill-gap phases will consume)
- Changeset 009 also back-fills the FK on the legacy `raw_resume.user_id`

**Code** (`resume/` package)
- `domain/` — `Resume`, `ResumeVersion`, `ResumeChunk` + enums `ResumeStatus`, `ExtractionStatus`, `SectionLabel`, `FileType`
- `storage/FileStorageService` — validation + local-disk storage with a storage-key scheme
- `parser/ResumeTextExtractorService` — PDFBox (PDF) + Apache POI (DOCX)
- `parser/ResumeChunkerService` — the keyword-driven section chunker
- `service/ResumeService` (orchestration) + `service/ResumePersistenceService` (transaction boundary)
- `controller/ResumeController`, `dto/` records, `mapper/ResumeMapper`

---

## Why we built it this way

- **Aggregates reference each other by `UUID` id, not JPA associations.** `Resume`,
  `ResumeVersion`, and `ResumeChunk` are separate aggregates; linking by identity (not object
  graph) is the DDD-correct approach and removes lazy-loading/N+1 traps. It also matches the
  existing `RawResumeEntity`.
- **Manual UUID ids, not `@UuidGenerator`.** The storage path embeds the resume id
  (`{userId}/{resumeId}/{fileName}`), so the service must know the id *before* the row is
  inserted. Manual assignment (the `User`/`AuthService` pattern) makes this a clean single
  insert; `@UuidGenerator` would hide the id until flush.
- **`TIMESTAMPTZ` + `Instant` for the new tables.** The project forces UTC everywhere; a
  timezone-aware column is simply the correct type. (Legacy `users`/`raw_resume` stay on plain
  `TIMESTAMP` — not worth a migration this phase.)
- **`pdfbox` library, not `pdfbox-app`.** The spec named `pdfbox-app`, but that artifact is the
  standalone CLI uber-jar; the embeddable library is `org.apache.pdfbox:pdfbox`.
- **The chunker is a deliberate DSA showcase.** A `Map<SectionLabel, List<String>>` keyword
  table drives a single-pass line state machine — O(lines × keywords), first-match-wins.
- **Split orchestration from the transaction boundary.** `ResumeService` is *not*
  `@Transactional` (file I/O must run outside a DB transaction), while
  `ResumePersistenceService` owns the atomic writes. Keeping them in separate beans is what
  makes Spring's `@Transactional` proxy actually apply — a self-invoked method would silently
  bypass it.

---

## Problems we faced & how we fixed them

### 1. Storage path needs the id before the row exists
The path scheme `{userId}/{resumeId}/{fileName}` requires the resume id up front, but
`@UuidGenerator` only assigns it at insert/flush time.
**Fix:** generate the id in the service (`UUID.randomUUID()`) and drop `@UuidGenerator` — the
established `User` pattern. One deterministic path, one clean insert.

### 2. Persisting `FAILED` while also returning 422 — the rollback trap
The spec wants a failed upload recorded as `status = FAILED` *and* the exception propagated as
422. But Spring rolls back a `@Transactional` method whenever a `RuntimeException` escapes —
so a single transaction would undo the `FAILED` write on the re-throw.
**Fix:** keep `uploadResume` non-transactional and write `FAILED` through
`ResumePersistenceService.saveResume` (its own transaction). With no outer transaction to roll
back, the `FAILED` row commits and survives; the 422 still propagates. No `REQUIRES_NEW` gymnastics.

### 3. "No try/catch in services" vs. "record FAILED and re-throw"
The coding convention forbids try/catch in services, but recording `FAILED` and re-throwing
inherently needs one.
**Fix:** a single, documented catch that does *state compensation only* (record the failed
attempt), never HTTP mapping — that stays in the global handler. This is the one justified
exception, and it's exactly what the `ResumeServiceTest` "status = FAILED" case pins down.

### 4. `ddl-auto: validate` makes the entities unforgiving
Because Hibernate validates entities against the live schema at boot, any type mismatch
(e.g. `TEXT`, `timestamptz`, enum length) would stop the app from starting.
**Fix:** map deliberately — `Instant`↔`timestamptz`, `Long`↔`BIGINT`, `@Enumerated(STRING)`
with explicit lengths, and `@Column(columnDefinition = "TEXT")` copied from the proven
`RawResumeEntity`. Verified by matching each entity field to its changeset column.

### 5. Getting `userId` into the controller safely
The JWT principal only carries the email, not the id.
**Fix:** the controller resolves email → `userId` via `UserRepository`; if that authenticated
user's row is gone, it raises `UsernameNotFoundException` (→ 401). `userId` is never taken from
the request.

### 6. Changeset 009 rejected by pre-existing orphan data (found on first live boot)
The first real boot failed applying 009: legacy `raw_resume` rows from the Phase-1 raw-intake
stub carry random `user_id` values (`RawResumeMapper` assigned `UUID.randomUUID()`), none of
which exist in `users` — so the FK to `users(id)` was rejected.
**Fix:** null those orphans first (`UPDATE raw_resume SET user_id = NULL WHERE user_id NOT IN
(SELECT id FROM users)`) inside the same changeset, before adding the constraint. That's the
same semantics the FK uses on delete (`SET NULL`), and `user_id` is nullable. Second boot:
006–009 all apply, `validate` passes.

### 7. `createdAt` null in the upload response — the assigned-id `merge()` trap (found in E2E)
The live end-to-end run showed `POST /upload` returning `createdAt: null`, while `GET` returned
it correctly. Root cause: with an **application-assigned** `@Id`, Spring Data's `save()` sees a
non-null id, assumes the row exists, and routes through `merge()`. `merge()` returns a *different*
managed instance (and runs `@PrePersist` on that copy), so the object we map into the response
never gets its audit values — and every insert pays for a needless pre-`SELECT`.
**Fix:** the three entities implement `Persistable<UUID>` with a `@Transient persisted` flag
(false when builder-created, flipped true on `@PostLoad`/`@PostPersist`). `isNew()` returns
`!persisted`, so `save()` uses `persist()` for new rows. Fixes the response *and* drops the
extra SELECT. Verified live: the upload response now carries `createdAt`.

---

## What we deferred to later phases

- **Async extraction** — upload is fully synchronous today. The `PROCESSING` / `PENDING` /
  `IN_PROGRESS` states exist in the enums but are reserved for when parsing is offloaded.
- **Orphan-file cleanup** — a `FAILED` attempt leaves the stored file on disk.
- **Content sniffing** — file type is resolved by extension only, not magic bytes.
- **Legacy `RawResumeController`** still isn't user-scoped (untouched intake module).

> **Local run note:** the app must reach the Dockerized PG16 on 5432. The native Windows
> `postgresql-x64-14` service shadows it — stop that service before running the backend from
> the host/IntelliJ (`net stop postgresql-x64-14`, elevated), or run the backend in Compose.

---

## Metrics — current functionality

| Metric                          | Value                                          |
|---------------------------------|------------------------------------------------|
| New endpoints                   | 5 (`/api/v1/resumes` …)                         |
| Persistence layers              | 3 (`resume` → `resume_version` → `resume_chunk`)|
| Liquibase changesets added      | 4 (006–009) — **applied on live PG16**          |
| Supported upload formats        | PDF (PDFBox), DOCX (Apache POI); ≤ 10MB         |
| Ownership enforcement           | `findByIdAndUserId`; wrong/missing → 404        |
| Exception → status              | 404 / 422 / 500 / 500, all via `ApiResponse`    |
| New unit tests passing          | 13 / 13 (chunker 6, extractor 4, service 3)     |
| Tests requiring a DB / context  | 0                                               |
| Phase-1 tests after changes     | 11 / 11 (no regression)                         |
| Carried-over gaps fixed         | 2 (raw-resume 500→404; `raw_resume.user_id` FK) |
| Live end-to-end verified        | register → upload → parse → 4 labelled chunks; POST returns `createdAt` |
| Post-live fixes                 | changeset 009 orphan-null; `Persistable` assigned-id insert |
