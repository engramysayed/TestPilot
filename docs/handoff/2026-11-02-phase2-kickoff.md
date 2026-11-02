# Phase 2 kickoff — failure replay + heal Accept/Reject + flaky tags (2026-11-02)

**Assigned by:** Maestro (user ask via Leader: parallelize beside Codegen W2)  
**Base tip:** `83b9077`  
**Hold:** W3 until W2 GREEN; Postman still deferred; no push without user OK

## DoD

1. **Failure replay evidence** — on failed execute/prove steps, capture **screen + network + console** and expose for replay/review in portal.
2. **Heal Accept/Reject** — heal proposals only; user Accept/Reject applies to **existing Java** (no silent rewrite; no inventing a second page class).
3. **Flaky tags** — mark/tag flaky cases or steps for later suite filtering.

## Owners

| Owner | Slice |
|-------|--------|
| **Keel Backend** | Persist evidence blobs/metadata; APIs for list/get failure evidence; Accept/Reject heal endpoints that patch existing generated Java only; flaky tag store/API |
| **Keel Automation Engine** | Capture screen+network+console at failure; emit heal proposals (not auto-apply); flaky signals into tags |
| **Keel Frontend** | Evidence UI (timeline/replay) + Accept/Reject controls — start shell now; wire when Backend APIs ready |
| **Keel QA/DevOps** | Gate after Backend/AE claim GREEN (serialize Maven; portal may be LIVE) |

## Constraints

- Do **not** touch codegen emit paths reserved for W2 (Codegen owns ladder/playground/merge/ARIA).
- Backup `_backup\` before live-apply.
- Serialize Maven on live tree.
- Handoff per owner under `docs/handoff/` (`2026-11-02-phase2-*.md`).

## Out of scope

- Codegen W3, Postman/API suites, Sonic.
