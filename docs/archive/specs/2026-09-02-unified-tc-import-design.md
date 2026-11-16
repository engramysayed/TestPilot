# Design: Unified TC Import + Fixed External Generate Path

**Date:** 2026-09-02  
**Status:** Approved in brainstorming (Approach 2) — awaiting user review of this file before implementation plan  
**Product:** Keel (TestPilot Delivery)

## Problem

Users who follow the **Copy prompt → Cursor/ChatGPT → CSV** path get output that is hard or impossible to use in Keel:

1. External models often smash multiline Steps/Expected/TestData into single lines (literal spaces or `\n` instead of real newlines).
2. The Generate UI tells them to “copy CSV into Excel, then upload via Automate” — no first-class **paste into Keel** path.
3. Portal Generate (Ollama) and external AI paths do not share one import/quality door, so quality rules drift.
4. Confusion about **blank KeelPath**: whether Automate/Execute will run those rows.

## Goals

1. **Two first-class paths** to a project workbook: in-portal Generate (Ollama) **and** Paste/Import from external AI.
2. **One shared import service** (parse → repair → quality gate → save) used by Generate save, Compare save, and Paste/Import.
3. **Fix or replace misleading prompts** so external AI output is importable (prefer JSON; CSV still supported with proper quoting).
4. **Document and preserve** blank KeelPath = eligible on **both** Automate and Execute (legacy).
5. **Verify with automated tests** (and manual smoke when portal is available) before calling the work done.

## Non-goals

- Removing Ollama Generate.
- Auto-filling blank KeelPath to AUTOMATE/EXECUTE.
- Soft-saving authoring failures (Phone field, vague asserts, etc.).
- Redesigning Automate prove/heal itself.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Primary product shape | Both portal Generate **and** paste-import |
| Blank KeelPath | Keep legacy: runnable on **both** Automate and Execute |
| Paste formats | Accept **JSON and CSV** (auto-detect) |
| Quality on import | **Auto-repair safe issues**, then **hard-block** remaining gate errors |
| Architecture | Shared Import Service as the single door |

---

## Architecture

```text
Raw text (JSON | CSV)  or  Excel upload (Automate/Execute)
        ↓
  Format detect / Excel → List<ManualTestCase>
        ↓
  Auto-repair (safe only)
        ↓
  GenerateQualityGate + GenerateAuthoringRules
        ↓
  fail → errors, no save / no job start
  pass → GeneratedWorkbookService.save  OR  continue Automate/Execute job
        ↓
  UI preview + “Open Automate / Execute” (use generated workbook)
```

### New / central component

**`TcImportService`** (name may vary; single public entry):

```text
ImportResult importText(projectId, ownerUserId, rawText, sourceLabel)
ImportResult importCases(projectId, ownerUserId, List<ManualTestCase>, sourceLabel)
```

Responsibilities:

