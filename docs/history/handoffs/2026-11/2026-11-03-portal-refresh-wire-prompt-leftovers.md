# Portal refresh - Slice D wire prompt leftovers LIVE (2026-11-03)

## Summary
Replaced keel-portal on Ramy so Slice D leftover AI prompts (`hunt/bug-triage`, `planner/llm-planner`, plus already-on-disk vision/* wire-only) are LIVE. Boot inventory now reports **classpathIdCount=29** (was 27 after Slice C).

## Ops
| Item | Value |
|------|-------|
| Machine | Ramy (`96a3c81a-96a9-490d-a835-f6bf263f986b`) |
| Live tree | `D:\Work\projects\Keel\keel\TestPilot` |
| HEAD | `b1f07bf` (`b1f07bf3f1443703a223adc5afaea81d67686454`) - `docs(handoff): record Slice D AE tip 3c387e0` |
| Feature tip | `3c387e0` (`3c387e0`) - `feat(ae): wire leftover AI prompts onto PromptCatalog` |
| Prior docs tip | `cc04115` (Slice C) — still ancestor |
| Previous PID | **9044** (stopped so jar unlock for repackage) |
| New PID | **24000** |
| Port | `:8080` |
| Profile | `product` (via `start-portal.cmd`) |
| Start | `start-portal.cmd` → `java -jar keel-portal\target\keel-portal-1.0-SNAPSHOT.jar --server.port=8080` |
| Jar path | `D:\Work\projects\Keel\keel\TestPilot\keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` |
| Boot log | `_backup\portal-start-20261103-wire-prompt-leftovers.log` |

## Build
```text
mvn -pl keel-portal -am "-Dmaven.test.skip=true" package
```
- Stopped PID 9044 first (would lock jar on spring-boot:repackage).
- Single reactor — **BUILD SUCCESS** (Africa/Cairo ~02:56:28). Total ~11.3s.

## Source / jar verification
On disk under `src/main/resources/prompts/`:
- `hunt/bug-triage.md` (new)
- `planner/llm-planner.md` (new)
- `vision/*` already present (design-compare, qwen, ui-tars*, vision-few-shot, vision-template)

Present under BOOT-INF in the fat jar:
- `BOOT-INF/classes/delivery/portal/PromptsBootInventoryRunner.class` (+ `$InventoryEntry`)
- `BOOT-INF/classes/prompts/VERSION`
- `BOOT-INF/classes/prompts/hunt/bug-triage.md`
- `BOOT-INF/classes/prompts/planner/llm-planner.md`
- `BOOT-INF/classes/prompts/vision/*.md` (8 files)

## Boot inventory (from portal log)
- Summary: `promptsVersion=2026.11.02 overrideConfigured=false classpathIdCount=29`
- New ids vs Slice C (27→29): `hunt/bug-triage`, `planner/llm-planner`
- Per-id lines emitted for all 29 classpath ids (authoring / codegen / grounding / heal / hunt / planner / revise / vision).
- Timestamp (Africa/Cairo): `03-11-2026 02:56:59`

## Smoke
| Request | Result |
|---------|--------|
| `GET http://localhost:8080/pricing` | **302** → `http://localhost:8080/login` |
| `GET http://localhost:8080/login` | **200** |

## Constraints honored
- No push / no commit (this handoff left uncommitted OK)
- Did not stage `application.properties` / `_backup` / `_scratch`
- Single Maven reactor; Maven free afterward
- Prefer `start-portal.cmd`; killed only portal java on :8080 (PID 9044)

## What's next
- Maestro/Leader: FE badge / heal.mjs / any remaining Slice D follow-ups remain out of scope for this refresh.
