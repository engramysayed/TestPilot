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
- `projects.tenant_id` / `jobs.tenant_id` persist the opaque id. Read access is membership; operate (mutate/execute/export/credentials/artifact download+delete) is OWNER or ADMIN; administer (delete project) is OWNER only. Members may list artifacts.
- Convert, execute, and Hunt login requests carry tenant + job id. Preferred hooks and locator memory use the tenant site tree when a tenant is present. Credential-revision memory keys include tenant.
- `TenantLayoutMigrator` journals each move. `recover()` resumes an in-progress journal after a crash; `revert()` still undoes a completed manifest. Ambiguous domain-shared hooks/memory are quarantined.
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

## Still open (do not treat as done)

- Live ProvePhase / P0-03 concurrent same-host sequence (browser proof, not only dry-run packages).
- Role checks on every remaining mutate path (generate/compare/workbook writes still use membership in places).
- Dedicated single-tenant product behavior documented as the same identity model.
- OS file lock on each **supported deployment** filesystem (network shares, Linux production volume) — this machine’s volume only so far.
- Broader customer isolation also depends on P2-03 worker/network boundaries and P2-04 data/provider controls.

The P0-03 concurrent same-host sequence remains an expected isolation failure until live ProvePhase isolation is shown.
