# Portal refresh - Guide + Pricing FE + ServicePrice (2026-11-02)

**HEAD tip:** `fc3faba` (`fc3faba344961687dc3247577c5572fe096c8b64`) — docs(portal): guide pricing backend handoff tip SHA
**Backend tip note:** ServicePrice covers + DRAFT add-ons VISUAL_DIFF=5, SUITES_MONITOR=10, HEAL_REVIEW=5 (Backend GREEN ~857a8fa / HEAD docs fc3faba)
**FE handoff:** `docs/history/handoffs/2026-11/2026-11-02-guide-pricing-fe.md`
**Backend handoff:** `docs/history/handoffs/2026-11/2026-11-02-guide-pricing-backend.md`

## Verify (working tree packaged; dirty uncommitted templates)
- `src/main/resources/templates/guide.html` — `#suites` nav + `id="suites"` section present (M dirty)
- `src/main/resources/templates/pricing.html` — Draft badge CSS/rows; `There is no dollar / USD pricing on this page.` (M dirty)
- Jar BOOT-INF templates match; `ServicePrice*.class` present in jar

## Ops
- Prior LIVE :8080 PID **11708** (Phase 3) stopped (jar lock blocked first repackage)
- Backup: `keel-portal\target\keel-portal-1.0-SNAPSHOT.jar.bak-pre-guide-pricing-20261102-212334`
- `mvn -pl keel-portal -am "-Dmaven.test.skip=true" package` SUCCESS (PowerShell needs quoted `-D`)
- Jar: `D:\Work\projects\Keel\keel\TestPilot\keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` (107873673 bytes)
- Started via `start-portal.cmd`
- LIVE :8080 PID **22104** (`java -jar ...keel-portal-1.0-SNAPSHOT.jar --server.port=8080`)
- Smoke: `GET /guide` → 302 `/login`; `GET /pricing` → 302 `/login` (routes exist)
- No git push/commit

## Next
Ctrl+F5 `/guide` (Suites section) and `/pricing` (credits-only + Draft badges when Backend seeds VISUAL_DIFF / SUITES_MONITOR / HEAL_REVIEW). Owner can tweak draft credits on Admin → Prices.
