# Defect Fix Pass — Tasks

**Spec:** `docs/superpowers/specs/2026-09-02-defect-fix-authoring-heal-design.md`  
**Plan:** `docs/superpowers/plans/2026-09-02-defect-fix-authoring-heal.md`

## P0 — Execute blockers

- [x] T1 D1/D9 Cursor escalate uses `tryRecoveryHeal`; no SUBMIT_REFUSE on recovery tier
- [x] T2 D2 Auto-fill skips leave-empty fields (esp. email)
- [x] T3 D3 Leave-empty → clear on TYPE_USER / TYPE_PASS

## P1 — Contract + Automate fidelity

- [x] T4 D4 All-or-nothing recovery parse
- [x] T5 D5 Proven recovery trail + keep `heal-recovery.json`
- [x] T6 D6 Invent budget only after successful parse
- [x] T7 D7 Hardened clear (select-all + backspace fallback)
- [x] T8 D8/D15 Broader vague-assert + leave phrasing
- [x] T9 D10 Quality gate on Excel load for Execute/Automate (already wired)
- [x] T10 D11 Prompt cleanup (drop resumeFromIntentIndex / document retry-same-intent)

## P2 — UX + polish

- [x] T11 D12 Step-grid TC editor
- [x] T12 D13/D14 Coverage notes session edit + Escape
- [x] T13 D16 Slim-HTML presence fallback
- [x] T14 D17 Notes / intentional limits
- [ ] T15 Verification bundle + manual smoke (user after portal restart)

## Mapping (brainstorm → task)

| Defect | Task |
|--------|------|
| Cursor escalate ignores recovery | T1 |
| RequiredControlFiller refill leave-empty | T2 |
| Recovery not in Automate proven trail | T5 |
| Partial recovery accepted | T4 |
| resumeFromIntentIndex unimplemented | T10 (removed from contract) |
| automationNotes evidence-only | T5 (proven + json; Excel rewrite deferred) |
| Invent budget before parse | T6 |
| Second recovery hard-fail | intentional v1 (doc T14) |
| Leave-empty only TYPE_FIELD | T3 |
| Vague assert narrow | T8 |
| UI not step grid | T11 |
| Coverage notes not editable | T12 (session) |
| Weak el.clear() | T7 |
| Slim HTML false reject | T13 |
| Gate vs Excel asymmetry | T9 |
| Alternate leave phrasing | T8 |
| Recovery allowlist limits | T14 |
| No Escape on modal | T12 |
| ThreadLocal / any-obstacle era | T14 out of scope |
| Recovery × SUBMIT_REFUSE | T1 |
