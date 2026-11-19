-- B09: persist live job progress history across JVM restarts.
-- Bounded JSON array of {ts, message} entries written by JobRecord.setMessage, serialized at
-- every PortalStore.syncJobPersistence (so the log survives the inevitable restart mid-run).
-- Applied automatically by Hibernate ddl-auto=update; kept here for DBAs on stricter configs.
ALTER TABLE jobs ADD COLUMN progress_log TEXT;
