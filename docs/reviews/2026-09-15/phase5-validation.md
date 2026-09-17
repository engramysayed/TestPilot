# Phase 5 validation record (2026-09-17)

Workstation `Ramy-Sayed`. **Candidate for remaining Phase 5 validations:** `75e6996`. Public launch: **HOLD** ([LAUNCH-DECISION.md](LAUNCH-DECISION.md)). Do not re-run unchanged local tests; next evidence must come from deployment environments, representative applications, and named approvers.

## Screenshot redaction and sidecar canaries

- `ScreenshotRedactor` paints password-field boxes black. Wired at Selenium grounding, Hunt, prove evidence, and `ElementsHandler.capture`.
- `CursorHealClient.sanitizeRequest` scrubs HTML, prompts, and string arrays before sidecar stdin.
- `SidecarCanaryTest` captured Node stdin via `tools/cursor-heal/capture-stdin.mjs`. Canary `SECRET_CANARY_PASSWORD` did not appear.

Engineering for those two P2-04 checks is done **locally**. Named privacy approval remains unsigned, so P2-04 is not a product guarantee.

## Worker-network isolation

- Application-layer: `TargetNetworkPolicy` / `WorkerNetworkGuard` (unchanged contract).
- Stronger local path: per-thread PAC (`WorkerPac`) installed before Chrome/Edge in ProvePhase and Hunt; non-approved hosts go to `PROXY 127.0.0.1:9`. Loopback is bypassed for Chromedriver; unapproved loopback page loads still fail `requireNavigate`.
- Dedicated CIDRs are emitted only for dedicated PAC scripts.
- Isolated local Spring Boot drills (2026-09-17): Spring `delivery.install.*` is copied into worker system properties (`InstallNetworkBridge`). That policy is **JVM-wide / installation-wide** (not per tenant or job). Shared and dedicated drills run in separate Maven JVMs. Shared drill ignores poison CIDRs; dedicated drill allows `10.0.0.0/8` for that install only. Evidence: [phase5-install-drills.md](phase5-install-drills.md).
- **Production shared/dedicated hosts:** still UNAVAILABLE.
- P2-03 remains open.

## Concurrent same-host benchmark

Recorded on this revision: [run-2026-09-17-concurrent-f44c76a.md](release-benchmark/run-2026-09-17-concurrent-f44c76a.md). **PASS.**

## This-host backup, tenant boundary, lock

| Check | Scope | Result |
|-------|--------|--------|
| `StoreBackupTest` | in-process snapshot/restore of tenant path + SHA-256 | PASS |
| `TenantIsolationApiTest` | opaque tenants; cross-tenant artifact 404 | PASS |
| `PublicationLockTest` | JVM + OS lock on FileStore type **NTFS** (`New Volume`) | PASS |
| `Phase5HostEvidenceTest` | writes `target/phase5-host-evidence/evidence.txt` | PASS |

These are **this volume / this JVM**, not a production restore drill. Isolated shared/dedicated *test* store-roots were later snapshot/restored in [phase5-install-drills.md](phase5-install-drills.md); still not a production host.

## Named approvals

[APPROVALS.md](APPROVALS.md) — all rows unsigned.

## Consolidated RC suite

```
Tests run: 121, Failures: 0, Errors: 0, Skipped: 0
Finished at: 2026-09-17T12:15:35+03:00
```

Includes the concurrent ProveEmit case plus screenshot/sidecar/PAC/host-evidence tests. Does not include P5-01 representative live Generate→replay on customer apps, capacity, or a browser UI walkthrough of the portal.

## Next (candidate `75e6996`)

Local install-network and drill evidence on this candidate is accepted. Do not re-run those checks. Remaining Phase 5 work needs deployed shared/dedicated environments, restore on those FileStores, live representative Generate → Execute → Automate → downloaded replay, and named P0-01 signatures. Development may continue; public rollout stays **HOLD**.
