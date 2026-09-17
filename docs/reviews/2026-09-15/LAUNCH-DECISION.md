# Launch decision — HOLD

**Date:** 2026-09-17  
**Engineering revision:** `f44c76a` (prior local checks). **This update** records isolated local install drills; public rollout stays **HOLD**.  
**Decision:** **HOLD public rollout.** Do not onboard a pilot cohort and do not publish shared or dedicated distributions as generally available.

## Why HOLD

1. **Named P0-01 approvals are unsigned** ([APPROVALS.md](APPROVALS.md)).
2. **Worker-network isolation** is still application-layer + PAC. Isolated local shared/dedicated Spring Boot contexts bind Spring `delivery.install.*` into `TargetNetworkPolicy.forJob` on a **per-JVM** basis ([phase5-install-drills.md](phase5-install-drills.md)). That is **not** kernel isolation and **not** a production second host.
3. **Backup/restore and tenant-boundary** now run against each drill's `delivery.store-root`. They are still this workstation's NTFS volume, not a production-host restore (P4-02).
4. **P5-01** Generate→Execute→Automate→downloaded ZIP **test-compile** ran as **dry-run** on the shared drill (no live LLM, no customer application, no live browser proof). Capacity, restart/disk-pressure, Bug Hunter live evidence, and a portal UI walkthrough remain undone.

## What passed locally (does not replace the above)

Prior `f44c76a` checks stand. Additional 2026-09-17T12:54:15+03:00 (bridge + drills) then 2026-09-17T13:20+03:00 (separate JVMs):

| Check | Result |
|-------|--------|
| `InstallNetworkBridgeTest` | Spring dedicated/shared config reaches worker policy; two jobs on one JVM share the same mode; job/tenant-scoped install keys are rejected |
| `SharedInstallDrillTest` | own Maven JVM (PID 21452, 2026-09-17T13:20:24+03:00): health=shared; poison CIDRs ignored; store backup/restore; cross-tenant 404; import→execute→automate→ZIP test-compile (dry-run). **3 tests, 0 failures** |
| `DedicatedInstallDrillTest` | own Maven JVM (PID 6732, 2026-09-17T13:20:46+03:00): health=dedicated; `10.0.0.0/8` allowed on that install only; store backup/restore; cross-tenant 404. **2 tests, 0 failures** |
| Combined `-Dtest=` in one JVM | **fails as designed** (`requireOwnJvm`: expected `dedicated`, found `shared`; 2026-09-17T13:21:29+03:00) |

## Rollback / next

Keep HOLD. Remaining gates: production shared/dedicated hosts with kernel or equivalent worker isolation; restore on those hosts' FileStores; live representative Generate/Execute/Automate (not dry-run); named product/security/privacy signatures.
