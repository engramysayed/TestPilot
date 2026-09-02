# Generate authoring repair + smarter second pass

**Date:** 2026-08-31  
**Status:** Approved — 2026-08-31  
**Depends on:** `TcGenerateService.generateStory`, `GenerateQualityGate`, `GenerateAuthoringRules`, Generate page review checkbox  

---

## 1. Goal

Gemma (and any generate model) must **not fail a generate job** for known, safely repairable authoring mistakes — especially empty-email cases with a typed TestData line (the `TC_02` / `QUALITY_GATE` failure on paste generate).

The optional **Second pass** checkbox must review **mistakes and coverage**, not coverage-only.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| When Gemma still fails the gate | **Java auto-repair** known-safe mistakes, then save |
| Auto-repair | **Always on** (paste generate, bulk, All in one). Not behind the checkbox |
| Second-pass checkbox | Stays **optional**. Extra Ollama call now: **fix mistakes + add missing coverage** |
| Unrepairable leftovers | Existing one-shot **quality-retry** Ollama call, then auto-repair again, then `QUALITY_GATE` fail |
| Invented expected text | **Never.** Vague asserts without a quoted UI message still fail |
| New API / job status | **None.** Repair is silent in the UI (info log only) |
| Models | Same path for `gemma4:e2b` and `qwen2.5:latest` |

---

## 3. Scope

### In

- New `GenerateAuthoringRepair` that rewrites `ManualTestCase` rows using the same field/step matching as `GenerateAuthoringRules`
- Wire repair into `TcGenerateService.generateStory` after parse + gate
- Expand `buildReviewUserMessage` so the optional second Ollama call fixes authoring mistakes **and** adds missing cases
- Generate page checkbox copy
- Unit tests for repair, generateStory-with-stub (no extra LLM when repair suffices), review-prompt wording

### Out

- Weakening or removing the quality gate
- Saving rows that still fail the gate after repair + retry
- Auto-inventing UI error copy for vague asserts
- Auto-fixing blank steps or prose-like `tcId`s
- Changing cancel, timeouts, or JSON parse (already separate)

---

## 4. Generate flow

```
Ollama draft
    → if reviewPass: Ollama again (fix mistakes + add missing cases)
    → parse + scope filter
    → quality gate
    → if errors: Java auto-repair
    → re-validate
    → if errors: existing quality-retry Ollama call
    → parse + scope filter
    → if errors: Java auto-repair
    → re-validate
    → if errors remain: throw QUALITY_GATE (unrepairable only)
    → else: save workbook
```

Paste generate is one story (`US_ASYNC_001`). If that story would have thrown only repairable errors, the batch **completes** with cases instead of `No test cases were generated… QUALITY_GATE`.

---

## 5. Auto-repair catalog

Repair is **per case**, **per numbered step**. Only the matching TestData line is blanked; other steps keep their data.

**Apply in this order** on each case: (1) combined-login field label rewrite, (2) empty-field “Leave the … empty” step rewrite, (3) blank matching TestData lines, (4) literal `\n` → real newlines.

| Condition (same detectors as `GenerateAuthoringRules`) | Repair |
|---|---|
| Combined login identifier (`email or phone` etc. in title, expected, or steps) and step says standalone `Email field` or `Phone field` | Rewrite to `Email or phone field` |
| Title/expected mentions empty email, phone, or password and that field’s enter/leave step is `Enter in the … field` (not already “leave” / “empty value”) | Rewrite that step to `Leave the {label} field empty`, where `{label}` is `Email or phone` if the step already says that (after step 1), else `Email` / `Phone` / `Password` |
| Title/expected mentions empty email, phone, or password **and** that field’s enter/leave step has a non-placeholder TestData value | Set that step’s TestData line to `""` |
| Steps contain literal `\n` and no real newline | Replace `\n` with real newlines (same as gate’s `hasLiteralBackslashNWithoutRealNewlines`) |

Placeholders like `<email>` are **not** stripped (gate already allows them).

