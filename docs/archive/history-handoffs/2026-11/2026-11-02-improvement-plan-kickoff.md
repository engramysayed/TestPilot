# 2026-11-02 — Keel improvement plan kickoff

**Plan Page:** https://cursor.com/canvas/store-d6a38265-0f1d-4369-89cb-4aa513feaa2f/d1e5438a-6ba7-47f4-8310-b91f540e2834  
**Live tip at kickoff:** `b41c774` on `D:\Work\projects\Keel\keel\TestPilot`  
**Coordinator:** Maestro (assigns / revise loop). Leader owns Page + user. Skill: Keel bot handoff loop.

## Constraints (locked)
- Generate-from-knowledge = SEPARATE option/section; do NOT replace current Generate/Automate.
- One Java class per page (locators + actions + asserts).
- Prefer existing portal preferred-hook; Automate full ladder as fallback.
- Heal = Accept/Reject only (Phase 2); no silent rewrite.
- Postman/API = later (do not start).
- Keel paths only; Sonic stays `D:\Work\projects\Sonic`.
- Document every slice here under `docs/handoff/`.

## Assignments (start)
| Slice | Owner | Status |
|-------|--------|--------|
| OPS handoff loop | Maestro | Active — this file |
| P1 Knowledge ingest + store | Keel Backend | Assigned |
| P1 Generate-from-knowledge UI | Keel Frontend | Assigned (shell/IA; APIs follow Backend) |
| P1 Bug Hunter + knowledge | Keel Automation Engine | Assigned (after/with pack schema) |
| Codegen W1 | Keel Codegen | Assigned (parallel) |
| Gates | Keel QA/DevOps | Standby |
| P4 visual + plain-English | FE + Backend/AE | AE disk compare shipped (flag off); Backend HTTP approve next |

## Verify
- Specialists report DoD to Maestro; Maestro revises; then handoff note + Leader FYI.
- No Sonic Remote dependency for this plan.

## Blocked / next
- Open: Generate-from-knowledge as option vs separate section (FE propose).
- Confirm portal/engine up before live UI demos.

## Update 2026-11-02 ~17:18 Africa/Cairo
Leader: assignments OK — proceed. UI shape locked: FE chooses option/tab under Generate (preferred if fastest) or separate section; current Generate unchanged; no Leader wait. Codegen W1 ACK started.

## Update 2026-11-02 ~18:53 Africa/Cairo
User (via Leader): parallelize. Codegen W2 remains primary for Codegen+QA. Phase 2 assigned Backend+AE+FE (evidence UI). Phase 3 Backend design/spike OK if bandwidth. Hold W3 until W2 GREEN. See 2026-11-02-phase2-kickoff.md, 2026-11-02-phase3-spike-kickoff.md.

## Update 2026-11-02 ~19:15 Africa/Cairo
User (via Leader): start Codegen W3. P3 stays deferred. See `2026-11-02-codegen-w3-kickoff.md`.
