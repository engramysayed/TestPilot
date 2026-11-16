# Add library TC + pre-run authoring review (Cursor or Keel)

**Date:** 2026-10-18  
**Status:** Implemented  
**Plan:** `docs/superpowers/plans/2026-10-18-library-add-and-prerun-review.md`  
**Surfaces:** project Test cases tab; Execute; Automate

## Problem

1. The project library can be edited and deleted, but there is no **Add test case**. People must Generate, Record, or upload Excel to get a new `TC_` id.
2. Execute **Review with Cursor before run (propose only)** already sends the suite to Cursor, but:
   - The UI is Cursor-only (Keel/Ollama is not offered).
   - The prompt does not teach Keel’s execute rules (quotes → visible-text wait, `and` splits, masked OTP).
   - **Accept & run** uses the proposal **for that job only** and does **not** write the library, so the next run hits the same Expected wording.

Live example: `OTP field is filled with '245345'` became “Confirm the text 245345 is visible” on a masked field; `"Upload Transaction List"` failed because the screen says **Upload Transactions List**.

## Goal

- **Add test case** on the project Test cases tab opens the existing editor; next `TC_` id; Save writes the library.
- Execute/Automate **Review cases before run** offers **Cursor** or **Keel**. The run’s cases go out as JSON plus a prompt of Keel authoring rules. The model returns edited cases.
- **Accept & run** writes those edits into the **library** (history version) **and** starts the job. Nothing runs until Accept.

## Non-goals

- Changing `OutcomeKind` quote-wins-first (runtime still treats quotes as visible text).
- Excel rewrite of a file on disk outside the library workbook.
- CSS reskin, Account vision radios, Bug Hunter, Midscene.
- Auto-accept. Unchecking review still runs the library as-is.
- Editing `api-keys.local.bat`. New credit product. Claude/Anthropic.

## Add test case

On project **Test cases**:

- Button **Add test case**.
- Next id: max existing `TC_n` + 1, formatted like current ids (`TC_1` / `TC_01` stay consistent with that library’s padding if any; default `TC_1` when empty).
- Opens the **same** edit panel (title, step / test data / expected rows, Add step, priority, tags, visual assertion, KeelPath **blank**).
- Save uses the same quality gate as edit. History row: Edited, signed-in email.
- Empty library (no generated workbook): Save creates the workbook with this first case (`available` becomes true). Same permission as other library writes (`ProjectAccess` operable).
- Credits: none (same as edit).

API: `POST /api/projects/{projectId}/generated-workbook/cases` with the same field map as `PUT .../cases/{tcId}`. Server assigns `tcId`. Reject duplicate ids.

## Pre-run review

Checkbox label: **Review cases before run**. Radio: **Cursor** | **Keel** (default last choice in `sessionStorage`; first visit **Keel** so a machine without Cursor key still works).

Suite = cases about to run (selected `TC_ID`s, or all library cases if none selected; upload mix still upload-wins per existing resolver).

Request body (JSON; multipart can remain for upload files):

- `provider`: `cursor` | `keel` (`keel` maps to existing `ollama` port)
- `model`: empty for Cursor; for Keel, Admin chat model / `delivery.generate-model` (same as Generate default)
- cases as JSON array: `tcId`, `title`, `steps`, `testData`, `expectedResult`, `keelPath`, `preconditions`, `priority`, `tags` (no secrets; login values stay tokens)

**Prompt (both providers)** — extend `AuthoringReviewService.SYSTEM_PROMPT`:

- Return only JSON `{ findings[], cases[] }` covering every input `tcId` once.
- Rewrite Steps, TestData, Expected so Keel Execute will bind them.
- Quoted text is a **visible-text** wait; the string must match the screen (`Upload Transaction List` ≠ `Upload Transactions List`).
- `and` can split extra expected checks; “expands and sub-menu items are shown” stays one check.
- After a fill, prefer `{Field} is accepted` / `OTP is accepted`. Do **not** assert typed digits/password on a masked field (`filled with '245345'` is wrong).
- `{Field} is shown` / `{Button} is clicked` / page shown — keep those shapes.
- Do not invent large extra suites. Preserve `tcId` and KeelPath.
- Flag leftover Expected that has no matching step.

**Cursor:** `CURSOR_API_KEY` required; missing → error, job does not start.  
**Keel:** Ollama chat model at Admin URL; unreachable → error, job does not start.

UI: findings + proposed cases (existing cards). Raw CSV can stay in a details block; JSON is the source of truth.

**Accept & run**

1. Quality gate on proposed cases (existing).
2. Write **full library**: replace matching `tcId`s; do not delete cases not in this run. History version.
3. Start Execute or Automate with the reviewed set (same as today’s accept, but library is already updated).
4. Cancel: discard proposal; library unchanged; job not started.

If review HTTP/LLM fails: do not start. User unchecks review to run without it.

Credits: Execute/Automate **this run** bar unchanged; charged when the job starts, not when review starts.

Automate uses the same checkbox, radios, prompt, and Accept-writes-library behavior.

Generate’s existing authoring-review panel can keep its provider dropdown; share the **same** system prompt so Generate Accept and pre-run review teach the same rules. Generate Accept already writes the library — do not change that save rule.

## Errors

| Case | Behaviour |
|------|-----------|
| No Cursor key, provider Cursor | Clear error; no run |
| Ollama down, provider Keel | Clear error; no run |
| Empty suite | Existing “no cases” |
| Quality gate on Accept | Show gate errors; do not save or run |
| No workbook and Add first case | Create workbook |
| Cannot add last-delete rules | Unchanged (cannot delete the last remaining case) |

## Tests (intent)

- POST add case → next id, listed, history.
- POST add on empty project → workbook created, `TC_1`.
- Pre-run review `provider=keel` calls Ollama port; `cursor` calls Cursor port; missing Cursor key fails.
- Prompt/system contains OTP-accepted and quote-must-match-screen rules (string assert on prompt).
- Accept persists edited expected into library then job payload uses it (library read-back).
- Unchecked review: Start execute does not write library.

## Browser check (after jar rebuild)

- Add test case on a project with a library; Save; case appears.
- Execute: pick Keel, review, read findings, Accept & run; library expected changed; run starts.
- Execute: Cursor with no key → error, no run.