### Still fail (do not invent)

- Blank steps after normalize
- `tcId` not matching `TC_<digits>` / `TC_<ALNUM_UNDERSCORE>`, or looking like step prose
- Vague assert (“clear validation/error state”) with no exact UI message to copy
- Unparseable / unmapped KeelPath
- Any gate error not in the table above

---

## 6. Second pass prompt

`buildReviewUserMessage` format hint becomes (JSON path; CSV fallback equivalent):

1. Fix authoring mistakes in **existing** cases (empty-field TestData must be blank on that step; use `Leave the … field empty`; combined login uses `Email or phone field`; quote exact UI errors).
2. Add **only missing** cases vs the original stories.
3. Reply with the **full** merged JSON (`testCases` + `coverageNotes`).

The checkbox stays. Unchecked generate is: one Ollama draft + Java repair (plus quality-retry Ollama only if unrepairable errors remain).

### UX copy (`generate.html`)

| Place | Copy |
|-------|------|
| Paste checkbox | `Second pass — fix mistakes and add missing coverage (slower)` |
| Paste hint | `Runs Ollama twice: first generates TCs, then asks the model to fix authoring mistakes and add only gaps vs your stories. Roughly doubles wait time. Keel also auto-repairs known empty-field TestData mistakes even when this is off.` |
| Bulk checkbox | `Second pass per story — fix mistakes and coverage (slower)` |

---

## 7. Architecture

| Unit | Responsibility |
|------|----------------|
| `delivery.excel.GenerateAuthoringRepair` | Pure functions: `repair(List<ManualTestCase>)` → new list; reuse `GenerateAuthoringRules` matchers (empty field, combined identifier, enter-step-for-field) |
| `GenerateAuthoringRules` | Unchanged validators; package-visible helpers stay the source of “what is an empty-email step” |
| `TcGenerateService.generateStory` | Flow in §4; call repair after each gate fail before retry / throw |
| `TcGenerateService.buildReviewUserMessage` | Mistake + coverage instructions |
| `generate.html` | Checkbox + hint copy only |

Repair returns **new** `ManualTestCase` records (existing type is a record). No mutation of input lists.

Log at info when repair changed at least one case: `Repaired N generate authoring issue(s) for {storyLabel}`.

---

## 8. Error handling

- Repair must not throw on empty/null case lists (return input / empty).
- If repair does not shrink the gate error list, skip a no-op loop; proceed to quality-retry or throw.
- Quality-retry still uses `buildQualityRetryUserMessage` (unchanged intent).
- `JobCancelledException` during any Ollama wait still cancels the job (existing cancel path).

---

## 9. Testing

Primary empty-email fixture: well-formed steps (`Leave the Email or phone field empty`, quoted UI error) **except** TestData line 2 is `user@example.com`. After repair, line 2 is blank and `GenerateQualityGate.validate` returns empty.

- `GenerateAuthoringRepairTest`: that fixture is repaired; gate passes.
- `GenerateAuthoringRepairTest`: well-formed golden empty-email case (already blank TestData) is unchanged (idempotent).
- `GenerateAuthoringRepairTest`: empty-email on step 2 + password value on a **later** TestData line keeps the password value.
- `GenerateAuthoringRepairTest`: combined-login `Enter in the Phone field` rewrites to `Email or phone field`.
- `TcGenerateService` stub test: first LLM JSON has **only** the TestData-blank-line defect; `llmCallCount == 1`; `generateStory` returns cases (quality-retry is not called).
- Review prompt test: `buildReviewUserMessage` contains fix-mistakes language **and** add-missing-coverage language.
- Existing `GenerateAuthoringRulesTest` / `FacebookLoginNegativeGenerateTest` stay green (gate still rejects unrepaired bad rows).

---

## 10. Non-goals / YAGNI

- Do not surface a “repaired” badge on Job status in v1.
- Do not add a third Ollama model for review.
- Do not auto-complete expected results from the story text.
- Do not change `delivery.generate-timeout-seconds`.
