# Phase 4 — Matching Engine

**Status:** ✅ Complete — code, unit tests (Java + Python), **and** a live end-to-end run
verified against Dockerized PostgreSQL 16, a real `ollama/ollama` container, and the FastAPI
service (resume + JD → scored match, recalculation upsert, and the service-down degradation path).
**Focus:** The first phase that *derives* rather than extracts. Phases 2 and 3 produced facts;
this one turns two independent fact sets — resume chunks and JD intelligence — into a scored,
explainable judgement. Six componentised sub-scores, hybrid Java-deterministic + Python-semantic.

---

## What we built

**Scoring model** (locked): six sub-scores, weighted to one 0–100 overall.

| # | Sub-score | Weight | Mechanism |
|---|-----------|--------|-----------|
| 1 | Must-Have Coverage | 30% | keyword, then semantic for the misses |
| 2 | Required Skills | 25% | keyword, then semantic for the misses |
| 3 | Responsibilities Overlap | 20% | semantic (EXPERIENCE chunks), keyword fallback |
| 4 | Experience Level Fit | 12% | year-range regex → span vs. JD min |
| 5 | Qualifications | 8% | keyword + degree-abbreviation table |
| 6 | Preferred Skills Bonus | 5% | keyword; never a penalty |

**intelligence-python additions** — `schemas/match_schemas.py`, `services/semantic_similarity_service.py`
(embed each side once, compare in numpy), `api/match_routes.py` (`POST /match/semantic-similarity`
→ 200/503), plus an `embed()` method on the existing `OllamaClient` and `numpy` in requirements.
Tests: `tests/test_semantic_similarity_service.py` (6, Ollama mocked).

**Endpoints** (`/api/v1/matches`, all require a Bearer token; `userId` from the principal)
- `POST /` → 201 — score a resume against a JD; upserts on recalculation
- `GET /{matchId}` → 200 · `GET /resume/{resumeId}` → 200 · `GET /jd/{jdId}` → 200

**Persistence** — `match_result` (changeset 012), one row per
`(resume_version_id, job_description_id)` enforced by a UNIQUE constraint; all six sub-scores
stored individually as `NUMERIC(5,2)`, gap arrays as JSON `TEXT`, plus `score_explanation`,
`status`, and `last_calculated_at`.

**Code** (`matching/` package) — `domain/` (`MatchResult`, `MatchStatus`), `repository/`,
`scoring/` (`SubScorer` interface + six implementations, `SubScoreOrchestrator`,
`WeightedScoreCalculator`, `ScoreWeights`, `ScoreExplanationBuilder`, `KeywordMatcher`,
`TextPassages`), `client/` (`MatchingIntelligenceClient` + records + its own RestClient config),
`dto/`, `mapper/MatchMapper`, `service/` (`MatchService` + `MatchPersistenceService`),
`controller/MatchController`.

---

## Why we built it this way

- **Strategy pattern for the sub-scores.** Six `SubScorer` implementations behind one interface,
  a separate orchestrator to run them and a separate calculator to weight them. Adding a seventh
  dimension is a new class plus a weight, not an edit to the five that already work — and each
  scorer stays a pure function over `ScoringContext`, which is why all of them unit-test with no
  Spring context and no database.
- **Cheap pass before expensive pass.** Skills and must-haves run keyword containment first and
  send only the *misses* for embedding. A keyword hit and a semantic hit both mean "present", so
  the ordering costs nothing in correctness and saves most of the LLM calls.
- **Degrade, never fail.** The two semantic scorers catch `IntelligenceServiceUnavailableException`
  themselves and fall back to keyword-only with a note in the explanation. A user who asked for a
  match gets four-fifths of an answer instead of a 503 — verified live.
- **Failures are isolated per scorer.** `SubScoreOrchestrator` records a throwing scorer as 0.0
  with the reason and runs the rest. The dimensions are genuinely independent, so aborting the
  match would discard five correct answers to avoid reporting one wrong one.
- **Embed each side once, not pairwise.** N phrases × M texts would be N×M Ollama calls;
  embedding both sides once and comparing vectors in numpy is N+M and gives identical answers.
- **Two RestClient beans, not one.** Semantic matching embeds every resume passage plus every
  unmatched phrase, so its worst case is a multiple of a JD extraction's — it gets its own 180s
  read timeout. Both clients now `@Qualifier` their injection point so the second bean cannot
  silently make the Phase 3 wiring ambiguous.
- **Explanation is the deliverable.** A score of 62 tells a user nothing actionable; "2 of 4
  must-haves found, missing Kubernetes" does. Each scorer owns its own sentence and the builder
  only orders them.

---

## Problems we faced & how we fixed them

### 1. The generative model cannot produce embeddings at all
The spec said to embed with `OLLAMA_MODEL`. Ollama 0.32.3 answers `/api/embeddings` for
`llama3.2:3b` with `{"error":"This server does not support embeddings"}` — llama.cpp only exposes
the embedding head when the model was loaded for it.
**Fix:** pulled `nomic-embed-text` (274MB vs 2GB) and added `ollama_embedding_model` as its own
setting. It returns a vector in ~0.2s, so the semantic passes are *faster* than extraction, not
slower.

### 2. The specced 0.65 similarity threshold rejected every true positive
Calibrated live against real resume prose. True positives — "Kubernetes" against "operated
container orchestration clusters" — land at **0.52–0.60**; unrelated skills (Rust, COBOL,
Salesforce) at **0.35–0.45**. A 0.65 bar rejected 100% of the true positives, which would have
made the semantic pass dead weight.
**Fix:** skills threshold → **0.50**, sitting in the measured gap. Same lesson shape as Phase 3's
30s Ollama timeout: a specced constant that measurement disproves.

