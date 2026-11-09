# Kickoff — Slice C: Boot prompt inventory + fail-fast (2026-11-03)

**Status:** KICKOFF ONLY — do not implement until Maestro assigns Backend (primary).  
**Base tip:** `cc502f35006e4dc078345bf7df6d5804bd3e3408` (`docs(handoff): note final tip df83752 after push-slice handoff commit`)  
**Ancestry:** includes Slice B prompts Owner UI tip `780b112` (verified ancestor).  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (Ramy `96a3c81a-96a9-490d-a835-f6bf263f986b`)  
**Branch:** `main` (tracks `keel/main`)  
**Constraints:** **no push / force-push / merge**; **do not restart portal**; **serialize Maven** (push may be finishing — avoid `package` / full test reactors that fight other work); do **not** edit `application.properties` or touch secrets. Commit optional; uncommitted kickoff/docs OK.

**Aligns with:** prompts-extract GREEN (`PromptCatalog` + `PromptsDirBinder` + `resources/prompts/` + Owner admin inventory). Maestro follow-on after extract: boot-time resolve **every known prompt id**, log source + short hash, **fail fast** when override dir is configured but incomplete.

---

## Goal

On portal startup, after override binding:

1. Resolve **every known prompt id** (classpath catalog set).
2. Log for each: `id`, `source` (`classpath` | `override`), short **sha256** (first 12 hex of UTF-8 body).
3. If `keel.prompts.dir` / `KEEL_PROMPTS_DIR` / `PromptCatalog.setOverrideDir` is set (override configured) but the override tree is **incomplete** (any known classpath id missing `.md`/`.txt` under override) → **fail fast** (abort startup with clear exception listing missing ids).
4. When override is **unset**: inventory classpath only; do **not** fail for missing override files.
5. Handoff + focused tests. **No FE** unless Maestro later justifies a tiny admin boot-status badge (default **NO FE** — Owner `/admin/prompts` already lists ids/sources).

---

## Owners

| Role | Scope |
|------|--------|
| **Backend (PRIMARY)** | Boot runner, inventory/fail-fast, tests, handoff |
| **AE** | Only if inventory API needs PromptCatalog helpers beyond AdminPromptsService scan, or heal/VERSION stamp path docs need a one-line ack — **not** primary |
| **FE** | **Default NO.** Clear need only: optional read-only “last boot inventory OK / promptsVersion” badge — out of scope unless Maestro reopens |

Do **not** self-assign specialists from this kickoff; Maestro assigns.

---

## How it works today (inventory ground truth)

| Piece | Location / contract |
|-------|---------------------|
| Catalog | `delivery.prompts.PromptCatalog` — `get` / `format` / `version` / `tryClasspathText` / `tryOverrideText` / `hasOverrideFile` / `setOverrideDir` / `overrideDir` |
| Resolve order | programmatic `setOverrideDir` → sysprop `keel.prompts.dir` (`PROP_DIR`) → env `KEEL_PROMPTS_DIR` (`ENV_DIR`) → classpath `prompts/<id>.md\|.txt` |
| Fail-closed runtime | When override dir set, missing override **file** throws on `get` (no silent classpath fallback) |
| Spring bind | `delivery.portal.PromptsDirBinder` `@PostConstruct bindOverrideDir()` — `@Value("${keel.prompts.dir:}")` → `setOverrideDir` + sysprop |
| Config key | `keel.prompts.dir=` in `application.properties` (leave blank = classpath). **Do not edit properties in this slice.** |
| Admin list | `delivery.portal.service.AdminPromptsService` — `classpathIds()` via `classpath*:prompts/**/*.*`, `listPrompts()` rows with `source` classpath\|override, `promptsVersion` |
| VERSION | `src/main/resources/prompts/VERSION` → `2026.11.02` (`PromptCatalog.version()`) |

**No boot inventory runner exists yet.** Closest fail-fast pattern: `delivery.portal.security.ProductionSafetyRunner` (`ApplicationRunner`, `@Order(HIGHEST_PRECEDENCE)`).

---

## Known prompt ids (N=27) + VERSION

Source of truth for Slice C: classpath under `src/main/resources/prompts/` (exclude `VERSION` from id set; still log version string).

```
authoring/authoring-review
authoring/authoring-review-cursor
authoring/authoring-service
authoring/heal-pick-payload
codegen/test-method-naming
grounding/free-invent
grounding/hunt-planner
grounding/locator-policy
grounding/login-prelude
grounding/vision-heal
heal/ground-rank
heal/ground-rank-batch
heal/hunt-cursor
heal/invent
heal/pick
heal/safety
heal/solve
hunt/ollama-system
revise/final-revise
vision/design-compare
vision/qwen
vision/qwen-assert-retry
vision/ui-tars
vision/ui-tars-assert
vision/ui-tars-assert-retry
vision/vision-few-shot
vision/vision-template
```

