# Phase 3 kickoff — suites + schedules + alerts / monitor (2026-11-02)

**Assigned by:** Maestro (user via Leader: start P3; bots free after W3)
**Base tip:** `e9ee274` (W3 + Phase 2 CLOSED)
**Hold / out of scope:** Postman/API suites; Codegen idle; Sonic; no merge/force-push without user OK

## Product DoD

1. **Suite tags** — built-ins `smoke` / `regression` / `nightly` plus user-named packs; filter execute/prove by tag.
2. **Schedule + alert on a suite** — attach cron/interval schedule and an alert channel to a suite.
3. **Promote green suite → always-on monitor** — target prod or staging; fire alerts on fail/flake.

Reuse existing `delivery.portal.alerts.*` (`AlertChannel`, Slack/Email/Telegram/Stub) — do **not** invent a second channel stack.

## Owners

| Owner | Slice | Handoff when GREEN |
|-------|--------|--------------------|
| **Keel Backend** | Suite model + tags CRUD; schedule CRUD; alert webhook/channel bind; promote-to-monitor flag/target; Surefire gate; publish interim API contract early for FE/AE | `docs/handoff/2026-11-02-phase3-backend.md` + `docs/handoff/2026-11-02-phase3-api-contract.md` |
| **Keel Frontend** | Suite tags UI; schedule/alert config; promote-to-monitor control — shell first if APIs mid-flight, then wire | `docs/handoff/2026-11-02-phase3-fe.md` |
| **Keel Automation Engine** | Runner honors suite tag filter; schedule trigger hook (claim/start suite run when portal fires schedule) | `docs/handoff/2026-11-02-phase3-ae.md` |
| **Keel QA/DevOps** | Standby → gate suite/schedule/monitor APIs after Backend/AE claim; serialize Maven | `docs/handoff/2026-11-02-qa-gates-phase3-*.md` |
| **Keel Codegen** | Idle (no P3 helper unless Maestro asks) | — |

## Constraints

- Live tree: `D:\Work\projects\Keel\keel\TestPilot` (Ramy). Backup `_backup\` before live-apply.
- **Serialize Maven** — one reactor at a time (portal package vs QA test/test-compile).
- Prefer small slices + revise loop; document every claim under `docs/handoff/`.
- Keel ≠ Sonic.

## Order / parallel

- Backend lands **API contract first** (even stubbed), then model/CRUD.
- FE shells in parallel against the contract.
- AE tag filter + schedule hook in parallel once contract paths/IDs are locked.
- QA gates after each owner GREEN claim; Maestro refreshes portal jar when Backend+FE ship.

## Spike note

Prior spike kickoff `docs/handoff/2026-11-02-phase3-spike-kickoff.md` is superseded by this full assign.
