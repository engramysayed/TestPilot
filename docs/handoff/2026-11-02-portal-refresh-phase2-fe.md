# Portal refresh — Phase 2 evidence FE shell (2026-11-02)

**Tip:** `83b9077`  
**FE handoff:** `docs/handoff/2026-11-02-phase2-fe.md`  
**Backup:** `_backup\backup-pre-phase2-evidence-20261102-190110`

## Ops
- Packaged with `-Dmaven.test.skip=true` (serialized; prior portal stopped first)
- Jar verified: `evidence.html` has Failure replay / heal-proposals / flaky-toggle
- LIVE :8080 — see Maestro note for PID

## Verify
Ctrl+F5 `/evidence` — Failure replay tab, Heal proposals panel, Flaky chip (stub-tolerant until Backend APIs GREEN).