Plus pack version file: `prompts/VERSION` → log `promptsVersion=…` (not an id).

Prefer **scanning** classpath (reuse / expose `AdminPromptsService.classpathIds()` or add `PromptCatalog`-adjacent helper) over hardcoding the list, so new extract files are covered automatically. Hardcoded golden list in a unit test is OK as a regression fence matching the 27 above at tip.

---

## Boot hook recommendation

**Recommended:** new `delivery.portal.PromptsBootInventoryRunner` implements `ApplicationRunner`.

- Inject `PromptsDirBinder` (or `@DependsOn`) so bind `@PostConstruct` has already run.
- `@Order` after binder / after safety is fine; binder is PostConstruct so any runner sees override already set.
- Method: `run(ApplicationArguments)` → inventory + optional fail-fast.
- Logging: one summary line (`promptsVersion`, override configured yes/no, count) + one line per id (`id=… source=… sha256=…`).
- SHA: `MessageDigest.getInstance("SHA-256")` over UTF-8 bytes of **resolved** body (`tryOverrideText` if present else classpath / or `get` when safe). Short = first 12 hex chars.
- Fail-fast predicate: `PromptCatalog.overrideDir().isPresent()` **and** any known classpath id lacks override `.md`/`.txt` → throw `IllegalStateException` with missing id list (abort Spring boot). Also fail if override path is set but directory is not usable (optional: missing dir = incomplete).
- Unset override: log classpath inventory only; startup continues.

**Do not** fold inventory into `PromptsDirBinder` long-term (keep bind vs inventory separate); runner may call a package-visible method on `AdminPromptsService` for id enumeration.

**Alternate (rejected as primary):** only extend `PromptsDirBinder` — mixes concerns; harder to unit-test fail-fast without Spring bind.

---

## Acceptance criteria

1. Fresh portal start logs inventory for all known classpath prompt ids + `promptsVersion`.
2. Each line includes source + short sha256.
3. With override dir configured and **one** known id file removed → process **fails to start** with missing id named.
4. With override **unset** → starts; classpath inventory logged; no false fail.
5. Focused Surefire tests GREEN (no full reactor required for slice sign-off if Maven contention — serialize).
6. Handoff `docs/handoff/2026-11-03-boot-prompt-inventory.md` (specialist) lists tip SHA, class/method, how to verify.
7. **No push.** No portal restart by specialist unless Maestro/Leader asks after GREEN.
8. `application.properties` / secrets untouched.

---

## Files to touch (expected)

| Path | Change |
|------|--------|
| `src/main/java/delivery/portal/PromptsBootInventoryRunner.java` | **NEW** ApplicationRunner |
| `src/main/java/delivery/portal/service/AdminPromptsService.java` | Possibly expose `classpathIds()` / shared inventory helper (package → public or package-friend) |
| Optional: `delivery.prompts.PromptCatalog` | Only if sha/helpers belong on catalog — prefer not bloating catalog unless AE agrees |
| `src/test/java/delivery/portal/…` | Inventory + fail-fast unit/IT tests (temp override dir) |
| `docs/handoff/2026-11-03-boot-prompt-inventory.md` | Specialist handoff after GREEN |

---

## Test plan

```text
# Serialize Maven; do not overlap portal package with other reactors
mvn -B -pl keel-portal -am -Dsurefire.failIfNoSpecifiedTests=false `
  "-Dtest=PromptsBootInventoryRunnerTest,PromptCatalogTest,PromptCatalogVersionTest,AdminPromptsServiceTest" test
```

Suggested cases:

- Override unset → inventory returns 27 ids; no exception.
- Override complete mirror → all sources `override`; startup path OK.
- Override missing one id → fail-fast with that id in message.
- sha256 stable for fixed temp file bytes.

Do **not** run portal `package` or restart LIVE :8080 in this slice unless Maestro schedules refresh after GREEN.

---

## Out of scope

- Slice D leftover wiring (separate kickoff; may consume this id list).
- Prompt wording changes.
- FE admin badge.
- Push / merge / force-push.
- Editing `application.properties` or secrets.
- Moving anything under `static/prompts`.

---

## Dirty-tree note (ops)

At kickoff write time, working tree had local WIP: modified `application.properties`, `admin-prompts.html`, FE handoff; untracked `BOOT-INF/`, `_backup/`, `_scratch/`, `integration/`, some older kickoffs. Specialists: leave those alone; do not revert or commit foreign WIP.

---

## Next

Maestro assigns Backend; AE only if catalog API extension needed. After C GREEN, Slice D can depend on the same known-id enumeration.