# Dedicated installation — identity and network policy

Dedicated (single-tenant) installs use the **same identity model** as shared hosting. They do not get a weaker tenant id, a slug-based storage path, or a global relaxation of shared-host network rules.

## Identity

- Tenant ids remain opaque `ws_` + 32 hex (`TenantId.mint()`). The install may show a mutable `displaySlug`; renaming the slug does not rename storage.
- `ws_install_{slug}` is not a tenant id and must not appear in paths.
- Membership, roles, and OWNER/ADMIN/MEMBER product rules are unchanged. A dedicated install still has one workspace; it is not a reason to skip membership checks.

## Network

- Shared hosting (`delivery.install.mode=shared`, the default) allows the approved public origin only. Loopback, private, link-local, ULA, `file:`, and `javascript:` destinations are blocked except when the **approved origin itself** is that loopback/private host (same host+port) so local fixture pages can run.
- Dedicated mode (`delivery.install.mode=dedicated`) may add `delivery.install.private-cidrs` (comma-separated CIDRs or hosts). That allowlist applies only to dedicated jobs. It does not broaden another tenant or a shared-host worker.
- Subresources may use public CDNs; they may not hit private IPs. Credentials are sent only on the approved origin (`WorkerNetworkGuard` / `TargetNetworkPolicy.credentialsAllowedAt`).
- This is an application-layer navigation/redirect/subresource policy, not a kernel firewall.

## Providers

`delivery.provider.allowlist` is fail-closed when empty. Engine choice (Keel vs Precision) is not a privacy control. Dedicated installs that want local-only models should set an explicit allowlist (for example `ollama`) rather than relying on an unset property.

## Publication lock

`PublicationLock` is an in-JVM mutex plus an OS `FileChannel` lock. Closing check on this workspace volume: `target/publication-lock-fs-probe`, DriveFormat `NTFS` (Windows). Other FileStore types (NFS, SMB, clustered volumes) are unverified; do not treat the lock as portable until that type is probed.