### 3. …and the threshold *relationship* was inverted
The spec set responsibilities *lower* than skills, reasoning that prose is harder to match.
Measurement says the opposite: prose-to-prose true positives score **0.65–0.74** while a bare
skill noun against a paragraph scores 0.52–0.60. Length asymmetry pulls short phrases away from
long text regardless of meaning.
**Fix:** responsibilities stays at 0.60 (validated), skills sits *below* it at 0.50. All three
thresholds are now config keys under `matching.semantic.*`, to be re-tuned whenever the embedding
model changes.

### 4. Must-have coverage was structurally pinned at zero (found in the live run)
The first real match scored **0 of 4 must-haves** on a resume that plainly satisfied them. The
cause is a spec assumption that the data contradicts: Phase 3 returns must-haves as whole
sentences ("Strong experience with Java and Spring Boot in production"), and no resume contains
those verbatim. Keyword-only matching therefore pinned the *heaviest* dimension (30%) at zero for
every LLM-extracted JD and filled `must_have_missing` with the entire requirement list — which
also made the spec's own Definition of Done ("genuine gaps") unsatisfiable.
**Fix:** gave `MustHaveCoverageScorer` the same two-pass shape as required skills, with the prose
threshold. A genuinely unmet must-have still costs full weight; the fix stopped *met* ones being
reported as missed.

### 5. Big chunks dilute embeddings (found in the same run)
Responsibilities scored 1 of 5 despite the resume covering four. An embedding is one vector for
whatever it is given, so a 158-word EXPERIENCE chunk covering five achievements yields their
*average*. Measured: "Mentor and grow junior engineers" scores **0.74** against the sentence about
coaching juniors but only **0.55** against the whole chunk containing it — under threshold, scored
as a miss.
**Fix:** `TextPassages` splits chunk text to bullets and sentences before semantic comparison
(deduplicated, capped at 60 to bound cost). Keyword matching still uses whole text, where size is
irrelevant.

**Combined effect of #4 and #5 on the same resume + JD: 51.50 → 74.50.**

### 6. ArgumentCaptor aliasing hid the CALCULATING state (test bug)
`MatchService` mutates one entity across both saves, so a captor held two references to the same
object and reported `COMPLETED` twice.
**Fix:** record the status inside the mock's `Answer`, at call time, rather than inspecting the
object afterwards.

---

## What we deferred to later phases

- **Async scoring** — a match takes ~12s (embeddings) and blocks the request. `CALCULATING` exists
  on the entity for exactly this, and is already written before scoring starts.
- **Acronym matching** — "CI/CD" scores 0.371 against "build and deployment pipelines in Jenkins",
  a true positive the embedder misses. Acronyms are the clearest remaining gap and are
  `SkillRegistry` territory.
- **Substring false positives** — one- and two-character skills ("R", "Go") match inside unrelated
  words, and "Kubernetes" ≠ "K8s". Same root cause: skills are compared as raw strings.
  `SkillRegistry` (core IP) is the phase that fixes it.
- **Per-user or per-role weights** — `ScoreWeights` is already a value object rather than six
  constants, so this is a wiring change rather than a rewrite.
- **Full evidence on read** — only the three keyword dimensions persist their matched/missing
  lists; a GET reconstructs the rest from `score_explanation`.

> **Local run note:** matching needs Ollama running with **both** models
> (`llama3.2:3b` for JD extraction, `nomic-embed-text` for matching), the intelligence service on
> `INTELLIGENCE_SERVICE_URL`, and the Dockerized PG16. The backend was run against the 5433
> sidecar to sidestep the native pg14 shadowing 5432.

---

## Metrics — current functionality

| Metric                          | Value                                                        |
|---------------------------------|--------------------------------------------------------------|
| New endpoints                   | 4 (`/api/v1/matches` …) + 1 Python (`POST /match/semantic-similarity`) |
| Sub-scores                      | 6, individually persisted + weighted overall (0–100, 2dp)    |
| Liquibase changesets added      | 1 (012) — **applied on live PG16**                           |
| Upsert guarantee                | UNIQUE `(resume_version_id, job_description_id)`; verified 1 row after 4 runs |
| Embedding cost                  | N+M calls, not N×M (asserted in a Python test)               |
| Service boundary                | Java `RestClient` → FastAPI → Ollama `nomic-embed-text`      |
| Degraded mode                   | Python down → **201 COMPLETED**, keyword-only, noted in explanation (never 503) |
| Ownership enforcement           | `findByIdAndUserId` on resume, JD, and match; wrong/missing → 404 |
| Live match latency              | ~12s (embeddings), vs ~42s for the JD extraction it consumes |
| New Java unit tests passing     | 66 / 66 — no DB / no context / no real Python                |
| New Python tests passing        | 6 / 6 (Ollama mocked)                                        |
| Prior tests after changes       | 34 / 34 Java + 4 / 4 Python (no regression)                  |
| Live end-to-end verified        | register → upload resume → paste JD → match 74.50; recalc upserts; GETs; 404s; 401; degraded mode |
| Live-discovered fixes           | embedding model; threshold 0.65→0.50; threshold ordering inverted; must-have semantic pass; passage splitting (**51.50 → 74.50**) |
