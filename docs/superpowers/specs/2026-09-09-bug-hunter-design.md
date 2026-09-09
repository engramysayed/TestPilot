# Bug Hunter (Exploratory) — design

**Date:** 2026-09-09  
**Status:** Implemented (dry-run + live loop). Live path: browser observe → Ollama/Cursor planner → allowlisted actions → hunter pack. Network CDP best-effort.  
**DOM & memory quality:** see `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md` (page map, coverage, grounding, strategies).  
**Job kind:** `HUNT`

## Goal

Give QA an exploratory **Bug Hunter** that:

1. Breaks features deliberately (find defects), and  
2. Invents edge-case scenarios (capped),  

using the same evidence loop as classic TestPilot (brief + slim DOM + screenshot → planner JSON → follow), with optional network failure context. Humans review outputs; nothing auto-merges into the project library.

## Product placement

- New top-nav page: **Bug Hunter** (`/bug-hunter`), peer of Generate / Automate / Execute.
- Start form:
  - Project
  - Multi-select library TCs (from generated workbook)
  - Optional user story / free text
  - Planner: **Cursor** | **Ollama**
  - Tunables: `scenarioCap` (default **5**), `cycleCeiling` (default **8**)
- Job status via existing Status / jobs list (`jobKind=HUNT`).

## High-level flow

```
selected TCs + optional US
        → brief.md
        → open browser (project base URL / first TC open step)
        → loop until finish | cycleCeiling
              capture: slim DOM + screenshot [+ network failures]
              planner (Cursor|Ollama) → strict JSON
              execute actions | record bugs | record scenarios
        → hunter pack ZIP
```

## Stop rules

| Condition | Behavior |
|-----------|----------|
| Planner returns `finish` | End loop; write pack |
| `cycleCeiling` reached | End loop even if planner wants more; write pack with `stopReason=CYCLE_CAP` |
| Hard job cancel / force-stop | Same as other jobs |
| Scenario invent budget exhausted | Planner may still hunt bugs until finish/ceiling; no more scenarios accepted |

Scenario invent cap and cycle ceiling are independent. Scenario cap does **not** force finish.

## Cycle contract

### Inputs to planner (each cycle)

- `brief.md` (stable for the job)
- Caps: `scenarioCap`, `scenariosEmitted`, `cycleCeiling`, `cycleIndex`
- Latest evidence: slim DOM text, screenshot bytes/path, optional network failures
- Prior cycle summaries (compact): actions taken, bugs/scenarios already logged

### Planner JSON (strict)

```json
{
  "decision": "continue" | "finish",
  "rationale": "short string",
  "actions": [ /* optional; omit or [] when finish */ ],
  "bugs": [ /* optional findings this cycle */ ],
  "scenarios": [ /* optional candidate edge cases; server truncates to remaining invent budget */ ]
}
```

**Actions** (v1 allowlist — extend later only with explicit design):

- `navigate` `{ "url": "..." }`
- `click` `{ "locator": "..." }` / intent text form if bind layer reused
- `type` `{ "locator": "...", "value": "..." }`
- `clear` `{ "locator": "..." }`
- `wait` `{ "ms": 500 }` (capped)
- `assert_visible` / `assert_text` (record pass/fail into cycle log; failed assert may auto-promote a bug draft)

Unsafe / out of allowlist actions are rejected and logged; they do not crash the job.

**Bugs** (minimal fields; mapped into Execute-compatible bug-report columns plus hunter extras):

- `title`, `severity` (`S1`–`S4` or `blocker|major|minor|trivial`)
- `repro` (steps text)
- `expected`, `actual`
- `evidenceHint` (optional; Keel attaches cycle screenshot paths)

**Scenarios** (candidate only):

- `title`, `steps`, `expected`, optional `tags`
- Server enforces `scenariosEmitted + new <= scenarioCap`

## Evidence & network

Each cycle writes under the job/project hunt store:

- `cycles/cycle-NN/dom-slim.txt`
- `cycles/cycle-NN/screenshot.png`
- `cycles/cycle-NN/network-failures.json` (may be empty array)
- `cycles/cycle-NN/planner-request.md` / `planner-response.json`
- `cycles/cycle-NN/actions-log.json`

**Network (v1):** best-effort CDP/DevTools capture of failed requests (4xx/5xx, net::ERR_*). If unavailable, omit with `networkCapture=unsupported` in SUMMARY — do not fail the hunt.

## Outputs — hunter pack ZIP

Downloadable when job completes (same download affordance as Automate packages where applicable):

| Path | Content |
|------|---------|
| `brief.md` | Selected TCs + optional US |
| `SUMMARY.md` | stop reason, cycles used, bug/scenario counts, planner mode |
| `bug-report.json` | Execute-shaped `rows` + hunter metadata |
| `bug-report.csv` | Same columns as Execute bug CSV **plus** `severity`, `repro` when present |
| `candidate-scenarios.json` | Invented edge cases (≤ cap) |
| `candidate-scenarios.csv` | Flattened review table |
| `cycles/**` | Per-cycle evidence + planner I/O |

No automatic write into `generated/` library or `ir/` Automate drafts.

## Job / control plane

- New `JobRecord.JobKind.HUNT`
- Worker: `HuntWorker` (or phase pipeline: Brief → Loop → Pack)
- Progress: `progressCurrent` = cycle index, `progressTotal` = cycleCeiling; message shows last decision / bug count
- Credentials: reuse project credential profile selection like Execute/Automate when login is required to reach the feature under hunt

## Planner backends

| Mode | Role |
|------|------|
| **Ollama** | Local JSON planner; screenshot attached when model supports vision; otherwise DOM-primary |
| **Cursor** | Same JSON contract via Cursor/agent bridge already used for Cursor heal paths; if bridge unavailable, job fails closed with clear error (no silent fallback unless user selected Ollama) |

Both must obey the same schema and caps. Server truncates invent overflow; server ignores actions after `finish`.

## Portal UX (v1)

- `/bug-hunter`: project, TC multi-select, US textarea, planner radio, caps, Start
- Recent HUNT jobs for project (status, bug count, scenario count, Download pack)
- Status page shows kind **Bug Hunter** and links to download when terminal

## Non-goals (v1)

- Auto-merge candidate scenarios into the library
- Unbounded invent or unbounded cycles
- Full network HAR export / performance profiling
- Replacing Automate prove or Execute regression
- Multi-browser matrix in one hunt

## Open implementation notes

- Assert failures auto-promote bug drafts when the planner emits no `bugs[]` for that cycle.
- Login uses the selected credential profile via `JobLoginService` when username is present; otherwise navigates to base URL.
- CDP Network uses Selenium DevTools v142; mismatches mark `networkCapture=unsupported` without failing the hunt.

## Success criteria

- User can start a hunt from selected library TCs + optional US
- Loop stops on `finish` or cycle ceiling
- Scenario invent never exceeds configured cap
- Completed job yields downloadable hunter pack ZIP with brief, SUMMARY, bug-report, candidates, and cycle evidence
- Library workbook unchanged unless user later imports candidates manually
