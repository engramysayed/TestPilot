-- B08 (Phase 0a): persisted Runner API v2 fencing tokens. Applied automatically at startup by
-- delivery.portal.db.FencingTokenColumnMigration (idempotent); kept here for DBAs.
ALTER TABLE jobs ADD fencing_token BIGINT;
