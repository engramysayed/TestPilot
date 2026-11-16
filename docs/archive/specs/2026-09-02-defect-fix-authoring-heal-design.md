# Design: Defect Fix Pass — Authoring / Preview / Heal Recovery

**Date:** 2026-09-02  
**Status:** Draft for approval (blocks Execute correctness + Automate fidelity)  
**Parent:** `docs/superpowers/specs/2026-09-02-authoring-heal-recovery-design.md`  
**Source:** Defect brainstorm after A/B/C ship (recovery era)

## Problem

Phases A–C shipped, but several logic holes still block empty-field / recovery / Automate:

- Recovery can be mishandled on the Cursor escalate path.
- Submit auto-fill can re-type into fields the TC left empty.
- Successful recovery is not proven for Automate.
- Preview edit UX is form textareas, not a step grid.
- Several gate / binder / clear / budget edge cases remain.

## Goals

1. **Never treat recovery steps as intent success** — always execute recovery → re-attempt original intent.
2. **Never auto-fill fields the TC intentionally left empty.**
3. **Successful recovery steps + notes become Automate-usable evidence** (proven trail + structured JSON).
4. **Hardening** so leave-empty, vague asserts, and recovery validation cannot silently degrade.
5. **Preview step grid** so users edit Steps ↔ TestData row-by-row before Execute/Automate.

## Non-goals (this pass)

- Full free-form agent that “solves any obstacle” beyond validated recovery (clear/type/click/select ≤3).
- `resumeFromIntentIndex` as a jump controller (v1 keeps **retry current intent only**; remove from prompts if present).
- Hermes / second agent product.
- Parallel multi-TC invent ThreadLocal redesign (document risk; fix only if prove goes parallel).

## Approach (chosen)

**In-place severity waves** on existing `ProvePhase` / `RequiredControlFiller` / `RecoveryPlanParser` / Generate UI — not a heal rewrite.

| Wave | Theme | Outcome |
|------|--------|---------|
| **P0** | Execute blockers | Recovery escalate wired; leave-empty respected by auto-fill; leave-empty on TYPE_USER/PASS |
| **P1** | Contract + Automate fidelity | All-or-nothing recovery; proven recovery steps; notes; clear harden; gate broaden; invent budget after parse |
| **P2** | UX + polish | Step-grid editor; coverage-notes edit; Escape modal; phrasing + slim-HTML notes |

**Rejected alternatives:** (B) rewrite heal cascade — too large; (C) defer Automate proven trail — leaves false confidence.

---

## P0 — Execute blockers

### D1 — Cursor escalate must use `tryRecoveryHeal`

**File:** `ProvePhase` (~ollamaUsed && !cursorUsed branch)  
**Rule:** Any `HealResult` with `tierUsed == "recovery"` goes through `tryRecoveryHeal` only. Never `execution.execute` + `settledSuccess` on recovery steps as if they satisfied the original intent.

### D2 — Auto-fill must skip leave-empty fields

**File:** `RequiredControlFiller`  
**Rule:** When planning fills before click, skip controls that match a TYPE_* intent whose text is leave/keep-empty **or** whose TestData line is blank **and** step is leave/keep-empty. Prefer matching by field tokens (email/phone/password/label). Auth skip list must include **email** / **email or phone** when paired with leave-empty intents (not only username/password).

### D3 — Leave-empty clear on TYPE_USER / TYPE_PASS

**File:** `StepIntentBinder` preferred + needle bind paths  
**Rule:** Same as TYPE_FIELD: `isLeaveOrKeepEmptyStep` → action `clear`, ignore TestData / invent.

---

## P1 — Contract + Automate fidelity

### D4 — All-or-nothing recovery parse

**File:** `RecoveryPlanParser`  
**Rule:** If any recovery step fails validation, reject the whole plan (do not run a subset). Cap still ≤3.

### D5 — Proven recovery trail + evidence

**Files:** `ProvePhase.tryRecoveryHeal`, proven accumulation  
**Rule:** On `RECOVERY_RETRY`, append successful recovery `ProvenStep`s into the TC’s proven list (or a dedicated recovered-prefix list merged like `recoveredNav`). Keep writing `heal-recovery.json` with steps + `automationNotes` + `thought`.

