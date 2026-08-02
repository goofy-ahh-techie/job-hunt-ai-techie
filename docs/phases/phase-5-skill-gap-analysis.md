# Phase 5 — Skill Gap Analysis

**Status:** ✅ Complete — code, unit tests (Java + Python), **and** a live end-to-end run
verified against Dockerized PostgreSQL 16, a real `ollama/ollama` container, and the FastAPI
service (match-tied analysis, standalone assessment, re-analysis upsert, and the service-down
failure path).
**Focus:** Turning a score into advice. Phase 4 answered *how well does this resume fit*;
Phase 5 answers *what is missing, how much does it matter, and what should I do about it*.
It is also the first phase where the LLM is used purely for judgement — everything mechanical
is computed in code and the model is overruled on it.

---

## What we built

Two modes over one pipeline:

1. **Match-tied** (primary) — reads the gaps Phase 4 already persisted, ranks them
   CRITICAL → HIGH → MEDIUM → LOW, and enriches each with a role-specific learning
   recommendation. Persisted per match, upserted on re-analysis.
2. **Standalone** (secondary) — assesses a resume with no target role: strong domains,
   weak domains, and what to learn next. Never persisted.

**intelligence-python additions** — `schemas/gap_schemas.py`, `prompts/gap_analysis_prompt.py`
(two sentinel-substituted templates), `services/gap_analysis_service.py`, `api/gap_routes.py`.
Endpoints `POST /gaps/analyze` and `POST /gaps/analyze-standalone` → `{success, data, error}`
(200/422/503). Tests: `tests/test_gap_analysis_service.py` (17, Ollama mocked).

**Endpoints** (`/api/v1/skill-gaps`, Bearer token; `userId` from the principal)
- `POST /match/{matchId}` → 201 (analyse, or re-analyse in place)
- `GET /{skillGapId}` · `GET /match/{matchId}` (404 if never analysed) ·
  `GET /resume/{resumeId}` · `GET /jd/{jdId}` (both `SkillGapSummaryResponse`) ·
  `GET /resume/{resumeId}/standalone`

**Persistence** — `skill_gap` (changeset 013), one row per match enforced by a UNIQUE
constraint on `match_result_id`. `gaps` holds a JSON array of **objects** — the first
structured JSON column in the codebase, where Phases 3 and 4 stored flat string arrays.

**Code** (`skillgap/` package) — `domain/` (`SkillGap`, `SkillGapStatus`, `GapPriority`,
`GapItem` value object), `repository/`, `extractor/` (`GapExtractor`, `ExtractedGaps`),
`client/` (`SkillGapIntelligenceClient` + four records + its own RestClient config), `dto/`,
`mapper/SkillGapMapper`, `service/` (`SkillGapService` + `SkillGapPersistenceService`),
`controller/SkillGapController`.

---

## Why we built it this way

- **Phase 5 detects nothing.** `GapExtractor` reads `must_have_missing` and `skills_missing`
  straight off the match row. Re-deriving them would mean re-running the whole matching engine
  — including its embedding calls — to reach an answer already stored, and would let the two
  phases disagree about the same resume. The one thing computed here is `preferredMissing`,
  because Phase 4 deliberately does not persist it: an unmatched nice-to-have is not a
  shortfall for a *score*, but it is worth mentioning in *advice*.
- **The LLM does judgement; code does rules.** Priority is decided entirely by which list an
  item came from, `quick_wins` is arithmetic on the estimates, and every supplied gap must
  appear exactly once. All three are enforced in the Python service after the model replies.
  The model is left with the parts only it can do: why a gap matters here, and how to close it
  given this candidate's background. Every one of those three rules was added because the
  model broke it live — see below.
- **The empty-gap case never reaches the model.** A perfect match short-circuits in
  `SkillGapService` with a fixed positive summary. Asking a model to explain an empty list
  invites it to invent one, which is the single worst failure a gap analysis can have.
- **Failure is recorded, not degraded around.** Unlike the Phase 4 scorers, which fall back to
  keyword matching, a gap analysis with no analysis is nothing at all. The row records
  `FAILED` + `analysis_error` and the exception propagates — visible through the API rather
  than as an empty result that reads like "no gaps found".
- **Standalone is deliberately not persisted.** It derives from the resume alone, so a stored
  copy would silently go stale the moment the resume changed, and recomputing costs one call.
- **A third RestClient bean.** JD extraction (130s), semantic matching (180s), and gap analysis
  (120s) have genuinely different worst cases. All three share a base URL and all three
  `@Qualifier` their injection point.

---

## Problems we faced & how we fixed them

### 1. The model copied the prompt's own example verbatim
The first live run returned, as its Kubernetes recommendation, the exact sentence used as the
`GOOD:` example in the prompt — because that example was about Kubernetes and the test data
also contained Kubernetes. A recommendation that is really a quoted template is worse than a
bland one: it looks specific while being fabricated.
**Fix:** rewrote the example around Elasticsearch (a skill unlikely to collide with the data)
and prefixed it with an explicit "this is about a DIFFERENT skill and candidate — copy its
shape, never its words".

