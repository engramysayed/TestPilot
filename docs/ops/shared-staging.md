# Shared staging (first-release install)

First-release support is **shared hosting only** (`delivery.install.mode=shared`). Dedicated installs and private runners stay in the tree; they are **not** a first-launch gate.

This procedure reuses [windows-vps-setup.md](windows-vps-setup.md), [deployment.md](deployment.md), [backup-restore.md](backup-restore.md), and `start-portal.bat`. It does **not** invent a second deploy path.

**Validation candidate:** `2721e6d` (unchanged). Public rollout stays HOLD until staging isolation, staging restore, authorized live G→E→A, and named signatures pass on this SHA.

## Host (fill when access exists)

| Item | Value |
|------|--------|
| Hostname / public DNS | _missing_ |
| Operator access (RDP/SSH) | _missing_ |
| OS | Windows Server 2022 (same as VPS guide) or equivalent JDK 21 host |
| Store volume | same disk as H2 `portal-db` + `delivery-store/tenants/` |
| TLS terminator | Win-ACME / Caddy in front of `localhost:8080` |

Do not set `delivery.install.private-cidrs`. Shared mode must reject that at production startup.

## One-time software (from the VPS guide)

JDK 21+, Maven 3.9+, Chrome, Ollama (if Generate/Execute will use local models), Git.

Clone or copy **`2721e6d`** to e.g. `C:\apps\TestPilot`.

## Configuration

Copy [examples/application-shared-staging.properties](examples/application-shared-staging.properties) over local overrides (do not commit secrets). Required production flags:

- `delivery.install.mode=shared`
- `delivery.install.production=true` (or Spring profile `prod`)
- Non-default `delivery.admin-password`
- `delivery.public-base-url=https://…`
- `spring.h2.console.enabled=false`
- Explicit `delivery.provider.allowlist`
- Non-blank `spring.datasource.password`
- Empty / unset `delivery.install.private-cidrs`

Start with `start-portal.bat` (or NSSM/WinSW as in the VPS guide). Confirm:

```
GET /api/health  → installMode=shared
GET /api/ready   → storeWritable=true
```

## Remaining first-launch drills on this host

1. **Worker-network isolation** — two jobs on public origins only; private/loopback/link-local/metadata hops rejected. Application-layer + PAC; not a kernel firewall.
2. **Cross-tenant access** — two workspaces; tenant B cannot read tenant A artifacts (404).
3. **Backup/restore** — snapshot `delivery-store/` (DB, artifacts, `delivery.secret`, checksums) on **this** volume; restore; tenant boundaries intact. See [backup-restore.md](backup-restore.md).
4. **Live G→E→A** — Generate → Execute → Automate NEW → downloaded-framework replay → UPDATE → replay against an **authorized** representative application (DOM Execute/Automate; not live UI-TARS click-grounding).

Dedicated CIDR drills and private-runner enrollment are **out of first-launch scope**.
