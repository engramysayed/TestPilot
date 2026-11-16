# Portal jar refresh — GFK ingest + VD sidebar (2026-11-02)

## What
Repackaged + restarted portal so live jar includes FE polish on tip `83b9077` (no push):
1. GFK Add pack ingest strip (`generate-from-knowledge.html` library / upload-tc / upload-md)
2. Sidebar Visual diffs → `/execute#visual-diffs-panel` (`fragments.html`)

FE backup: `_backup\backup-pre-gfk-ingest-vd-sidebar-20261102-183236`

## Ops
- Tip: `83b9077` (ahead of keel/main by 8; dirty working tree as before)
- Maven: `mvn -pl keel-portal -am -Dmaven.compiler.release=21 -Dmaven.test.skip=true package` (serialized; prior portal PID 20648 stopped first)
- LIVE: http://127.0.0.1:8080 — java PID **13340** product profile
- Jar verified: BOOT-INF templates contain `ingest-strip` / Add pack tabs and `/execute#visual-diffs-panel` link
- HOME/GFK HTTP 302 → login (expected)

## Verify
Ctrl+F5 on `/generate-from-knowledge` (Add pack strip) and sidebar Visual diffs → execute panel.

## Next
Hold new wave until Leader relays user pick (W2 / P2 / P3 / Sonic pause).
