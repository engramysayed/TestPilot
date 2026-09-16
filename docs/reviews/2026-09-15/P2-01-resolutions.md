# P2-01 / P2-02 — tenant-storage groundwork (partial)

**Date:** 2026-09-16  
**Status:** tenant-storage **groundwork**. **P2-01 and P2-02 remain open.** This is not completed customer isolation.

**Depends on:** P0-03 certified fixture replay on `3a806d3`. P0-01 product/security sign-off remains open.

## Identity (settled)

Tenant ids are opaque `ws_` + 32 hex (`TenantId.mint()`). Account numbers and installation slugs are membership or display metadata and must not appear in storage paths.

- `WorkspaceDirectory.ensurePersonalWorkspace(userId)` may bootstrap an initial personal workspace, but the id does not embed the user. `rebindAccount` changes membership without renaming the tenant.
- Dedicated installations get an opaque id plus a mutable `displaySlug`. Renaming the slug does not change the tenant id. `ws_install_{slug}` is not a tenant id.

## In this groundwork

- Filesystem layout: `tenants/{tenant}/projects/{projectId}`, `sites/{host}`, `jobs/{jobId}`. Exclusive job dirs; host+timestamp work folders remain only when tenant/job are omitted (CLI/legacy tests).
- `projects.tenant_id` / `jobs.tenant_id` persist the opaque id. Portal create bootstraps a personal workspace and stores it. Artifact routes use membership (`getOwnedProject` / `getOwnedJob`), not owner-id equality alone.
- Convert, execute, and Hunt login requests carry tenant + job id. Preferred hooks and locator memory use the tenant site tree when a tenant is present (ProvePhase, LiveHuntService, project settings). Credential-revision memory keys include tenant.
- `TenantLayoutMigrator` moves known project trees under the tenant layout and writes a reversible manifest. Ambiguous domain-shared hooks/memory are quarantined, not assigned.
- `PublicationLock` combines an in-JVM lock with an OS `FileChannel` lock so publication serializes across threads and server processes. `ProjectStore.saveVersion` uses it and records `tenantId` in `project.json`.

## Targeted tests that passed (2026-09-16)

Opaque id + membership rebind/rename; migrator + quarantine + revert; in-JVM and cross-process publication lock; concurrent same-host job dirs with distinct sentinels; concurrent `saveVersion` keeping v1 and v2; Hunt login request tenant/job; portal artifact cross-tenant 404 with distinct ZIP sentinels.

## Still open (do not treat as done)

- Fail closed on missing tenant context. Hosted convert/execute/Hunt paths must not fall back to legacy directories when tenant/job IDs are omitted. Any leftover compatibility stays explicitly restricted (CLI/tests), not the default.
- Enforce **roles** as well as membership. A member must not automatically authorize deletion or administration. Cover mutate, execute, export, credentials, and artifacts.
- Full same-host pipeline: concurrent jobs with overlapping TCs must keep each customer’s proof, packages, evidence, hooks, and memory separate through publication.
- Migration interruption recovery: the reversible manifest must recover a crash halfway through migration, not only a successful migrate-then-revert.
- OS file lock: useful in tests; validate on the actual supported storage filesystem before claiming deployment-wide protection.
- Dedicated single-tenant product behavior documented as the same identity model.
- Broader customer isolation also depends on P2-03 worker/network boundaries and P2-04 data/provider controls.

The P0-03 concurrent same-host sequence remains an expected isolation failure until the full pipeline check passes.
