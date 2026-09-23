# Backup and restore

Live deletion (tombstone → purge) is not a backup. Keep store files and the portal database on a backup schedule that matches retention (`delivery.retention.days`).

## What to copy

1. `delivery-store/` including `tenants/`, `portal-db.mv.db` (H2), library `revisions/`, and version checksums.
2. Do **not** treat `delivery-work/` as durable evidence.
3. Record the git revision of the bits that produced the artifacts.

`delivery.store.StoreBackup` snapshots a store directory and restores it without rewriting tenant ids. A local unit test (`StoreBackupTest`) checks tenant path + SHA-256 identity after wipe/restore.

## Drill (required for first-launch P4-02 close)

On **shared staging**, not this developer workstation (see [shared-staging.md](shared-staging.md)):

1. Install the documented **shared** profile (`delivery.install.mode=shared`, no `private-cidrs`).
2. Create two tenants targeting the same host with distinct sentinels.
3. Snapshot store + DB.
4. Upgrade or wipe the live store.
5. Restore.
6. Confirm artifact checksums, job download binding, and that tenant A cannot read tenant B.

Until that drill is recorded against candidate `2721e6d` on shared staging, **backup restoration remains a first-launch blocker**. Dedicated-host restore is deferred.

## Rollback

- Failed layout migration: `TenantLayoutMigrator.revert` using the migrator manifest.
- Failed package publish: previous ZIP remains; jobs do not fall back to another job's latest ZIP.
