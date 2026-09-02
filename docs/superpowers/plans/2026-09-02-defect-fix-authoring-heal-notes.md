# Defect Fix Pass — Notes

**Spec/plan/tasks:** `docs/superpowers/*/*2026-09-02-defect-fix-authoring-heal*`

## Shipped (this pass)

| Wave | Status |
|------|--------|
| P0 T1–T3 | Done — escalate recovery routing, leave-empty auto-fill skip, TYPE_USER/PASS clear |
| P1 T4–T8, T10 | Done — all-or-nothing recovery, proven trail, invent budget after parse, hardened clear, broader gate/phrasing, prompt cleanup |
| P1 T9 | Already present — Execute/Automate Excel load already runs `GenerateQualityGate` |
| P2 T11–T13 | Done — step×TestData grid editor, Escape, coverage notes session edit, slim→full HTML fallback |
| P2 T14–T15 | Notes + Maven focus below |

## Intentional limits (D17)

- Recovery allowlist: `clear` | `click` | `type` | `select` only (≤3 steps, one attempt per intent).
- No intent-index jump; always retry the **current** failed intent after recovery.
- `automationNotes` → evidence JSON + proven recovery steps; auto-rewrite Excel rows is a later optional.
- Coverage notes edit is **session-scoped** on Generate (not a separate workbook meta API yet).
- Full “solve any obstacle” agent is out of scope.
- Invent `ThreadLocal` isolation only if prove becomes parallel.
- Second recovery attempt per intent still fails closed (v1).

## Manual smoke (after portal restart)

1. Hard-refresh Generate.
2. Import broken leave-empty + `<VALID_PASSWORD>` → gate fail.
3. Import/fix empty-email TC → Submit must **not** invent email on Submit-like clicks.
4. Click TC → step grid edit TestData → Save → workbook updated; Escape closes modal.
5. Force assert fail with filled email → expect `HEAL_RECOVERY` / `heal-recovery.json` and retry assert; proven trail includes recovery clears/clicks.
6. Cursor escalate path must not mark assert passed from recovery alone.

## Maven focus

```text
mvn -q "-Dtest=RecoveryPlanParserTest,HealCascadeTest,FreeInventHealerTest,RequiredControlFiller*Test,StepIntentBinderTest,GenerateAuthoringRulesTest,ProvePhase*Test" test
```
