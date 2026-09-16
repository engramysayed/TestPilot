# P2-01 / P2-02 — tenant-storage groundwork (partial)

**Date:** 2026-09-16  
**Status:** tenant-storage **groundwork**. **P2-01 and P2-02 remain open.** This is not completed customer isolation.

**Depends on:** P0-03 certified fixture replay on `3a806d3`. P0-01 product/security sign-off remains open.

## Identity (settled)

Tenant ids are opaque `ws_` + 32 hex (`TenantId.mint()`). Account numbers and installation slugs are membership or display metadata and must not appear in storage paths.

- `WorkspaceDirectory.ensurePersonalWorkspace(userId)` may bootstrap an initial personal workspace, but the id does not embed the user. `rebindAccount` changes membership without renaming the tenant.
- Dedicated installations get an opaque id plus a mutable `displaySlug`. Renaming the slug does not change the tenant id. `ws_install_{slug}` is not a tenant id.

## In this groundwork

- Filesystem layout: `tenants/{tenant}/projects/{projectId}`, `sites/{host}`, `jobs/{jobId}`. Exclusive job dirs.
- Hosted portal convert/execute/Hunt use `TenantScope.HOSTED` and fail closed (`TENANT_REQUIRED`) when tenant/job are omitted. CLI and tests that omit them must pass `TenantScope.LEGACY_EXPLICIT`. Tenant-scoped `ProjectStore` no longer dual-reads legacy domain folders.
- Convert, execute, Hunt, generate, compare, workbook writes, pipeline, design-reference upload, pre-run review, IR clear, job cancel/force-stop, execute-run delete, and artifact download/delete require OWNER or ADMIN to operate. Members may list/read. Project deletion is OWNER only.
- **Product role contract (approved):** read-only MEMBER and OWNER-only deletion are product decisions, not automatic consequences of tenant isolation. ADMIN may operate but cannot delete the project.
- Convert, execute, and Hunt login requests carry tenant + job id. Preferred hooks and locator memory use the tenant site tree when a tenant is present. Credential-revision memory keys include tenant.
- `TenantLayoutMigrator` journals each move. `recover()` resumes an in-progress journal after a crash; `recover(store, crashAfterOps)` can be interrupted again; a completed journal returns the same counts without remigrating; `revert()` still undoes a completed manifest. File contents are preserved across repeated interruptions. Ambiguous domain-shared hooks/memory are quarantined.
- `PublicationLock` is an in-JVM lock plus OS `FileChannel` lock. A helper process was blocked on this workspace volume’s `FileStore` (`target/publication-lock-fs-probe`). That is not a claim for every deployment filesystem.

## Targeted tests that passed (2026-09-16)

Opaque id + membership rebind/rename; hosted fail-closed; role enforcement (member 403 on export/mutate/execute/credentials/delete); migrator quarantine, revert, and crash-halfway recover; in-JVM and cross-process publication lock on this volume; concurrent hosted dry-run packages with distinct hooks/memory/evidence sentinels; portal artifact cross-tenant 404.

Commit `0acc31d` recorded **72** tests (not this follow-up):

```
mvn "-Dtest=TenantIdTest,WorkspaceDirectoryTest,WorkspaceMembershipTest,ScopePathsTest,PublicationLockTest,TenantLayoutMigratorTest,ConcurrentSameHostSentinelTest,HuntRuntimeFactoryTenantTest,ProjectStoreTenantIsolationTest,TenantScopedHooksAndMemoryTest,TenantIsolationApiTest,ProjectStoreTest,ProjectStoreDomainLayoutTest,AuthOwnershipTest,ProjectArtifactsApiTest,PreferredHooksStoreTest,DomainLocatorMemoryTest,ReuseEligibilityTest" test
Tests run: 72, Failures: 0 (BUILD SUCCESS, 2026-09-16T19:22:45+03:00).
```

This follow-up (fail-closed hosted scope, roles, crash recover, volume file lock, concurrent dry-run sentinels) was verified separately:

```
mvn "-Dtest=HostedTenantScopeTest,WorkspaceRoleEnforcementTest,WorkspaceRoleApiTest,TenantLayoutMigratorTest,ConcurrentSameHostPipelineTest,PublicationLockTest,HuntRuntimeFactoryTenantTest,ProjectStoreTenantIsolationTest,DryRunConversionServiceTest,TenantIsolationApiTest,AuthOwnershipTest,ProjectArtifactsApiTest" test
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS, 2026-09-16T19:36:46+03:00).
```

Working-tree follow-up (not a commit): remaining operate-role routes, approved MEMBER/OWNER product contract, interruptible idempotent recover, concurrent ProvePhase + emit + download on local pages (no live LLM). Do not attribute this to `0acc31d` or `c652fd9`.

```
mvn "-Dtest=WorkspaceRoleApiTest,WorkspaceRoleEnforcementTest,TenantLayoutMigratorTest,ConcurrentSameHostProveEmitTest" test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS, 2026-09-17T01:29:39+03:00).
```

Phase 2 remaining slices + Phase 3 claim groundwork (working tree, 2026-09-17):

```
mvn "-Dtest=SecretSanitizerTest,ProviderPolicyTest,SecretSanitizerIntegrationTest,TargetNetworkPolicyTest,WorkerNetworkGuardTest,DurableJobClaimTest,PublicationLockTest,WorkspaceRoleApiTest,WorkspaceRoleEnforcementTest,TenantLayoutMigratorTest,ConcurrentSameHostProveEmitTest,HuntCoreTest,HostedTenantScopeTest" test
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS, 2026-09-17T01:53+03:00).
```

```
mvn "-Dtest=LocalLlmClientTest,PhraseAssertBindTest,AccessibleNameCaptionTest,DryRunConversionServiceTest,TenantIsolationApiTest,AuthOwnershipTest,ProjectArtifactsApiTest,WorkspaceRoleApiTest,HuntApiTest,DurableJobClaimTest,SecretSanitizerIntegrationTest,ProviderPolicyTest" test
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS, 2026-09-17T01:54:16+03:00).
```

## Still open (do not treat as done)

- This working-tree slice is uncommitted until asked. P2-01 and P2-02 stay open until remaining isolation gates close. Do not advertise completed customer isolation.
- Dedicated single-tenant product behavior is documented in [dedicated-install.md](dedicated-install.md) (same identity model; dedicated CIDRs do not broaden shared host). Product/security sign-off of that document is still P0-01.
- OS file lock: probed on this workspace volume at `target/publication-lock-fs-probe` (`PublicationLockTest.osLockIsExercisedOnThisStoresFileSystemType`). This machine’s data volume is **NTFS**. Other FileStore types (NFS/SMB/production Linux volumes) remain unverified.
- The P0-03 concurrent same-host **benchmark** case has not been re-run from a commit that includes live ProvePhase isolation.

## P2-03 / P2-04 (working tree)

Application-layer worker network policy (`TargetNetworkPolicy` / `WorkerNetworkGuard`) on Hunt navigate and ProvePhase `driver.get`. Shared vs dedicated modes; dedicated CIDRs do not widen shared jobs. Credentials stay on the approved origin. `WorkerCredentialScope` holds target creds only.

`SecretSanitizer` masks password DOM values, token query/userinfo, and `password=` assignments before slim HTML, Hunt packs, and JS results. `ProviderPolicy` allowlist is fail-closed when empty and is checked on Ollama, Cursor, AgentRouter, and vision fallbacks. Default `delivery.provider.allowlist` in `application.properties` keeps current engines allowed; an unset/empty list blocks dispatch.

