# Handoff — prompts-extract Backend slice (2026-11-02)

**Tip SHA:** `67315671c7bb0e50f848909b8e946b6c76b29564`  
**Base AE tip:** `35b1aab` (`feat(ae): extract system prompts into PromptCatalog + resources`)  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`  
**Specialist:** KeelBackend  
**Kickoff:** `docs/handoff/2026-11-02-prompts-extract-kickoff.md`  
**Backup:** `_backup/prompts-extract-20261102-2130`

## What changed

### A) Spring property `keel.prompts.dir`
- `src/main/resources/application.properties` (committed from HEAD baseline + property only; tip-local model/admin WIP left unstaged):
  - `# Absolute folder of editable prompt files (mirrors env KEEL_PROMPTS_DIR). Leave unset to use classpath prompts/`
  - `keel.prompts.dir=`
- Wiring: `delivery.portal.PromptsDirBinder` (`@Component`)
  - `@Value("${keel.prompts.dir:}")` (Spring relaxed binding also maps env `KEEL_PROMPTS_DIR`)
  - When non-blank: `PromptCatalog.setOverrideDir(absolutePath)` **preferred**, plus `System.setProperty(PromptCatalog.PROP_DIR, ...)` as supplement
- **No second catalog API** — `delivery.prompts.PromptCatalog` only (AE-owned).

### Property / env / resolve contract
| Priority | Source |
|----------|--------|
| 1 | `PromptCatalog.setOverrideDir(Path)` (Spring binder) |
| 2 | System property `keel.prompts.dir` (`PromptCatalog.PROP_DIR`) |
| 3 | Env `KEEL_PROMPTS_DIR` |
| 4 | Classpath `prompts/<id>.md` then `.txt` |

### B) AuthoringReviewService
- Removed inline `SYSTEM_PROMPT`
- Loads `PromptCatalog.get("authoring/authoring-review")` (once-cached)
- Prompt file **not rewritten**: used AE file `src/main/resources/prompts/authoring/authoring-review.md`

### C) static/prompts
Untouched — Guide/Generate public assets only; no secret prompts under static.

## AE coordination
- Synced on AE GREEN `35b1aab` before Backend finish
- Did not rewrite AE prompt wording or PromptCatalog core
- Backend owns Spring bind + AuthoringReviewService call-site only

## Verify
```
mvn -q -pl keel-portal -Dtest=delivery.prompts.AuthoringReviewClasspathPromptTest,delivery.portal.service.AuthoringReviewServiceTest test
```
Tests: **GREEN**

## Portal jar
After Maestro GREEN, refresh portal jar from `keel-portal` (serialize Maven). **No push.**

## Out of scope
Heal/vision/hunt; FinalRevise/TestMethodNaming; prompt wording; USD/pricing; push.
