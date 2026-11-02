# Handoff — prompts Owner UI Backend (Slice B) (2026-11-02)

**Status:** GREEN (Backend DoD)  
**Specialist:** Keel Backend  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`  
**Kickoff:** `docs/handoff/2026-11-02-prompts-owner-ui-kickoff.md`  
**Base tip:** `301a3fc`  
**Tip SHA:** see `git log -1` after Backend commit (recorded in Maestro/Leader report)  
**Backup:** `_backup/prompts-owner-ui-20261102-2252/`  
**Portal LIVE:** left PID `17288` on `:8080` (jar not refreshed — Maestro owns after FE)

## API contract (final)

Auth: `roles.requireHeadAdmin(currentUser.requireUser())` — same gate as `/api/admin/llm-settings`.

| Method | Path | Response / notes |
|--------|------|------------------|
| GET | `/api/admin/prompts` | `{ items:[{id,source}], promptsVersion, overrideDirConfigured }` |
| GET | `/api/admin/prompts/{id}` | id may contain `/` (e.g. `heal/pick`). `{ id, source, text, classpathText }` |
| PUT | `/api/admin/prompts/{id}` | body `{ text }` writes `<keel.prompts.dir>/<id>.md` (keeps `.txt` if already). **400** `{ error:"PROMPTS_DIR_UNSET", message }` if unset |
| GET | `/api/admin/prompts/{id}/diff` | `{ id, source, identical, unified, lines:[{op,classpath,override}] }` |

`source` = `override` iff override file exists for id, else `classpath`.  
When override unset: list/get still work from classpath; save returns 400 explaining `keel.prompts.dir` / `KEEL_PROMPTS_DIR`.

## VERSION + stamp

- Classpath pack file: `src/main/resources/prompts/VERSION` = `2026.11.02`
- `PromptCatalog.version()` — override `VERSION` first, then classpath
- **Evidence field name:** `promptsVersion`
- **Generate stamp locations:** `GeneratedWorkbookService` → `latest-meta.json` (both writers that emit `excelFile`)
- **Heal:** AE-owned (`delivery.heal.HealProposalWriter`, `delivery.evidence.FailureReplayWriter`). Backend did not invent a second evidence format. **AE ping needed** via Maestro to stamp `promptsVersion` on heal proposal JSON and/or failure `meta.json`.

## Verify

```
mvn -q -pl keel-portal -Dtest=delivery.prompts.PromptCatalogTest,delivery.prompts.PromptCatalogVersionTest,delivery.portal.service.AdminPromptsServiceTest,delivery.portal.api.AdminPromptsApiTest test
```

**Result:** GREEN (exit 0)

## Paths changed

- `src/main/java/delivery/prompts/PromptCatalog.java` (`version`, `tryClasspathText`, `tryOverrideText`, `hasOverrideFile`, public `normalizeId`)
- `src/main/java/delivery/portal/service/AdminPromptsService.java` (new)
- `src/main/java/delivery/portal/api/AdminPromptsController.java` (new)
- `src/main/java/delivery/portal/service/GeneratedWorkbookService.java` (`promptsVersion` stamp)
- `src/main/resources/prompts/VERSION` (new)
- `src/test/java/delivery/prompts/PromptCatalogVersionTest.java` (new)
- `src/test/java/delivery/portal/service/AdminPromptsServiceTest.java` (new)
- `src/test/java/delivery/portal/api/AdminPromptsApiTest.java` (new)
- `docs/handoff/2026-11-02-prompts-owner-ui-backend.md`

## Next

- FE wires `admin-prompts.html` to these endpoints
- Maestro: jar refresh after FE; smoke admin prompts + pricing
- Maestro → AE: stamp `promptsVersion` on heal evidence writers

## Out of scope

FE templates; hot-reload beyond existing override re-read; wholesale prompt rewrites; push; Slice A debit.
