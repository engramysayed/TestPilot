# Portal refresh - credit add-ons confirm LIVE (2026-11-02)

## Summary
Replaced keel-portal on Ramy so **confirmed credit add-on covers** (no Draft) from tip `4dd70e0` plus **FE pricing/guide copy** on disk land LIVE.

## Ops
| Item | Value |
|------|-------|
| Machine | Ramy (`96a3c81a-96a9-490d-a835-f6bf263f986b`) |
| Live tree | `D:\Work\projects\Keel\keel\TestPilot` |
| Tip | `4dd70e0` (`4dd70e0e88d0e5dfa827a8858fb9a30a202696cc`) |
| Tip subject | `feat(portal): confirm credit addon covers remove draft` |
| Previous PID | **11968** (stopped before package to free jar lock) |
| New PID | **4032** |
| Port | `:8080` |
| Start | `start-portal.cmd` → `java -jar keel-portal\target\keel-portal-1.0-SNAPSHOT.jar --server.port=8080` |
| Jar path | `D:\Work\projects\Keel\keel\TestPilot\keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` |
| Jar size | ~107,892,379 bytes |

## Pre-checks
- `git rev-parse --short HEAD` → `4dd70e0` (expected).
- `pricing.html` on disk: **0** `Draft` matches; confirmed add-on note for `VISUAL_DIFF` / `SUITES_MONITOR` / `HEAL_REVIEW` as normal rows.
- `guide.html` on disk: confirmed (not draft/provisional) credit callouts for heal / visual / suites.
- Working tree still has uncommitted FE templates and other local changes; packaged as-on-disk (no new commit).

## Build
```text
mvn -pl keel-portal -am "-Dmaven.test.skip=true" package
```
- Stopped PID **11968** first (jar was holding `:8080`).
- **BUILD SUCCESS** (reactor: keel-parent → keel-contract → mobile-contracts → keel-portal; ~11.6s).
- Did not use `rebuild-portal.cmd` (unquoted `-D` props); used quoted Maven props per rule.

## Jar verification
Present under BOOT-INF:
- `BOOT-INF/classes/delivery/portal/service/credits/ServicePriceService.class`
- `BOOT-INF/classes/templates/pricing.html` — **0** `Draft` matches; confirmed add-on copy present
- `BOOT-INF/classes/templates/guide.html`

## Smoke
- `GET http://localhost:8080/pricing` → **302** → `http://localhost:8080/login` (OK)

## Constraints honored
- No push / no force-push / no merge to remote main
- No new commit (left tip `4dd70e0` + working-tree FE as packaged)
- Single Maven reactor; portal stopped before package
- Quoted `-D` Maven props

## What's next
- Maestro/Leader: authenticated smoke of `/pricing` + Admin → Prices if needed (seeded add-on rows as normal, no Draft badge).
