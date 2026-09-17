# Launch decision — HOLD

**Date:** 2026-09-17  
**Current validation candidate:** `2721e6d` (`test(phase5): assert preferred hooks write to the tenant site folder`)  
**Nominated then tested:** `23f9351` (explicitly replaced `75e6996`; Phase 6 product SHA)  
**Decision:** **HOLD public rollout.** Do not onboard a pilot cohort and do not publish shared or dedicated distributions as generally available. Development may continue; public rollout stays paused.

`75e6996` is superseded. A clean checkout of `23f9351` was used for shared/dedicated drills, workstation restore, and the default suite. That default suite **failed** (stale preferred-hooks path assertion + two live ui-tars misses). The test assertion is fixed in `2721e6d`. Product code is unchanged from `23f9351`. Do not attribute `2721e6d` results to `23f9351`.

## Why HOLD

1. **Named P0-01 approvals are unsigned** ([APPROVALS.md](APPROVALS.md), [APPROVAL-PACKET.md](APPROVAL-PACKET.md)).
2. **Worker-network isolation** is still application-layer + PAC. Isolated local shared/dedicated Spring Boot contexts bind Spring `delivery.install.*` into `TargetNetworkPolicy.forJob` on a **per-JVM** basis ([phase5-install-drills.md](phase5-install-drills.md)). That is **not** kernel isolation and **not** a production second host.
3. **Backup/restore and tenant-boundary** ran against this workstation's `delivery-store` and each drill's `delivery.store-root`. They are still this NTFS volume, not a production-host restore (P4-02). See [phase5-restore-drill.md](phase5-restore-drill.md).
4. **P5-01 live Generate → Execute → Automate NEW → downloaded-framework replay → UPDATE → replay** was **not** run against authorized representative applications. Shared-drill Generate-import → dry-run Execute → dry-run Automate → ZIP test-compile is not live proof.

## What was measured

Clean worktree of `23f9351` plus follow-up tests on `2721e6d`. Workstation `Ramy-Sayed`.

| Check | SHA | Result |
|-------|-----|--------|
| `SharedInstallDrillTest` | `23f9351` | own Maven JVM (PID **19556**, 2026-09-17T20:19:07+03:00): health=shared; poison CIDRs ignored; store backup/restore; cross-tenant 404; import→execute→automate→ZIP test-compile (dry-run). **3 tests, 0 failures** |
| `DedicatedInstallDrillTest` | `23f9351` | own Maven JVM (PID **29044**, 2026-09-17T20:20:32+03:00): health=dedicated; `10.0.0.0/8` allowed on that install only; store backup/restore; cross-tenant 404. **2 tests, 0 failures** |
| Workstation restore | `23f9351` product / this disk | consistent copy of `delivery-store` after stopping leftover portal PID 5136: 699 files, 0 checksum mismatches, two `ws_` tenants, `delivery.secret` and `portal-db.mv.db` SHA-256 match, encryption round-trip. **Not** a production FileStore |
| Default `mvn test` | `23f9351` | **1301 run, 3 failed, 2 skipped** (see [phase5-validation.md](phase5-validation.md)) |
| `mvn -Pdeterministic test` | `2721e6d` | **1297 run, 1295 passed, 0 failed, 2 skipped** (2026-09-17T20:34:15+03:00). Same two Facebook IR skips. LiveSmoke excluded (matches CI) |

## Rollback / next

Keep HOLD. Phase 5 stays open. Remaining gates on candidate **`2721e6d`** (documentation-only SHAs after it are not the code candidate):

- Deployed shared and dedicated environments with kernel or equivalent worker isolation
- Restore on those hosts' FileStores
- Live representative Generate → Execute → Automate NEW → downloaded replay → UPDATE → replay (not dry-run)
- Named product/security/privacy signatures