### 2. Priority was assigned by vibes, not provenance
`Terraform` came from `missing_skills` (→ HIGH) and the model returned it as `CRITICAL`.
Priority in this design is *purely* a function of which list an item came from, so a model
judgement call there is not a nuance — it is an error.
**Fix:** `_enforce_priorities` overwrites every gap's priority from its source list, matching
leniently so the model's shortened "Kubernetes" still maps back to "Hands-on experience with
Kubernetes in production". Gaps matching no input list are the model's own inferences and are
pinned to LOW.

### 3. `quick_wins` disagreed with the model's own estimates
The model returned `quick_wins: []` while estimating a gap at one week. Two halves of one
answer contradicting each other.
**Fix:** `_derive_quick_wins` recomputes the list from `estimated_weeks <= 1`. The constant
`QUICK_WIN_MAX_WEEKS` had been defined and then never used — the rule existed in the code as
a comment and nowhere as behaviour.

### 4. The model silently dropped gaps
On one run only 3 of 5 supplied gaps came back; on another it paraphrased "Experience owning
services end to end" into "Service ownership" so the original looked absent. A dropped gap
disappears from the candidate's to-do list entirely.
**Fix:** two layers. The prompt now restates every gap as an explicit numbered checklist
(`skill="..." priority=...`) that the model must echo character-for-character — after which
the live run returned 5 of 5. `_restore_dropped_gaps` is the backstop, re-adding anything
still missing with an honest "no detailed guidance was generated" note rather than an invented
rationale, so a thin entry is never mistaken for a considered one.

### 5. `estimated_weeks` came back as prose
"2-3 weeks" instead of an integer, which would fail the `int` type and take the whole analysis
down over a formatting detail — and `quick_wins` is computed from that number.
**Fix:** a before-validator extracts the first integer, taking the low end of a range (the
point at which progress can start being claimed) and nulling anything unparseable.

### 6. Byte-truncating a curl response to strip a status marker
A verification script sliced the response with `head -c -13` and corrupted the JSON. Purely a
test-harness bug, but it briefly looked like a malformed API response.
**Fix:** split on the marker instead of counting bytes.

---

## What we deferred to later phases

- **Recommendation specificity is at the model's ceiling.** After the enumeration fix the
  model reliably covers every gap, but its prose became more formulaic ("This role's use of X
  depends on it"). It still names the candidate's own adjacent experience, which is what the
  Definition of Done required, but `mistral:7b` remains the documented upgrade path — the same
  quality ceiling recorded in Phase 3.
- **`deal_breakers` is the one judgement still fully trusted to the model.** Whether a resume
  shows adjacent experience is genuinely a judgement; code only constrains the answer to
  CRITICAL gaps. It came back empty on the live run, which was correct — the resume shows
  container orchestration — but it has not been observed on a resume that should populate it.
- **Gap analysis is synchronous**, blocking ~60s on the LLM. `ANALYZING` is persisted before
  the call, so the async offload has its state waiting.
- **No re-analysis trigger on match recalculation.** Recalculating a match leaves its gap
  analysis stale; `overall_score_context` records the score it was reasoned against, so the
  staleness is visible but not acted upon.
- **The perfect-match short-circuit is unit-tested only** — constructing a resume that satisfies
  every must-have of a real JD was not worth the live run.

> **Local run note:** gap analysis needs Ollama with `llama3.2:3b`, the intelligence service on
> `INTELLIGENCE_SERVICE_URL`, and the Dockerized PG16 (the backend was run against the 5433
> sidecar to sidestep the native pg14 shadowing 5432).

---

## Metrics — current functionality

| Metric                          | Value                                                        |
|---------------------------------|--------------------------------------------------------------|
| New endpoints                   | 6 (`/api/v1/skill-gaps` …) + 2 Python (`/gaps/analyze`, `/gaps/analyze-standalone`) |
| Liquibase changesets added      | 1 (013) — **applied on live PG16**                           |
| Upsert guarantee                | UNIQUE `match_result_id`; verified 1 row after 4 analyses    |
| Rules enforced in code, not LLM | 3 — priority from provenance, quick_wins from estimates, no gap dropped |
| LLM calls avoided               | perfect match short-circuits; standalone never persisted     |
| Failure visibility              | `FAILED` + `analysis_error` persisted, 503 returned (never a raw 500) |
| Ownership enforcement           | match, resume and gap all `findByIdAndUserId`; wrong/missing → 404 |
| Live analysis latency           | ~60s match-tied, ~15s standalone                             |
| New Java unit tests passing     | 33 / 33 — no DB / no context / no real Python                |
| New Python tests passing        | 17 / 17 (Ollama mocked)                                      |
| Prior tests after changes       | 100 / 100 Java + 12 / 12 Python (no regression)              |
| Live end-to-end verified        | analyse → 2 CRITICAL + 2 MEDIUM with role-specific advice; all GETs; standalone; re-analysis upsert; 404s; 401; 503 + FAILED persisted |
| Live-discovered fixes           | prompt example copied verbatim; priority by vibes; quick_wins ignored; gaps dropped (3 of 5 → 5 of 5); prose week estimates |
