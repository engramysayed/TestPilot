-- M1-01: additive mobile schema. Applied idempotently by MobileSchemaMigration (and Hibernate ddl-auto=update).
-- projects.platform: WEB | ANDROID; backfill WEB for legacy rows. Immutable after create (enforced in app).
ALTER TABLE projects ADD COLUMN IF NOT EXISTS platform VARCHAR(16) DEFAULT 'WEB';

-- Tables below are created by JPA entities when missing; kept here for DBA visibility on validate/none installs.
-- mobile_apps, mobile_app_revisions, mobile_device_profiles, mobile_devices, mobile_device_leases
