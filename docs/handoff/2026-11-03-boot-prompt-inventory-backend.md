# Handoff — Slice C: Boot prompt inventory + fail-fast (Backend)

**Status:** GREEN  
**Date:** 2026-11-03 (Africa/Cairo)  
**Specialist:** Keel Backend  
**Base tip:** `cc502f3` (`docs(handoff): note final tip df83752 after push-slice handoff commit`)  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (Ramy `96a3c81a-96a9-490d-a835-f6bf263f986b`)  
**Branch:** `main` (tracks `keel/main`)  
**No push.** Portal LIVE PID **20332** on `:8080` left untouched (no package/restart).

---

## What changed

| Path | Change |
|------|--------|
| `src/main/java/delivery/portal/PromptsBootInventoryRunner.java` | **NEW** `ApplicationRunner`: scan classpath ids via `AdminPromptsService.classpathIds()`, log `promptsVersion` + per-id `source` + short sha256 (12 hex of UTF-8 resolved body), fail-fast `IllegalStateException` when override dir set but any known id missing `.md`/`.txt` |
| `src/main/java/delivery/portal/service/AdminPromptsService.java` | `classpathIds()` visibility → **public** (reuse for boot scan) |
| `src/test/java/delivery/portal/PromptsBootInventoryRunnerTest.java` | **NEW** TestNG: unset override happy path (27 golden fence), complete override, incomplete fail-fast (`heal/pick`), stable sha256 |
| `docs/handoff/2026-11-03-boot-prompt-inventory-backend.md` | This handoff |

**Backup (pre-edit):** `_backup/boot-prompt-inventory-20261103-0237/` (`AdminPromptsService.java`, `PromptsDirBinder.java`)

**Not touched:** `application.properties`, secrets, META-INF, BOOT-INF, portal DB, FE, Slice D leftovers, `PromptCatalog` (no bloat), portal jar / restart.

---

## Behavior

1. After `PromptsDirBinder` `@PostConstruct` (constructor inject of binder), runner inventories every classpath prompt id (~27; excludes `VERSION`).
2. Summary log: `promptsVersion`, `overrideConfigured`, `classpathIdCount`.
3. Per id: `id=… source=classpath|override sha256=…` (resolved body: override if present else classpath).
4. Override **set** + any known id missing override file → abort startup with missing id list.
5. Override **unset** → classpath inventory only; boot succeeds.

Known scan count at tip: **27** (matches golden fence in test). `promptsVersion` = `2026.11.02`.

---

## Verify

```bat
cd /d D:\Work\projects\Keel\keel\TestPilot
mvn -B -pl keel-portal -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PromptsBootInventoryRunnerTest,PromptCatalogTest,PromptCatalogVersionTest,AdminPromptsServiceTest test
```

**Result (Africa/Cairo 2026-11-03 ~02:39):** `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.

---

## Commit

Selective commit of Slice C Backend files only (foreign WIP left alone). Subject:

`feat(portal): boot prompt inventory and override fail-fast`

Tip SHA: c1cf3ea — feat(portal): boot prompt inventory and override fail-fast

---

## Ops / next

- Portal PID 20332 still live; inventory takes effect on **next** portal start (Maestro/Leader schedules refresh — not this slice).
- Slice D leftovers migration / FE badge / heal.mjs remain out of scope.
- No push from this specialist.
