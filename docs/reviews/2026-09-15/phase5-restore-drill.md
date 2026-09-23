# Workstation restore drill (candidate `23f9351`)

**Date:** 2026-09-17T20:21+03:00  
**Code revision:** `23f9351`  
**Scope:** this workstation's `D:\priv\testpilot\TestPilot\delivery-store` (H2 file DB + tenant artifacts + `delivery.secret`). **Not** shared-staging storage (P4-02 first-launch drill still open). Dedicated-host restore is deferred.

A leftover Phase 6 dry-run portal (PID 5136, port 8081, dedicated `127.0.0.0/8`) held `portal-db.lock.db`. A hot copy failed (`The process cannot access the file because another process has locked a portion of the file`). That portal was stopped, then a consistent copy was taken. It was **not** restarted. Public rollout stays HOLD.

## What was copied

| Item | Result |
|------|--------|
| H2 database `portal-db.mv.db` | SHA-256 match after restore |
| Artifacts under `tenants/ws_…` and leftover `opssit-axispay-app` | 699 regular files, 0 mismatches |
| Encryption key `delivery.secret` | SHA-256 match; AES-GCM encrypt/decrypt round-trip of `restore-canary` using restored store-root |
| Checksums | SHA-256 of every copied file compared source vs restored |
| Tenant boundaries | `ws_f892159691224fe4834a67d6a4e15853` and `ws_f91830228c05437fbe12e3ec9f3d047c` remain distinct; 0 cross-tenant path leaks |

Lock files `*.lock.db` were excluded (H2 runtime locks, not backup payload). Snapshot/restore trees live under `target/phase5-restore-drill/` (gitignored). Manifest: `target/phase5-restore-drill/checksums.txt`.

```
delivery.secret.sha256=34d9e5c32669aed0ece2589c675e867875f11a5dca4e24bf94c8d5785cbb768f
portal-db.mv.db.sha256=24eaac7a715b71a42ff75b8bdef55eddd5b73130243cc0600c8c245c74882214
fileCount=699
mismatches=0
tenantBoundaryIntact=true
encryptionRoundTrip=true
```

Isolated shared/dedicated **test** store-roots were separately snapshot/restored inside `SharedInstallDrillTest` / `DedicatedInstallDrillTest` on the clean `23f9351` worktree.

## Still open

Restore onto the FileStore of **shared staging**, with the operator backup destination, remains a first-launch blocker. Dedicated-host restore is deferred.
