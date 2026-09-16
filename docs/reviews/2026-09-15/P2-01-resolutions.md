# P2-01 / P2-02 — tenant-storage groundwork (partial)

**Date:** 2026-09-16  
**Status:** partial groundwork. **P2-01 and P2-02 remain open.** This slice is not completed customer isolation.

**Depends on:** P0-03 certified fixture replay on `3a806d3`. P0-01 product/security sign-off remains open.

## In this commit

- Filesystem layout helpers: `tenants/{tenant}/projects/{projectId}`, `tenants/{tenant}/sites/{host}`, `tenants/{tenant}/jobs/{jobId}`.
- `ScopePaths.createJobWorkDir` fails with `FileAlreadyExistsException` if the job leaf exists.
- Portal convert/execute workers pass tenant + job id on `ConversionJobRequest`. CLI and tests that omit them keep the legacy host+timestamp work folder.
- `ProjectStore` with a tenant writes under the tenant tree and still dual-reads a legacy domain/project folder when it already looks like a project.
- Preferred hooks and locator memory tenant overloads do not write the legacy domain-shared file when a tenant is supplied.

## Known identity debt (do not treat as done)

`ws_user_{id}` can bootstrap an initial personal workspace, but **membership must be separate from tenant identity** so account changes do not rename storage. `ws_install_{slug}` is safe only if the slug is immutable; an opaque persisted tenant id is preferable.

## Not done

- Opaque persisted tenant ids that survive account changes and installation renaming.
- Persist `tenantId` on `projects` / `jobs`; enforce it on artifact routes.
- Hunt, caches, hooks, and locator memory scoped consistently in every path.
- Reversible migration with a backup and manifest; quarantine ambiguous legacy data.
- Publication locking across server processes.
- Concurrent same-host sentinel tests and cross-tenant access rejection.
