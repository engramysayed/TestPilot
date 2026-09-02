# Design: Heal Recovery → Generated Workbook Patch

**Date:** 2026-09-02  
**Status:** Implemented  
**Parent:** `docs/superpowers/specs/2026-09-02-authoring-heal-recovery-design.md`  
**Plan:** `docs/superpowers/plans/2026-09-02-heal-workbook-patch.md`

## Problem

Successful heal **recovery** on Execute writes evidence (`heal-recovery.json`, `automation-notes.txt`) and may merge notes into Automate ZIP docs, but the project’s **saved generated workbook** (`generated/latest.xlsx`) still carries the pre-recovery Steps / TestData. Re-Automate or re-Execute from Generate can therefore encode the wrong leave-empty path.

`GeneratedWorkbookService.mergeAutomationNotes` exists but is unused and meta-only — it never mutates Excel cells.

## Goals

1. After a **successful recovery**, automatically apply **deterministic** Excel patches so leave-empty / clear behavior is reflected in Steps + TestData.
2. Keep unmatched free-text `automationNotes` visible without rewriting Steps from prose (append to coverage notes + meta).
3. Never fail the TC / ProvePhase because workbook patch failed (best-effort).
4. Preserve quality gate: bad patches must not land in `latest.xlsx`.

## Non-goals

- Captcha / 2FA / general multi-obstacle agent.
- LLM free-form rewrite of Steps from notes.
- Changing ExpectedResult / Title / KeelPath from notes.
- Inserting brand-new numbered Excel steps from recovery.
- Rewriting Excel for recovery `navigate` (execute proven-prefix is enough; open URL stays authored).
- Invent ThreadLocal / parallel prove hardening.

## Chosen approach

**Option C + deterministic patcher (Approach 1):**

- Auto-apply structured step/TestData patches immediately after successful recovery.
- Unmatched notes → `automationNotesByTc` meta + append a dated Heal block under `coverageNotes` (no new confirm modal).
- Pure helper + workbook service apply + ProvePhase best-effort call.

## Architecture

### `HealWorkbookPatcher` (pure)

**Input:** `ManualTestCase`, proven recovery `List<ProvenStep>`, `List<String> automationNotes`  
**Output:** patched case (or unchanged), applied change summaries, unmatched notes

**Matching (deterministic):**

| Recovery signal | Excel effect |
|-----------------|--------------|
| `clear` (or blank-value `type`) on a field | Best-match step via existing leave-empty / enter-in-field / field-label heuristics; rewrite to Leave-/keep-empty wording if needed; **blank that TestData line** |
| Other recovery actions (`click`, `select`, valued `type`, `navigate`) | No Excel cell rewrite in v1 |
| Note text alone | Never invents Steps; if no structured match consumed the note, it stays **unmatched** |

Reuse patterns from `GenerateAuthoringRules` / `GenerateAuthoringRepair` where possible (do not duplicate leave-empty wording logic ad hoc).

### `GeneratedWorkbookService.applyHealRecoveryPatch(...)`

1. If no `latest.xlsx` or unknown `tcId` → merge notes only (if any); return skipped.
2. Load cases → run patcher on matching TC.
3. If no cell changes → still `mergeAutomationNotes` + append unmatched to coverage; return.
4. `TcImportRepair` + `GenerateQualityGate.validate` with job `baseUrl`.
5. On gate OK → `saveFromCases` (preserves prior meta fields via existing save path) + `mergeAutomationNotes` + append unmatched coverage block (dedupe).
6. On gate fail → **do not save Excel**; log `HEAL_WORKBOOK_PATCH_SKIPPED`; still merge notes + optional coverage append for unmatched; return skipped with reasons.

Target is always `storeRoot/{projectId}/generated/latest.xlsx`, **not** the job-scoped temp Excel copy.

### ProvePhase wiring

On successful `tryRecoveryHeal` (after evidence write):

- Best-effort call apply with `projectId`, `tcId`, proven recovery steps, `automationNotes`, `baseUrl`.
- Catch/log all exceptions; never change recovery outcome.
- Inject `GeneratedWorkbookService` (or a thin port) through the execute/conversion path that constructs ProvePhase.

### UI

No new modal. Generate coverage notes panel already shows persisted notes; after Execute refresh, heal appends appear. Existing evidence files unchanged.

## Data flow

```
recovery PASS
  → writeHealRecoveryEvidence
  → applyHealRecoveryPatch(project generated workbook)
       → HealWorkbookPatcher
       → gate? save excel/csv : skip excel
       → mergeAutomationNotes + coverage append (unmatched)
  → retry original intent
```

Multiple recoveries in one TC: each successful recovery may call apply; later successful patches overwrite earlier cell state for that TC.

## Error handling

| Case | Behavior |
|------|----------|
| No generated workbook | No Excel; notes merge no-op or skip |
| Unknown tcId | Skip Excel; notes only if meta exists |
| Gate fail | Excel unchanged; log skip reason |
| Patch exception | Log; ProvePhase continues |
| Empty notes + no cell change | No-op |

## Testing

1. **HealWorkbookPatcherTest** — clear → leave-empty + blank TestData; already leave-empty blanks TestData; no match → unmatched notes; does not add steps; valued type/click/navigate do not rewrite.
2. **GeneratedWorkbookService** apply tests — saves on good patch; skips Excel on gate fail; appends unmatched coverage; preserves sibling TCs; preserves `automationNotesByTc` merge/dedupe.
3. **ProvePhase** (or collaborator) — successful recovery invokes apply once; apply failure does not flip recovery outcome.

## Success criteria

- After recovery that clears a field the TC meant to leave empty, `latest.xlsx` for that TC has Leave-/keep-empty step wording and blank TestData for that line (when gate allows).
- Unmatched notes appear under coverage notes and/or `automationNotesByTc` without corrupting Steps.
- Automate ZIP still gets `docs/HEAL_AUTOMATION_NOTES.md` as today; Excel is no longer the only stale surface.