1. Strip markdown fences (\`\`\`json / \`\`\`csv).
2. Detect JSON vs CSV (trim, leading `{` / header row).
3. Parse via existing `GeneratedTcJsonParser` / `GeneratedTcCsvParser` (extend CSV for RFC4180 multiline if needed).
4. Apply **safe repair** (see below).
5. Run `GenerateQualityGate.validate(cases, project.baseUrl)`.
6. On success: `GeneratedWorkbookService.saveFromCases(...)` with source e.g. `PASTE_IMPORT` / `GENERATE_SYNC` / `GENERATE_COMPARE`.
7. Return the same payload shape Generate already returns (rows, csv, counts, keelPathCounts, coverageNotes optional).

### Callers (must use the service)

| Caller | Behavior after change |
|--------|------------------------|
| Portal Generate (async batch complete / sync save paths that persist workbook) | Save through import service (or equivalent shared gate+save) |
| Compare → Save A/B | Route through import service |
| **New** `POST /api/projects/{id}/generate/import` | Paste/Import API |
| Automate / Execute Excel upload | After `ExcelTcReader`, run **repair + gate**; reject job create on failure |

---

## Auto-repair (safe only)

| Repair | Yes |
|--------|-----|
| Strip surrounding markdown code fences | Yes |
| Replace literal two-char `\n` with real newlines in steps / expected / testData | Yes |
| Trim tcId and KeelPath token whitespace | Yes |
| Normalize known KeelPath casing (`automate` → `AUTOMATE`) when token is valid | Yes |
| Invent or rewrite field labels / assert wording | **No** |
| Fill blank KeelPath with AUTOMATE/EXECUTE | **No** |

After repair, run the full quality gate. Remaining errors → hard fail with `QUALITY_GATE` detail.

---

## KeelPath matrix (product contract)

| Value | Automate | Execute |
|-------|----------|---------|
| blank / missing | Eligible | Eligible |
| `AUTOMATE` | Eligible | Eligible |
| `EXECUTE` | Skipped | Eligible |
| `VISION_ONLY` | Skipped | Eligible |
| `MANUAL` | Skipped | Skipped |
| invalid token | Reject at import/gate | — |

UI copy on Generate Import and Automate/Execute upload:  
**“Blank KeelPath runs on both Automate and Execute.”**

Existing `KeelPathCaseFilter` already implements blank → both; do not change that semantics.

---

## UI

### Generate page

1. **Generate with Keel** — existing async Ollama button (unchanged behavior).
2. **Paste from external AI** — primary panel (not only buried under details):
   - Large textarea
   - Button: **Import into project**
   - Success: same results preview as Generate; status: workbook saved; links to Automate / Execute
   - Failure: show gate/parse errors inline
3. **Copy-prompt workflow** — keep under details or secondary, but rewrite steps:
   1. Copy prompt (project name + base URL)
   2. Paste stories into Cursor/ChatGPT/etc.
   3. Paste the AI’s **JSON or CSV only** into **Import into project** (not “Excel then Automate” as the only path)

### Automate / Execute

- Keep Excel upload + **Use latest generated workbook**.
- On Excel upload: repair + gate before queueing; surface errors like Generate Import.
- Happy path after Paste/Generate: select **Use latest generated workbook**.

---

## Prompts

### Keep both prompt files; fix content

| File | Role after change |
|------|-------------------|
| `keel-tc-generate-from-stories-to-json.txt` | **Primary** for portal Generate **and** copy-prompt (JSON preferred) |
| `keel-tc-generate-from-stories-to-csv.txt` | Secondary; must require Excel-style quoting for multiline fields; state that Keel Import accepts CSV |

### Prompt requirements

1. Prefer JSON with real newline characters inside strings.
2. Combined login identifier → `Email or phone field` (map Email/Mobile).
3. Empty fields → `Leave the … empty` + blank testData lines.
4. Asserts quote exact messages; forbid vague validation phrases.
5. Explicit KeelPath guidance: use AUTOMATE/EXECUTE/VISION_ONLY/MANUAL when known; **blank is allowed** and means both surfaces in Keel.
6. Instruct: paste output into Keel **Import into project** (not Excel-only workflow).
7. Remove contradictory examples that teach standalone `Email field` on combined boxes.

If a prompt cannot be made safe for import, **remove it from the UI copy-prompt list** rather than leaving a broken path.

---

## API

```http
POST /api/projects/{projectId}/generate/import
Content-Type: application/json
X-Keel-Requested-With: Keel

{ "raw": "<json or csv text>", "format": "auto" | "json" | "csv" }
```

Responses:

- `200` — same shape as successful generate/save (projectId, rows, csv, counts, keelPathCounts, …)
- `400` — `BAD_REQUEST` / `INVALID_FORMAT`
- `422` — `QUALITY_GATE` or `INVALID_CSV` / parse errors with message detail
- `404` — unknown project

---

## Error handling

| Failure | User sees |
|---------|-----------|
| Empty paste | “Paste JSON or CSV first” |
| Unparseable | Parse error + hint to use JSON or quoted CSV |
| Quality gate | List of rule failures (tcId + reason); no workbook write |
| Archived project | Existing conflict behavior |

Batch Generate fail-loud (`last failure: QUALITY_GATE:…`) remains as already implemented.

---

## Testing / verification (implementation must pass)

1. **Repair:** smashed-newline / literal `\n` CSV or JSON → after repair, gate passes for golden Facebook-negative cases.
2. **Reject:** standalone Phone field / vague assert → import API returns QUALITY_GATE.
3. **KeelPath blank:** cases with blank path pass `KeelPathCaseFilter` for AUTOMATE and EXECUTE surfaces.
4. **MVC:** Generate page contains Import UI + updated copy-prompt steps (no “Excel only” as sole path).
5. **Prompt resource tests:** JSON (and CSV) prompts mention Import into Keel + Email or phone mapping.
6. **Import API test:** JSON paste saves workbook; CSV paste saves workbook.
7. **Excel upload gate:** uploading known-bad xlsx to Automate/Execute job create fails with gate detail (or equivalent API test).
8. **Manual smoke (when portal + project available):** paste Cursor-style output → Import → Execute with “use generated”.

---

## File map (expected)

| Area | Files (indicative) |
|------|---------------------|
| Service | New `TcImportService` (or `GeneratedTcImportService`); wire `TcGenerateService` / compare save / workbook |
| Repair | Extend `GeneratedTcCsvRepair` / small `TcImportRepair` |
| API | `GenerateTcController` or dedicated import controller |
| UI | `generate.html`, maybe short hints on `execute.html` / upload |
| Prompts | JSON + CSV under `static/prompts/` |
| Tests | Import service tests, API test, MVC, prompt tests, KeelPath filter regression |

---

## Open follow-ups (out of v1 unless trivial)

- One-click “Send imported workbook to Execute” without leaving Generate.
- Diff UI when re-import overwrites `latest.xlsx`.

## Success criteria

- A user can take external-AI JSON **or** CSV, paste into Keel, and get a saved workbook usable on Automate/Execute **without** manual Excel surgery.
- Portal Generate continues to work and shares the same gate.
- Blank KeelPath sheets still run on both surfaces.
- Bad authoring still blocked with clear errors.
- Automated tests above are green; manual smoke documented if run.

---

## Spec self-review

- No TBD placeholders left for v1 scope.
- Decisions match brainstorming (Approach 2, blank path A, formats C, gate C).
- Scope is one feature: unified import + prompt/UI fix — not heal redesign.
