# Launch decision — HOLD

**Date:** 2026-09-20  
**Validation candidate:** `2721e6d` (unchanged)  
**Nominated product SHA:** `23f9351`  
**First-release scope:** **shared hosting only**  
**Decision:** **HOLD public rollout.** Do not onboard a pilot cohort and do not publish a generally available distribution.

Dedicated installations and private runners remain **implemented**. They are **not** first-launch support and **not** first-launch validation gates.

## Why HOLD

1. **Named P0-01 approvals are unsigned** for the **shared** first-release contract ([APPROVALS.md](APPROVALS.md), [APPROVAL-PACKET.md](APPROVAL-PACKET.md)). Dedicated-install sign-off is deferred with the dedicated product.
2. **Shared-host worker-network isolation** on a staging host is still missing. Local shared-install drills are application-layer + PAC on this workstation ([phase5-install-drills.md](phase5-install-drills.md)).
3. **Backup/restore** has not run on **shared staging** storage. Workstation NTFS restore is not that drill ([phase5-restore-drill.md](phase5-restore-drill.md), [shared-staging.md](../../ops/shared-staging.md)).
4. **Live Generate → Execute → Automate NEW → downloaded replay → UPDATE → replay** has not run against an authorized representative application (DOM path). Shared-drill dry-run import is not this gate.

Live UI-TARS click-grounding is **not** a first-release supported configuration ([vision-live-smoke-rca.md](vision-live-smoke-rca.md)). Those two live misses do not replace `2721e6d`.

## What was measured (unchanged candidate)

| Check | SHA | Result |
|-------|-----|--------|
| `SharedInstallDrillTest` | `23f9351` | 3 tests, 0 failures (local JVM; not staging) |
| `DedicatedInstallDrillTest` | `23f9351` | 2 tests, 0 failures — **preserved implementation; not a first-launch gate** |
| Workstation restore | this NTFS volume | 699 files, 0 mismatches — **not** shared staging |
| Default `mvn test` | `23f9351` | 1301 run, 3 failed, 2 skipped |
| `mvn -Pdeterministic test` | `2721e6d` | 1297 run, 0 failed, 2 skipped (Facebook IR) |

## Remaining first-launch gates on `2721e6d`

- Shared staging worker-network isolation and cross-tenant 404
- Restore on that staging store (DB, artifacts, encryption key, checksums, tenant bounds)
- Authorized live G→E→A NEW → downloaded replay → UPDATE → replay
- Named product / security / privacy signatures for **shared** scope

Dedicated deployment validation is **not** on this list.
