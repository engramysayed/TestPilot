# Phase 8 — Execute tunnel copy (2026-11-16)

## Shipped

- **`TunnelProjectStatusService`** — user-facing badge `label` + `hint` (no JVM property names in labels).
- **`execute.html` / `execute.js`** — badge + **Private app tunnel →** guide link; hint line when not active.
- **`keel-portal-enhancements.css`** — tunnel row layout.

## Verify

1. Execute with tunnel disabled on server → **Tunnel inactive** + guide link + admin hint (no `delivery.tunnel.enabled`).
2. Tunnel enrolled and heartbeating → **Tunnel active** (green badge); guide link hidden.

## Next

Phase 9 — Run memory table header + Bug Hunter polish.