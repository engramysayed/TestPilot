# 2026-11-02 — Portal rebuild/restart for GFK HTTP

**Tip jar:** `cfa220e` (keel-portal-1.0-SNAPSHOT.jar after `mvn -pl keel-portal -am -DskipTests package`)
**PID:** 12476 on `:8080` (product profile)
**Start:** detached `start-portal.cmd` (Hidden); logs `_backup/portal-start-20261102.log`
**Verify:** HOME HTTP 200; `/generate-from-knowledge` → 302 (auth redirect expected)
**Note:** BOM warning on llm-settings.json (pre-existing); does not block boot.

FE smoke next; Codegen W1 + Phase 4 in parallel.
