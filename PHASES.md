# Phase Tracker — Index

Each phase has its own file under [`docs/phases/`](docs/phases/) telling that phase's story:
**what we built, why we built it that way, problems we faced, how we fixed them, and what we
deferred to later phases.** Keep each phase's detail in its own file — this page is only the index.

_Last updated: 2026-08-02 (Phase 4 complete)._

| Phase | Focus                          | Status         | File |
|-------|--------------------------------|----------------|------|
| 0     | Foundation & scaffolding       | ✅ Complete    | [phase-0-foundation.md](docs/phases/phase-0-foundation.md) |
| 1     | Authentication (JWT)           | ✅ Complete    | [phase-1-jwt-auth.md](docs/phases/phase-1-jwt-auth.md) |
| 2     | Resume parsing & storage       | ✅ Complete    | [phase-2-resume-parsing.md](docs/phases/phase-2-resume-parsing.md) |
| 3     | JD intelligence                | ✅ Complete    | [phase-3-jd-intelligence.md](docs/phases/phase-3-jd-intelligence.md) |
| 4     | Matching engine                | ✅ Complete    | [phase-4-matching-engine.md](docs/phases/phase-4-matching-engine.md) |
| 5     | Skill gap analysis             | 🔜 Next        | _not started_ |
| 6     | Application tracking           | ⬜ Pending     | _not started_ |
| 7     | Interview preparation          | ⬜ Pending     | _not started_ |
| 8     | Feedback loop (OutcomeSignal)  | ⬜ Pending     | _not started_ |
| 9     | Frontend integration           | ⬜ Pending     | _not started_ |
| 10    | Production hardening           | ⬜ Pending     | _not started_ |

**Legend:** ✅ Complete · 🔜 In progress · ⬜ Pending

Phase 2 is fully verified: 13 unit tests plus a live end-to-end run (migrations 006–009 applied
on PG16, upload → parse → read works). Two fixes came out of the live run — see its phase doc.

Phase 3 is fully verified: 10 new Java unit tests + 4 Python pytest, plus a live end-to-end run
(migrations 010–011 applied on PG16, paste + PDF upload → structured extraction via FastAPI →
Ollama `llama3.2:3b`, and the service-down 503 + `FAILED` path confirmed in the DB). Six fixes
came out of the live run — timeouts on both sides of the service boundary, prompt hardening, and
`temperature=0`/`num_ctx=8192` — see its phase doc.

Phase 4 is fully verified: 66 new Java unit tests + 6 Python pytest, plus a live end-to-end run
(changeset 012 applied on PG16, resume + JD → a 74.50 match with all six sub-scores, recalculation
upserting in place, and a Python-service-down run still returning 201 on keyword-only scoring).
Five fixes came out of the live run, two of them structural — must-have coverage was pinned at
zero and big chunks were diluting the embeddings, together worth **51.50 → 74.50** on the same
pair. See its phase doc.

---

## How to use this tracker

- **Starting a phase?** Create `docs/phases/phase-N-<slug>.md` from the same five sections:
  _What we built · Why this way · Problems & fixes · Deferred to later · Metrics_.
- **Finishing a phase?** Fill in the metrics, flip the status here, and update `CLAUDE.md`.
- **One phase per file** — don't let one phase's detail bleed into another's page.
