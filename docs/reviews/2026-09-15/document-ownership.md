# Document-to-feature ownership

When behavior changes, update the contract in the same change. Historical plans stay; do not silently rewrite past status.

| Contract | Owner surface | Enforcement / evidence |
|----------|---------------|------------------------|
| Result integrity | `JobDiagnostics`, `ResultIntegrity`, status/dashboard | `JobDiagnosticsTest`, `ResultIntegrityTest`, `DashboardServiceTest` |
| Dry-run ≠ live proof | `DryRun*Service`, diagnostics `SIMULATED` | `DryRunConversionServiceTest` |
| Tenant identity | `delivery.identity.*` | `HostedTenantScopeTest`, `TenantIsolationApiTest` |
| Roles | `WorkspaceRole`, portal APIs | `WorkspaceRoleApiTest` |
| Worker destinations | `TargetNetworkPolicy`, `WorkerPac` | `TargetNetworkPolicyTest`, `WorkerPacTest` (application-layer + PAC; not deployed isolation) |
| Provider allowlist / sanitizer | `ProviderPolicy`, `SecretSanitizer`, `ScreenshotRedactor` | `ProviderPolicyTest`, `SecretSanitizerTest`, `ScreenshotRedactorTest`, `SidecarCanaryTest` |
| Durable jobs / cancel | `DurableJobClaim`, `ProcessSupervisor` | `DurableJobClaimTest`, `ProcessSupervisorTest` |
| Artifacts | `ArtifactResolver` | `ArtifactResolverTest` |
| Deletion | `PortalStore.purgeProjectById` | `AdminUserDeletionApiTest`, `AdminDomainsApiTest` |
| Library revisions | `LibraryRevisionStore`, `LibraryRevisionDiff`, project Test cases history UI | `LibraryRevisionStoreTest`, `LibraryRevisionDiffTest`, `GeneratedWorkbookRevisionFlowTest`, `LibraryRevisionApiTest` |
| Production config | `ProductionSafetyGuard` | `ProductionSafetyGuardTest` |
| Backup identity | `StoreBackup` | `StoreBackupTest` (local only) |
| Release CI | `.github/workflows/release.yml` | `ReleaseCiContractTest` |
| Constitution | `.specify/memory/constitution.md` (gitignored) and [constitution-supersession.md](constitution-supersession.md) | 1.2.0 supersession of local-only / Excel-only |
| Dedicated install | [dedicated-install.md](dedicated-install.md), [deployment.md](../../ops/deployment.md) | Implemented; **deferred** from first-launch gates |
| First-release scope | [LAUNCH-DECISION.md](LAUNCH-DECISION.md), [SUPPORT.md](../../SUPPORT.md), [shared-staging.md](../../ops/shared-staging.md) | Shared hosting only; candidate `2721e6d` |
| P0-03 benchmark | [release-benchmark](release-benchmark/README.md) | `ReleaseBenchmarkP003Test`; concurrent sequence 10: [run-2026-09-17-concurrent-f44c76a.md](release-benchmark/run-2026-09-17-concurrent-f44c76a.md) |
| Named approvals | [APPROVALS.md](APPROVALS.md) | unsigned first-release rows; dedicated CIDR row deferred |
| Launch decision | [LAUNCH-DECISION.md](LAUNCH-DECISION.md) | HOLD; shared-only remaining gates; packet [APPROVAL-PACKET.md](APPROVAL-PACKET.md) |

Privacy policy, hosted-target sign-off, and deployed restore drills are **not** owned by a passing unit test.
