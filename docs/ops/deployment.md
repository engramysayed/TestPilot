# Shared and dedicated deployment

Keel supports two install **profiles**. They share the same identity model (`ws_` + 32 hex tenant ids, OWNER/ADMIN/MEMBER). Dedicated mode does not weaken tenant paths or shared-host network rules.

This document is the engineering checklist. A passing local backup unit test is **not** a signed shared/dedicated deployment drill (P4-02 / P5-01).

## Profiles

| | Shared (`delivery.install.mode=shared`) | Dedicated (`delivery.install.mode=dedicated`) |
|--|--|--|
| Tenants | Many workspaces on one install | One workspace; same identity types |
| Browser targets | Approved public origin only | May add `delivery.install.private-cidrs` for that install only |
| Providers | Explicit `delivery.provider.allowlist` | Same; local-only means allowlist `ollama` (or similar), not an unset property |
| Private CIDRs | Must be empty | Optional extra CIDRs/hosts |

Network checks are application-layer (navigate/redirect/subresource) plus a per-thread PAC. Spring `delivery.install.mode` / `private-cidrs` must be copied into worker system properties (`InstallNetworkBridge`) or Prove/Hunt/Execute jobs still see the default shared policy. Those properties are installation-wide for the JVM; do not set per-job or per-tenant overrides. Isolated local shared/dedicated drills run in separate JVMs; P2-03 stays open until production-host worker-network tests pass.

## Production configuration

Set `delivery.install.production=true` (or Spring profile `prod`). Startup refuses:

- default admin password `ChangeMeAdmin1!`
- non-HTTPS `delivery.public-base-url`
- enabled H2 console
- empty provider allowlist
- blank datasource password
- `private-cidrs` while mode is `shared`

Change the admin password before any shared or remote use. TLS, session cookies, and operator access still need a **deployed** review.

## Health

- `GET /api/health` — process up, install mode
- `GET /api/ready` — store writable + job status counts
- `GET /api/admin/ops/metrics` — ADMIN-only queue/running/cancelling counts and runbook pointers

## Database and store

Default: H2 file `./delivery-store/portal-db` plus `delivery-store/tenants/{tenantId}/…`. Postgres may replace H2 later; keep artifact files on the same volume as the DB backup.

Upgrade from a previous layout: `TenantLayoutMigrator` (legacy domain folders → tenant paths, quarantine ambiguous shared files). Preflight on a copy of production data before touching live disks.

## Workers

Browser prove/execute/hunt jobs share the portal JVM today. Isolation is tenant-storage + application network policy, not separate worker hosts. Process deadlines use `ProcessSupervisor`. Queue caps: `delivery.jobs.max-queued-per-tenant`, `delivery.jobs.max-running-per-install`.
