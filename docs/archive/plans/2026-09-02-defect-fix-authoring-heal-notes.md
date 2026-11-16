# Defect Fix Pass — Notes

**Spec/plan/tasks:** `docs/superpowers/*/*2026-09-02-defect-fix-authoring-heal*`

## Closable follow-ups (closed)

| Item | Status |
|------|--------|
| Invent `fullHtml` presence | Wired via `HealCascade.setPresenceHtml` / invent parse |
| Per-row Expected in TC grid | Generate modal Step × TestData × Expected |
| Coverage notes persist | `PUT .../generated-workbook/coverage-notes` + Import/Generate write meta |
| automationNotes trail | `automation-notes.txt` evidence + ZIP `docs/HEAL_AUTOMATION_NOTES.md` + **workbook patch** (`HealWorkbookPatcher` / `applyHealRecoveryPatch`) |
| Second recovery | Up to **2** attempts per intent |
| Focus trap | Tab cycles inside modal; Escape closes |
| Bounded “wrong page” recovery | `navigate` only to Excel open-path; max **5** recovery steps |
| Log in autofill | `looksLikeSubmit` includes log in / sign in; leave-empty still skipped |

## Still out of scope (honest)

- Full free-form multi-obstacle agent (captcha, 2FA, arbitrary wizards)
- Parallel invent ThreadLocal isolation (prove remains single-threaded per job)

## Shipped after defect pass

- Deterministic heal recovery → generated workbook leave-empty / blank TestData patch (not free-form note→Steps rewrite); unmatched notes append to coverage + `automationNotesByTc`

## Manual smoke

1. Restart portal + hard-refresh Generate.
2. Click TC → grid shows Step / Test data / Expected → Save.
3. Edit coverage notes → **Save coverage notes**.
4. Empty-email + Click Log in → email must stay empty.
5. Recovery: look for `heal-recovery.json`, `automation-notes.txt`, Generate coverage **Heal notes**, updated leave-empty cells in `generated/latest.xlsx`, and after Automate `docs/HEAL_AUTOMATION_NOTES.md`.

## Maven focus

```text
mvn -q "-Dtest=RecoveryPlanParserTest,HealCascadeTest,FreeInventHealerTest,RequiredControlFiller*Test,StepIntentBinderTest,GenerateAuthoringRulesTest,ProvePhase*Test,GeneratedWorkbookCoverageNotesTest,HealWorkbookPatcherTest,GeneratedWorkbookHealPatchTest" test
```
