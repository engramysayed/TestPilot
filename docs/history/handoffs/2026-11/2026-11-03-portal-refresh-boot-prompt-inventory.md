# Portal refresh - boot prompt inventory LIVE (2026-11-03)

## Summary
Replaced keel-portal on Ramy so **PromptsBootInventoryRunner** (Slice C) is LIVE from docs tip `cc04115` (feature tip `c1cf3ea`).

## Ops
| Item | Value |
|------|-------|
| Machine | Ramy (`96a3c81a-96a9-490d-a835-f6bf263f986b`) |
| Live tree | `D:\Work\projects\Keel\keel\TestPilot` |
| HEAD | `cc04115` (`cc041152179eaa2c3519262d6a1f60207191a3d6`) — `docs(handoff): record Slice C tip c1cf3ea` |
| Feature tip | `c1cf3ea` (`c1cf3ea1fc7ba5198ef82e5ba11ca06b37ffca4c`) — `feat(portal): boot prompt inventory and override fail-fast` |
| Previous PID | **20332** (stopped so jar unlock for repackage) |
| New PID | **9044** |
| Port | `:8080` |
| Profile | `product` |
| Start | `start-portal.cmd` → `java -jar keel-portal\target\keel-portal-1.0-SNAPSHOT.jar --server.port=8080` |
| Jar path | `D:\Work\projects\Keel\keel\TestPilot\keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` |
| Boot log | `_backup\portal-start-20261103-boot-prompt-inventory.log` |

## Build
```text
mvn -pl keel-portal -am "-Dmaven.test.skip=true" package
```
- Stopped PID 20332 first (would lock jar on spring-boot:repackage).
- Single reactor — **BUILD SUCCESS** (Africa/Cairo ~02:43:48). Total ~12s.

## Jar verification
Present under BOOT-INF:
- `BOOT-INF/classes/delivery/portal/PromptsBootInventoryRunner.class`
- `BOOT-INF/classes/delivery/portal/PromptsBootInventoryRunner$InventoryEntry.class`
- `BOOT-INF/classes/prompts/VERSION`

## Boot inventory (from portal log)
- Summary: `promptsVersion=2026.11.02 overrideConfigured=false classpathIdCount=27`
- Per-id lines emitted for all 27 classpath ids (authoring / codegen / grounding / heal / hunt / revise / vision).
- Pre-existing WARN: `llm-settings.json` BOM (0xfeff) — does not block boot.

## Smoke
| Request | Result |
|---------|--------|
| `GET http://localhost:8080/pricing` | **302** → `http://localhost:8080/login` |
| `GET http://localhost:8080/login` | **200** |

## Constraints honored
- No push / no commit (handoff left uncommitted OK)
- Did not stage `application.properties` / `_backup` / `_scratch`
- Single Maven reactor; Maven free afterward
- Prefer `start-portal.cmd`; killed only portal java on :8080

## What's next
- Maestro/Leader: Slice D leftovers / FE badge / heal.mjs remain out of scope for this refresh.