### D6 — Invent budget after successful parse

**File:** `HealCascade` (`tryInvent` / `acceptWrittenLocator`)  
**Rule:** Claim invent budget only when parse yields a usable `HealResult` (classic or recovery). Failed JSON must not burn the slot.

### D7 — Stronger clear

**File:** `TcExecutionService` (and context retry)  
**Rule:** `clear` = Selenium `clear()` plus select-all + backspace/delete fallback when value still non-blank (site-agnostic).

### D8 — Broader vague-assert gate

**File:** `GenerateAuthoringRules`  
**Rule:** Also reject common soft asserts: “verify an error appears”, “show an error/message”, “user is notified”, “validation message is shown” without a quoted string. Quoted exact UI text remains allowed.

### D9 — Recovery vs SUBMIT_REFUSE

**File:** `ProvePhase`  
**Rule:** Do not apply `refusesSubmitNavigation` to recovery-tier results; recovery clicks are state repair, not intent bind.

### D10 — Gate on Excel paths that feed Execute/Automate

**Files:** workbook upload / Automate+Execute entry that reads Excel  
**Rule:** Run `GenerateQualityGate` (or authoring subset) when loading generated/uploaded workbook for run — fail loud with gate errors (same messages as Import).

### D11 — Prompt / schema cleanup

**Files:** `heal.mjs`, invent prompts  
**Rule:** Drop `resumeFromIntentIndex` from examples; document retry-current-intent only. Keep `mode` / `recoverySteps` / `automationNotes`.

---

## P2 — UX + polish

### D12 — Step-grid TC editor

**File:** `generate.html` (+ CSS)  
**Rule:** Modal shows a table: Step # | Steps | TestData (aligned rows), plus other columns (title, expected, preconditions, priority, tags, visual, keelPath). Save still PUT same API. Textarea fallback only if parse of numbered steps fails.

### D13 — Editable coverage notes

**Files:** Generate UI + workbook meta/API as needed  
**Rule:** Coverage notes field editable; persist with workbook meta or sidecar already used for notes.

### D14 — Modal Escape + a11y

**Rule:** Escape closes; focus return to row optional.

### D15 — Extra leave-empty phrasing (gate + binder)

**Rule:** Treat “do not fill …”, “skip the … field”, “leave … blank” like leave-empty for gate blank-TestData + clear bind.

### D16 — Slim-HTML false reject mitigation

**Rule:** If slim presence fails but full-page (or candidate table) presence passes, accept locator; log `HEAL_RECOVERY_SLIM_MISS`. Keep invent budget rules.

### D17 — Document intentional limits

**Notes file:** Recovery allowlist stays clear/type/click/select; no assert/navigate in recovery v1; “solve any obstacle” remains future era; ThreadLocal invent risk if parallel prove lands.

---

## Testing strategy

| Wave | Must-have tests |
|------|-----------------|
| P0 | ProvePhase/HealCascade: escalate recovery → retry intent, not success; RequiredControlFiller skips leave-empty email; TYPE_USER leave-empty → clear |
| P1 | RecoveryPlanParser rejects partial; proven list includes recovery; invent budget not burned on bad JSON; clear fallback; vague assert; gate on upload |
| P2 | MVC/JS smoke or documented manual checklist for grid + Escape + coverage edit |

## Success criteria

- Empty-email Facebook-style TC: leave empty stays empty through Submit (no invented email).
- Cursor recovery on escalate path retries assert after clear(+click); never marks assert passed from recovery alone.
- `heal-recovery.json` + proven steps both contain successful recovery actions.
- Bad ChatGPT leave-empty+`<placeholder>` still fails Import **and** fails Execute workbook load if sneaked in via Excel.
- Click TC → edit step/TestData cells in a grid → Save → workbook updated.

## Out of scope follow-ups (explicit)

- Multi-step recovery planner / captcha / 2FA.
- Auto-apply `automationNotes` into Excel rewrite (evidence + proven is enough this pass; optional later).
- Parallel invent ThreadLocal isolation.
