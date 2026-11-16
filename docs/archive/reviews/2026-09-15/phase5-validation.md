# Phase 5 validation record (2026-09-17)

Workstation `Ramy-Sayed`. Public launch: **HOLD** ([LAUNCH-DECISION.md](LAUNCH-DECISION.md)).

**Nominated candidate:** `23f9351` (explicitly replaced `75e6996`).  
**Current candidate after a test-only fix:** `2721e6d` (`test(phase5): assert preferred hooks write to the tenant site folder`). Product code in `2721e6d` is `23f9351` plus that test. Do not attribute `2721e6d` suite numbers to `23f9351`.

**2026-09-20:** first-release scope is **shared hosting only**. Candidate `2721e6d` is unchanged. Dedicated/private-runner launch validation is deferred. Live UI-TARS grounding is not a supported first-release configuration.

## Environment inspection (this workstation)

| Item | Found |
|------|--------|
| Git HEAD at start | `091b5e0` (docs after `23f9351`) on `launch/p0-baseline-and-p1-assertions` |
| Clean checkout | worktree `D:\priv\testpilot\worktrees\phase5-23f9351` at `23f93514383982fbc53074d6b36abf5bcaa9ce56` |
| Shared/dedicated production hosts | **unavailable** (`gh` CLI missing; no deploy inventory) |
| Portal | leftover PID 5136 on 8081 (dedicated `127.0.0.0/8`, dry-run) was holding H2; stopped for restore |
| Store | local `delivery-store` H2 + two `ws_` tenants + `delivery.secret` on NTFS |
| Ollama `127.0.0.1:11434` | healthy (`qwen2.5`, `ui-tars`, `qwen2.5vl:3b`, `gemma4:e2b`) |
| Facebook IR fixture | **missing** (`delivery-store/facebook-com/prj_e9a9fc7313b2/ir`) |
| Authorized representative apps | **none recorded**. `https://opssit.axispay.app` and Facebook are not treated as authorized for this drill |
| Named approvers | **none** |

## Screenshot redaction and sidecar canaries

Historical local PASS on earlier SHAs still stands as engineering evidence. Named privacy approval remains unsigned, so P2-04 is not a product guarantee.

## Worker-network isolation (`23f9351` worktree)

- Application-layer: `TargetNetworkPolicy` / `WorkerNetworkGuard`.
- Isolated local Spring Boot drills in **separate Maven JVMs**. Shared drill ignores poison CIDRs; dedicated drill allows `10.0.0.0/8` for that install only. Evidence: [phase5-install-drills.md](phase5-install-drills.md).
- **Production shared/dedicated hosts:** still UNAVAILABLE.
- P2-03 remains open.

## Concurrent same-host benchmark

Recorded on `f44c76a`: [run-2026-09-17-concurrent-f44c76a.md](release-benchmark/run-2026-09-17-concurrent-f44c76a.md). **PASS.** Not re-run; not this candidate.

## This-host backup, tenant boundary, lock

| Check | Scope | Result |
|-------|--------|--------|
| Workstation restore | `delivery-store` consistent copy after stopping PID 5136 | 699 files, 0 SHA-256 mismatches, two tenants, key round-trip. [phase5-restore-drill.md](phase5-restore-drill.md) |
| Shared/dedicated drill snapshot | each drill `store-root` | PASS on `23f9351` |
| `StoreBackupTest` / `TenantIsolationApiTest` / `PublicationLockTest` / `Phase5HostEvidenceTest` | included in suites below | see suite rows |

These are **this volume**, not a production restore drill.

## Clean-checkout default suite on `23f9351`

Worktree `phase5-23f9351`. Command: `mvn test` (default; LiveSmoke **included**; install drills skipped by pom). Finished 2026-09-17T20:26:43+03:00.

```
Tests run: 1301, Failures: 3, Errors: 0, Skipped: 2
```

**Skipped (both):**

1. `FacebookSubmitLiveRebindTest.liveRegPageSubmitBindPrefersButtonWhenPresent` — `SkipException`: Facebook IR missing at `delivery-store/facebook-com/prj_e9a9fc7313b2/ir`.
2. `FacebookIrCodegenIdentityTest.facebookIrRegeneratesWithDistinctControlIdentity` — `SkipException`: Facebook IR not present at the same path.

**Failures on `23f9351` (do not reassign to a later SHA):**

1. `ProjectPatchApiTest.patch_savesPreferredHooksOnTheDomainFolder` — asserted `target/test-delivery-store-patch/opssit-axispay-app/preferred-hooks.json`. Portal patch already writes tenant `sites/` via `PreferredHooksStore.save(..., tenant, url)`. A dirty `target/` leftover made this pass on the development tree; the clean worktree had no leftover, so it failed. Fixed in **`2721e6d`** (test only).
2. `UiTarsLiveSmokeTest.uitarsFindsLoginControlAndHonestAssertDoesNotPlaceholderPass` — live `ui-tars` on `https://the-internet.herokuapp.com/login` grounded `input#username` instead of the Login control. Ollama was up. Not a product code change.
3. `VisionRoleSplitLiveSmokeTest.uitarsGroundsLoginAndQwenAssertsHonestly` — same live miss. Not a product code change.

CI `.github/workflows/release.yml` uses `-Pdeterministic`, which excludes `*LiveSmoke*`. Those two live failures are **not** in the release.yml suite. RCA: [vision-live-smoke-rca.md](vision-live-smoke-rca.md). **First-release restriction:** live UI-TARS click-grounding is not a supported configuration. Candidate `2721e6d` is unchanged.

## Deterministic suite on `2721e6d`

Command: `mvn -B -Pdeterministic test` (excludes `*LiveSmoke*`; install drills skipped). Finished 2026-09-17T20:34:15+03:00 on `2721e6d6cf0083192718b21a25864c27ffa68702`.

```
Tests run: 1297, Failures: 0, Errors: 0, Skipped: 2
```

The two skips are the same Facebook IR fixtures as on `23f9351`. Install drills were not re-run: product code is unchanged from `23f9351`.

## Named approvals

[APPROVALS.md](APPROVALS.md) / [APPROVAL-PACKET.md](APPROVAL-PACKET.md) — first-release rows unsigned; dedicated CIDR row deferred.

## Live P5-01 Generate → Execute → Automate

**Not run.** No written authorization for representative applications. Dry-run import→execute→automate on the shared drill is not this gate.

## Information still needed

1. Shared staging host: hostname/DNS, operator access (RDP/SSH), confirmation `delivery.install.mode=shared`.
2. Written authorization for the representative app: name, origin URL, login ownership, credentials, permission to run live Generate → Execute → Automate NEW → downloaded replay → UPDATE → replay.

Dedicated hosts, private-runner targets, and named approver identities are **not** asked here: dedicated is out of first-launch gates; approvers sign when they are ready.

## Next

Keep HOLD. Do not publish. Remaining first-launch work: shared-staging isolation, restore on that store, authorized live G→E→A, named shared-scope signatures. Procedure: [shared-staging.md](../../ops/shared-staging.md).
