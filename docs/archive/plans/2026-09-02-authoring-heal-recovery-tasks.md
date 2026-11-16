# Authoring + Heal Recovery — Tasks

**Spec:** `docs/superpowers/specs/2026-09-02-authoring-heal-recovery-design.md`  
**Plan:** `docs/superpowers/plans/2026-09-02-authoring-heal-recovery.md`

## Phase A — Correctness (start now)

- [x] A1 Coverage notes returned from Paste/Import
- [x] A2 Gate rejects Leave-empty + non-blank TestData (incl. `<placeholders>`)
- [x] A3 Gate rejects vague “validation or error / clear validation” asserts
- [x] A4 Never type literal `<ANGLE_TOKEN>` values
- [x] A5 Leave-empty binds to clear (ignore TestData)

## Phase B — Editable TC preview

- [x] B1 Click TC row → structured editable panel/modal
- [x] B2 Save edits to workbook API + lastResult
- [x] B3 Coverage notes visible (edit optional / deferred)

## Phase C — Heal recovery era

- [x] C1 Structured recovery JSON schema + parser
- [x] C2 HealCascade: run recovery → re-attempt intent
- [x] C3 Evidence: automationNotes + successful recovery steps (`heal-recovery.json`)

## Phase V — Verify

- [x] V1 Unit/API/MVC green; document in notes file
- [ ] V2 Manual: import bad TC_02 fails; fixed TC_02 executes empty email correctly (user after portal restart)
