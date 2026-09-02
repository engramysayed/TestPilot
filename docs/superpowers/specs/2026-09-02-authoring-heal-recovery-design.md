# Design: Authoring Gate Hardening + Editable TC Preview + Heal Recovery Era

**Date:** 2026-09-02  
**Status:** Approved to implement (user: go ahead)  
**Context:** ChatGPT-imported Facebook negative TCs — TC_02 typed `<VALID_PASSWORD>` into email; coverage notes missing; Cursor saw filled fields but could not propose clear→submit recovery.

## Goals

1. **Stop bad imports / executes** that mis-fill empty-field cases and type angle-bracket tokens.
2. **Show coverage notes** after Paste/Import.
3. **Editable TC detail** — click a preview row → structured table of all columns; edit before Automate/Execute.
4. **Heal recovery invent** — when assert fails because the page state is wrong (e.g. field not empty), Cursor/Ollama may return a **structured JSON recovery plan** (clear → submit → resume), execute it, re-run the intent, and **document automation-safe steps**.

## Non-goals (v1 of heal recovery)

- Full free-form agent rewriting entire TCs mid-run.
- Hermes as a second product.
- Cloud-only invent without validation against live DOM.

---

## Phase A — Gate + Import correctness (ship first)

| ID | Change |
|----|--------|
| A1 | `TcImportService` returns `coverageNotes` from JSON parse |
| A2 | Gate: Leave-empty steps must have **blank** TestData (reject `<placeholders>` too) |
| A3 | Gate: broaden vague assert to `validation or error`, `clear validation` |
| A4 | `DummyValueInventor`: never type literal `<ANGLE_TOKENS>` — treat as unspecified → invent or empty |
| A5 | Leave-empty intents: bind as **clear** (or type empty), never type column value |

## Phase B — Editable TC preview (Generate UI)

| ID | Change |
|----|--------|
| B1 | Click TC row → modal/panel with editable fields: title, steps, expected, testData, preconditions, priority, tags, visualAssertion, keelPath |
| B2 | Save edits → update `lastResult` + `PUT` generated workbook rows (existing keel-path API extended or new PATCH) |
| B3 | Show coverage notes (from A1) editable optional |

## Phase C — Heal recovery invent (“another era”)

### Contract (structured JSON)

Cursor/invent returns **either** classic shortlist pick **or**:

```json
{
  "mode": "recovery",
  "thought": "Email field is filled; assert expects empty-field validation.",
  "resumeFromIntentIndex": 4,
  "recoverySteps": [
    { "action": "clear", "targetHint": "email or phone", "strategy": "css", "value": "input[name='email']" },
    { "action": "click", "targetHint": "Log in", "strategy": "css", "value": "button[name='login']" }
  ],
  "automationNotes": [
    "After recovery, Excel step 2 should remain Leave empty; do not type TestData on that line."
  ]
}
```

### Runtime

1. Assert / bind fails → existing shortlist heal.
2. If shortlist fails **and** failure looks like state mismatch (filled when empty expected, wrong value visible) → **recovery invent** call.
3. Validate each recovery step against live DOM (locator must exist; action in allowlist: clear, type, click, select).
4. Execute recovery steps → re-attempt original intent.
5. Persist `automationNotes` + executed recovery steps into execute evidence / optional revise notes for Automate (IR-friendly ProvenStep list).

### Safety

- Max 3 recovery steps, one recovery attempt per intent.
- No invented CSS unless present in slim HTML / candidate table (prefer candidateId when possible).
- Document only steps that **succeeded** live.

## Success criteria

- Import of the user’s broken TC_02 JSON **fails gate** with clear Leave-empty / TestData error.
- Fixed TC_02 does not type into email; Leave empty clears.
- Coverage notes visible after import.
- Click TC_02 → edit TestData → save → Execute uses corrected data.
- On assert fail with filled email, recovery invent can clear + click login and evidence records structured JSON + notes.

## Implementation order

A → B → C (do not start C until A tests green).
