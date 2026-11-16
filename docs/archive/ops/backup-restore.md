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

## H2 after issue #2 (no AUTO_SERVER)

- The portal no longer opens an H2 TCP server (`AUTO_SERVER` removed), so **only the portal process can open `portal-db`**. A second process (H2 console, DBeaver, a script, or a second portal) fails with "Database may be already in use" while the portal runs.
- For backups, either stop the portal and copy `portal-db.mv.db`, or (while it runs) take an online copy from inside the portal connection with `BACKUP TO 'path.zip'`. Don't hot-copy the file.
- The password comes from `KEEL_DB_PASSWORD`, set as an environment variable or in an external `application-*.properties` file that isn't committed. Under the `prod` profile (or with `delivery.install.production=true`), the portal refuses to start when it's empty. In dev, it logs a warning.
- Existing DBs that still have the old empty `sa` password are migrated once at startup (`ALTER USER SA SET PASSWORD …`). This only happens when the configured password fails and an empty password succeeds; otherwise nothing is changed. Turn it off with `keel.db.migrate-empty-password=false`. A restored backup taken before the migration still has the empty password and is migrated again on its first start.
- To open a DB with an external tool, stop the portal and use `jdbc:h2:file:<path>/portal-db` with user `sa` and `KEEL_DB_PASSWORD`.
