# Portal refresh — Slice B prompts Owner UI LIVE

**When:** 2026-11-02 ~23:03 Africa/Cairo (UTC+2)  
**Machine:** Ramy (96a3c81a-96a9-490d-a835-f6bf263f986b)  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`  
**Actor:** executor (portal jar refresh)

## Goal

Refresh Keel portal jar so Slice B prompts Owner UI is LIVE:
- `admin-prompts.html` (Owner UI FE)
- Admin prompts API (`AdminPromptsController` / `AdminPromptsService`)
- `promptsVersion` stamps (`HealProposalWriter` + `prompts/VERSION`)

## Tip

- HEAD: `780b112` — `docs(handoff): record AE promptsVersion heal stamp tip SHA`
- Feature tips referenced: `4685bcf` + `05c7dc8` + `34bab54` (AE docs tip expected `780b112`)

## Ops

| Item | Value |
|------|--------|
| Stopped PID | `17288` (java on :8080) |
| New PID | `20332` (java -jar keel-portal) |
| Port | `8080` |
| Jar | `D:\Work\projects\Keel\keel\TestPilot\keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` |
| Start | `start-portal.cmd` (SPRING_PROFILES_ACTIVE=product) |
| Maven | `mvn -pl keel-portal -am "-Dmaven.test.skip=true" package` — BUILD SUCCESS (~12s) |

## Jar contains (verified)

- `BOOT-INF/classes/templates/admin-prompts.html`
- `BOOT-INF/classes/delivery/portal/service/AdminPromptsService.class`
- `BOOT-INF/classes/prompts/VERSION` (source content `2026.11.02`)

## Smoke

| URL | Result |
|-----|--------|
| `GET http://localhost:8080/admin/prompts` | **302** → `/login` |
| `GET http://localhost:8080/pricing` | **302** → `/login` |
| Follow redirect once | **200** on `/login` |

## Source stamps confirmed on disk before package

- `src/main/resources/templates/admin-prompts.html`
- `src/main/java/delivery/portal/api/AdminPromptsController.java`
- `src/main/java/delivery/portal/service/AdminPromptsService.java`
- `src/main/java/delivery/heal/HealProposalWriter.java` — `o.put("promptsVersion", PromptCatalog.version());`
- `src/main/resources/prompts/VERSION` → `2026.11.02`

## Git

- Handoff file written **uncommitted** (no commit / no push).

## Next

- Owner can sign in and exercise `/admin/prompts` UI + Admin prompts API.
- No further jar rebuild needed for this slice unless tip moves.

