# Launch decision — HOLD

**Date:** 2026-09-17  
**Revision:** `f44c76abfc35053af09c26c47fcd77c120cad839` (`launch/p0-baseline-and-p1-assertions`)  
**Decision:** **HOLD public rollout.** Do not onboard a pilot cohort and do not publish shared or dedicated distributions as generally available.

## Why HOLD

Engineering on this workstation advanced, but the remaining launch gates are still open:

1. **Named P0-01 approvals are unsigned** ([APPROVALS.md](APPROVALS.md)).
2. **No deployed shared or dedicated installation** was available. Worker-network isolation was exercised as application-layer policy plus a per-thread PAC on this host only. That is not kernel isolation and not a second-host drill.
3. **Backup/restore, tenant-boundary, and filesystem-lock checks** ran on this Windows NTFS volume (`Ramy-Sayed`, FileStore `New Volume`). They do not substitute for a production-host restore drill (P4-02).
4. **P5-01** controlled-app Generate→Execute→Automate, Bug Hunter live evidence, capacity, restart/disk-pressure, and UI walkthrough on a release candidate were **not** executed against customer-representative applications.

## What passed locally (does not replace the above)

| Check | Result |
|-------|--------|
| Screenshot password-box pixel redaction | `ScreenshotRedactorTest` |
| Intercepted Cursor sidecar stdin canary | `SidecarCanaryTest` via `tools/cursor-heal/capture-stdin.mjs` (`SECRET_CANARY_PASSWORD` absent) |
| Shared vs dedicated PAC scripts | `WorkerPacTest`; dedicated CIDRs do not appear in the shared PAC |
| Concurrent same-host ProvePhase+emit+ZIP | **PASS** — see [run-2026-09-17-concurrent-f44c76a.md](release-benchmark/run-2026-09-17-concurrent-f44c76a.md) |
| Tenant API boundary | `TenantIsolationApiTest` on this host |
| Store snapshot/restore path+checksum | `StoreBackupTest` on this volume |
| OS `FileChannel` lock | `PublicationLockTest` on this NTFS FileStore |
| Consolidated RC suite | **121 tests, 0 failures**, 2026-09-17T12:15:35+03:00 |

The earlier 100-test Phase 4 pass on `bbbe31e` and the 29-test P0-03 fixture replay remain valid for fixture/engineering history. They still do not prove public-launch readiness.

## Rollback / next

Hold the branch. Close the four blockers (deployed isolation, real-host restore on a supported FileStore, named approvals, P5-01 representative acceptance) before any launch decision other than HOLD.
