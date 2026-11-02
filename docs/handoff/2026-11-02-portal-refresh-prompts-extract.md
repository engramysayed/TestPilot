# Portal refresh — prompts extract LIVE (2026-11-02)

## Summary
Replaced keel-portal on Ramy so **PromptCatalog**, **resources/prompts**, and **PromptsDirBinder** are live from tip `294291a`.

## Ops
| Item | Value |
|------|-------|
| Machine | Ramy (`96a3c81a-96a9-490d-a835-f6bf263f986b`) |
| Live tree | `D:\Work\projects\Keel\keel\TestPilot` |
| Tip | `294291a` (`294291ac32c00af4a0f172630e9b0a91cb0e607e`) |
| Tip chain | `294291a` → `6731567` → `35b1aab` |
| Previous PID | **22104** (stopped; jar lock blocked Spring Boot repackage until kill) |
| New PID | **11968** |
| Port | `:8080` |
| Start | `start-portal.cmd` → `java -jar keel-portal\target\keel-portal-1.0-SNAPSHOT.jar --server.port=8080` |
| Jar path | `D:\Work\projects\Keel\keel\TestPilot\keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` |

## Build
```text
mvn -pl keel-portal -am "-Dmaven.test.skip=true" package
```
- First attempt: FAILURE on `spring-boot:repackage` (unable to rename jar while PID 22104 held it).
- Stopped 22104, retry → **BUILD SUCCESS**.

## Jar verification
Present under BOOT-INF:
- `BOOT-INF/classes/delivery/prompts/PromptCatalog.class`
- `BOOT-INF/classes/delivery/portal/PromptsDirBinder.class`
- `BOOT-INF/classes/prompts/heal/pick.md`
- Plus authoring / codegen / grounding / heal / hunt / revise / vision prompt trees.

## Smoke
- `GET http://localhost:8080/guide` → **302** → `http://localhost:8080/login` (OK)

## Constraints honored
- No push / no commit
- Single Maven reactor
- Prefer `start-portal.cmd`

## What’s next
- Maestro/Leader: confirm product smoke beyond `/guide` if needed (authoring-review / heal pick paths).
