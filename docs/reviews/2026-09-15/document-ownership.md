# Document-to-feature ownership

When behavior changes, update the contract in the same change. Historical plans stay; do not silently rewrite past status.

| Contract | Owner surface | Enforcement / evidence |
|----------|---------------|------------------------|
| Result labels (PASS/FAIL/TODO/PARTIAL/REUSED/SIMULATED/BLOCKED) | `JobDiagnostics`, status page, [SUPPORT.md](../../SUPPORT.md) | `JobDiagnosticsTest` |
| Dry-run ≠ live proof | `DryRun*Service`, diagnostics `SIMULATED` | `DryRunConversionServiceTest` |
| Tenant identity | `delivery.identity.*` | `HostedTenantScopeTest`, `TenantIsolationApiTest` |
| Roles | `WorkspaceRole`, portal APIs | `WorkspaceRoleApiTest` |
| Worker destinations | `TargetNetworkPolicy` | `TargetNetworkPolicyTest` (application-layer only) |
| Provider allowlist / sanitizer | `ProviderPolicy`, `SecretSanitizer` | `ProviderPolicyTest`, `SecretSanitizerTest` |
| Durable jobs / cancel | `DurableJobClaim`, `ProcessSupervisor` | `DurableJobClaimTest`, `ProcessSupervisorTest` |
| Artifacts | `ArtifactResolver` | `ArtifactResolverTest` |
| Deletion | `PortalStore.purgeProjectById` | `AdminUserDeletionApiTest`, `AdminDomainsApiTest` |
| Library revisions | `LibraryRevisionStore`, `LibraryRevisionDiff` | `LibraryRevisionStoreTest`, `LibraryRevisionDiffTest` |
| Production config | `ProductionSafetyGuard` | `ProductionSafetyGuardTest` |
| Backup identity | `StoreBackup` | `StoreBackupTest` (local only) |
| Release CI | `.github/workflows/release.yml` | `ReleaseCiContractTest` |
| Constitution | `.specify/memory/constitution.md` (gitignored) and [constitution-supersession.md](constitution-supersession.md) | 1.2.0 supersession of local-only / Excel-only |
| Dedicated install | [dedicated-install.md](dedicated-install.md), [deployment.md](../../ops/deployment.md) | P2-03/P4-02 still open |
| P0-03 benchmark | [release-benchmark](release-benchmark/README.md) | `ReleaseBenchmarkP003Test` |

Privacy policy, hosted-target sign-off, and deployed restore drills are **not** owned by a passing unit test.
