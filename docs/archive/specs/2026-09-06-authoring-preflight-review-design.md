# Design: Authoring Preflight Review (Cursor | Ollama)

**Date:** 2026-09-06  
**Status:** Implemented  
**Plan:** `docs/superpowers/plans/2026-09-06-authoring-preflight-review.md`  
**Product surface:** Generate (after import / generate / upload → saved workbook)  
**Related:** quality gate + `TcImportRepair`; Cursor heal sidecar; Ollama `LocalLlmClient`; distinct from Execute heal recovery and Automate final-revise

## Problem

Uploaded or imported TCs go through deterministic repair + quality gate, but there is no authoring-time AI pass to:

- Spot missing scenarios vs requirements/stories  
- Clear ambiguities before Execute/Automate burns browser time  
- Propose leave-empty / TestData / vague-assert fixes with a human Accept gate  

Final revise (AgentRouter) runs **after** prove on Automate — too late for cheap authoring cleanup.

## Goals

1. Optional **Review with AI** on Generate when a generated workbook exists.  
2. User chooses provider: **Cursor** or **Ollama** (same review contract).  
3. Return **findings + proposed cases**; preview like Generate; **Accept** saves workbook; **Discard** keeps current.  
4. When quality gate / authoring rules flag issues on import/save, show a **banner** CTA to open Review (does **not** auto-call the LLM).  
5. Ground truth: current suite JSON + optional project stories/PRD + optional free-text requirements notes.

## Non-goals

- Silent overwrite of Excel  
- Auto-start review without user click  
- Silent fallback between Cursor ↔ Ollama  
- Captcha / 2FA / multi-obstacle Execute agent  
- Merging this into heal-recovery or final-revise  
- Free-form invent of an entire suite with no Accept  

## Locked decisions

| Topic | Choice |
|-------|--------|
| Accept UX | **B** — proposed suite in preview; Accept saves |
| Trigger | **A+C** — always optional button; banner when gate/authoring issues |
| Auto-offer UX | **A** — banner only; user still clicks Review |
| Ground truth | **B+C** — TCs + stories when available + optional notes |
| Providers | **1+2** — user picks Cursor or Ollama |

## Approach

Shared `AuthoringReviewService` + provider adapters:

1. Load current `generated/latest.xlsx` cases (source of truth).  
2. Attach optional stories artifacts + `requirementsNotes`.  
3. Call chosen provider with structured JSON contract.  
4. Parse → repair → quality gate on **proposed** cases.  
5. Return preview payload; persist only on Accept.

## Architecture

### UI (Generate)

- **Review with AI** panel when workbook available:
  - Provider toggle: Cursor | Ollama  
  - Requirements notes textarea  
  - Primary: Start review  
- Banner after QUALITY_GATE / authoring warnings: “Issues found — Review with AI?” → focuses panel  
- Results: findings list + proposed CSV/grid → **Accept** / **Discard**  
- Remember last provider in `localStorage`

### API

`POST /api/projects/{projectId}/generate/authoring-review`

Request:

```json
{
  "provider": "cursor" | "ollama",
  "requirementsNotes": "optional",
  "includeStories": true
}
```

Behavior:

- Reads current generated workbook (404 if missing)  
- Does **not** write Excel  
- Returns:

```json
{
  "provider": "cursor",
  "findings": [
    { "severity": "error|warn|info", "tcId": "TC_01", "message": "..." }
  ],
  "csv": "...",
  "cases": [ /* optional mirror of csv */ ],
  "coverageNotes": "optional",
  "gateErrors": []
}
```

If proposed cases fail the gate: include `gateErrors`; UI disables Accept (or Accept re-validates and fails).

Accept: reuse existing import/save path (`saveFromCases` / import) with the proposed CSV/cases after server-side gate — either client posts to existing generate import endpoint or a thin `POST .../authoring-review/accept` that saves the last review payload / posted cases. Prefer **client posts proposed csv to existing import** to avoid duplicate save logic, unless session storage of last review is cleaner for audit — plan may choose one; default: **Accept = import proposed CSV through existing import API**.

### Services

| Piece | Role |
|-------|------|
| `AuthoringReviewService` | Orchestrate payload, dispatch, parse, repair, gate |
| Cursor adapter | `mode: "authoring-review"` via heal sidecar / `CursorHealClient` |
| Ollama adapter | `LocalLlmClient.completeJson` with identical contract |
| Stories loader | Best-effort load of project generate-batch / story artifacts when `includeStories` |

### Shared LLM contract

```json
{
  "findings": [{ "severity": "error|warn|info", "tcId": "", "message": "" }],
  "cases": [
    {
      "tcId": "TC_01",
      "title": "",
      "preconditions": "",
      "steps": "",
      "expectedResult": "",
      "priority": "",
      "tags": "",
      "visualAssertion": "",
      "testData": "",
      "keelPath": "EXECUTE"
    }
  ],
  "coverageNotes": ""
}
```

Rules for the model (prompt):

- Prefer clarifying / repairing existing cases over inventing large new suites  
- Flag missing coverage vs stories/notes in `findings`  
- Keep Keel authoring rules (leave-empty ↔ blank TestData, no vague unquoted asserts, TC_id shape)  
- Return **full** proposed suite (all cases), not a diff-only list  

## Data flow

```
workbook exists
  → [optional] gate warning banner
  → user picks Cursor|Ollama + notes
  → authoring-review API
       → build JSON (cases + stories? + notes)
       → provider
       → parse + repair + gate
  → preview findings + proposed suite
  → Accept → existing import/save (+ gate)
  → Discard → no write
```

## Error handling

| Case | Behavior |
|------|----------|
| No workbook | Clear error; no review |
| Provider disabled / unreachable | Fail closed; message names provider; no silent switch |
| Bad / empty LLM JSON | Error; workbook unchanged |
| Gate fail on proposal | Return findings + gateErrors; Accept disabled until fixed |
| Timeout | Error status; no write |
| No stories | Proceed; finding may note stories not attached |
| Findings only (identical cases) | Show findings; Accept disabled or labeled no-op |
| Suite too large | Soft reject/warn above configured cap (plan picks N, ~40–50); no silent truncate |

## Testing

1. Review JSON parser — happy path, garbage reject, empty cases reject  
2. Service — mocked Cursor / Ollama; unavailable provider errors  
3. Gate — bad leave-empty proposal surfaces gateErrors; Accept path rejects  
4. API — review does not mutate `latest.xlsx`; Accept via import does  
5. Manual smoke — bad import → banner → Review (each provider) → Accept → workbook updated  

## Success criteria

- User can run authoring preflight with **either** Cursor or Ollama and Accept a proposed suite into the project workbook.  
- Banner appears on gate/authoring issues without auto-spending LLM calls.  
- Execute heal recovery and Automate final-revise remain separate.  
