# Launch decision — HOLD

**Date:** 2026-09-17  
**Candidate for remaining Phase 5 validations:** `75e6996`  
**Decision:** **HOLD public rollout.** Do not onboard a pilot cohort and do not publish shared or dedicated distributions as generally available. Development may continue; public rollout stays paused.

The JVM-wide install-network boundary is accepted from the checks already recorded on this candidate. Do **not** re-run unchanged local tests. Further progress requires deployment environments, representative applications, and named approvers.

## Why HOLD

1. **Named P0-01 approvals are unsigned** ([APPROVALS.md](APPROVALS.md)).
2. **Worker-network isolation** is still application-layer + PAC. Isolated local shared/dedicated Spring Boot contexts bind Spring `delivery.install.*` into `TargetNetworkPolicy.forJob` on a **per-JVM** basis ([phase5-install-drills.md](phase5-install-drills.md)). That is **not** kernel isolation and **not** a production second host.
3. **Backup/restore and tenant-boundary** ran against each drill's `delivery.store-root`. They are still this workstation's NTFS volume, not a production-host restore (P4-02).
4. **P5-01** Generate→Execute→Automate→downloaded ZIP **test-compile** ran as **dry-run** on the shared drill (no live LLM, no customer application, no live browser proof). Capacity, restart/disk-pressure, Bug Hunter live evidence, and a portal UI walkthrough remain undone.

## What passed locally (does not replace the above)

Prior `f44c76a` checks stand. Additional 2026-09-17T12:54:15+03:00 (bridge + drills) then 2026-09-17T13:20+03:00 (separate JVMs), committed as `75e6996`:

| Check | Result |
|-------|--------|
| `InstallNetworkBridgeTest` | Spring dedicated/shared config reaches worker policy; two jobs on one JVM share the same mode; job/tenant-scoped install keys are rejected |
| `SharedInstallDrillTest` | own Maven JVM (PID 21452, 2026-09-17T13:20:24+03:00): health=shared; poison CIDRs ignored; store backup/restore; cross-tenant 404; import→execute→automate→ZIP test-compile (dry-run). **3 tests, 0 failures** |
| `DedicatedInstallDrillTest` | own Maven JVM (PID 6732, 2026-09-17T13:20:46+03:00): health=dedicated; `10.0.0.0/8` allowed on that install only; store backup/restore; cross-tenant 404. **2 tests, 0 failures** |
| Combined `-Dtest=` in one JVM | **fails as designed** (`requireOwnJvm`: expected `dedicated`, found `shared`; 2026-09-17T13:21:29+03:00) |

## Rollback / next

Keep HOLD. Phase 5 stays open. Remaining gates on candidate `75e6996` (unless a later commit is explicitly re-nominated):

- Deployed shared and dedicated environments with kernel or equivalent worker isolation
- Restore on those hosts' FileStores
- Live representative Generate → Execute → Automate → downloaded replay (not dry-run)
- Named product/security/privacy signatures
