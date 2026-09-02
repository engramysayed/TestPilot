# P8 — Execute & verify E3–E4

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** phase **P8** (depends on P7)  

---

## 1. Goal

Add **design-vs-actual** checks (reference screenshots per TC) and **bug report export** for execute runs.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| E3 references | `{projectRoot}/design-references/{tcId}.png` — upload via API |
| E3 compare | After prove, if reference + actual screenshot exist → Qwen dual-image compare |
| E3 evidence | `evidence/{tcId}/design-compare.json` + `design-actual.png` copy |
| E3 scope | Execute runs only; conversion path unchanged |
| E4 report | `GET /api/execute-runs/{jobId}/bug-report` (JSON) + `.csv` download |
| E4 rows | FAIL TCs only: tcId, title, reason, step, screenshot URLs, visual/design status |
| Dry-run | Skip compare; write `design-compare.json` status `SKIPPED` |
| UI | Execute results: design badge + Export bug report button |

---

## 3. Out of scope

- Figma integration, auto-clustering ML, Jira export
