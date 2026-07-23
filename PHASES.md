# Phase Tracker — Index

Each phase has its own file under [`docs/phases/`](docs/phases/) telling that phase's story:
**what we built, why we built it that way, problems we faced, how we fixed them, and what we
deferred to later phases.** Keep each phase's detail in its own file — this page is only the index.

_Last updated: 2026-07-24._

| Phase | Focus                          | Status         | File |
|-------|--------------------------------|----------------|------|
| 0     | Foundation & scaffolding       | ✅ Complete    | [phase-0-foundation.md](docs/phases/phase-0-foundation.md) |
| 1     | Authentication (JWT)           | ✅ Complete    | [phase-1-jwt-auth.md](docs/phases/phase-1-jwt-auth.md) |
| 2     | Resume parsing & storage       | ✅ Complete\*  | [phase-2-resume-parsing.md](docs/phases/phase-2-resume-parsing.md) |
| 3     | JD intelligence                | 🔜 Next        | _not started_ |
| 4     | Matching engine                | ⬜ Pending     | _not started_ |
| 5     | Skill gap analysis             | ⬜ Pending     | _not started_ |
| 6     | Application tracking           | ⬜ Pending     | _not started_ |
| 7     | Interview preparation          | ⬜ Pending     | _not started_ |
| 8     | Feedback loop (OutcomeSignal)  | ⬜ Pending     | _not started_ |
| 9     | Frontend integration           | ⬜ Pending     | _not started_ |
| 10    | Production hardening           | ⬜ Pending     | _not started_ |

**Legend:** ✅ Complete · 🔜 In progress · ⬜ Pending

\* Phase 2 code + unit tests are complete (13 new tests passing); the only open item is
verifying changesets 006–009 against a live database via Docker Compose.

---

## How to use this tracker

- **Starting a phase?** Create `docs/phases/phase-N-<slug>.md` from the same five sections:
  _What we built · Why this way · Problems & fixes · Deferred to later · Metrics_.
- **Finishing a phase?** Fill in the metrics, flip the status here, and update `CLAUDE.md`.
- **One phase per file** — don't let one phase's detail bleed into another's page.
