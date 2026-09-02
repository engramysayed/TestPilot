# P7 — Execute & verify E1–E2

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P7**  
**Depends on:** P2 Projects hub, existing ProvePhase + vision stack  

---

## 1. Goal

Run manual Excel TCs **without codegen** — prove-only jobs with pass/fail, step evidence screenshots, and existing UI-TARS weak-bind grounding (E2) via `ProvePhase`.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Engine | **Reuse `ProvePhase`** — no new runner |
| Job model | `jobKind` on `jobs` table: `CONVERT` (default) \| `EXECUTE` |
| Storage | `{projectRoot}/execute-runs/{jobId}/ir` + `evidence/` |
| API | `POST /api/projects/{id}/execute-runs`; results under `/api/execute-runs/{jobId}/...` |
| Screenshots | Execute-scoped route (re-enable serving; conversion project route stays 410) |
| Dry-run | `DryRunExecuteService` writes stub IR (tests without browser) |
| UI | `/execute` — project picker, upload, start, poll, TC results + step screenshots |
| QA labels | `PASSED` → **PASS**; `PARTIAL`/`TODO` → **FAIL** |
| Out of scope | E3 design-vs-actual, E4 bug export, doc upload, in-portal LLM |

---

## 3. Spec self-review

- [x] Reuses prove + vision (E2 automatic)
- [x] Separate from conversion ZIP emit
- [x] Evidence viewing restored for execute runs only
- [x] P8 deferred
