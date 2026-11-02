# Handoff - prompts-extract Codegen slice (2026-11-02)

**Status:** GREEN
**Specialist:** Codegen
**Kickoff tip base:** `c93aa6d092f516e9e47aad8bd5233d23863d8be8` (`c93aa6d`) - confirmed before edits
**Working-tree tip at handoff:** `fc3faba344961687dc3247577c5572fe096c8b64` (`fc3faba`) - advanced only by unrelated guide+pricing commits after kickoff; **no Codegen commit/push**
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`
**Backup:** `_backup/prompts-extract-codegen-20261102/` (pre-edit copies of `FinalRevisePhase.java`, `TestMethodNaming.java`)

## New prompt ids + files

| Id | Classpath file |
|----|----------------|
| `revise/final-revise` | `src/main/resources/prompts/revise/final-revise.md` |
| `codegen/test-method-naming` | `src/main/resources/prompts/codegen/test-method-naming.md` |

Text extracted **verbatim** from the previous inline Java strings (no prompt rewriting). Em dash preserved in final-revise. Naming file has no trailing newline (matches original string concat).

## Call sites changed

| Call site | Change |
|-----------|--------|
| `src/main/java/delivery/revise/FinalRevisePhase.java` | `systemPrompt()` -> `PromptCatalog.get("revise/final-revise")`; large inline text block removed |
| `src/main/java/delivery/codegen/TestMethodNaming.java` | `OLLAMA_SYSTEM` const removed; loads `PromptCatalog.get("codegen/test-method-naming")` at Ollama polish call |

## PromptCatalog status

**Minimal bootstrap by Codegen** - AE had **not** landed `delivery.prompts.PromptCatalog` or `src/main/resources/prompts/` at assign/start (no AE handoff yet).

Added shared class:

- `src/main/java/delivery/prompts/PromptCatalog.java`

Contract matched to kickoff:

- Resolve: absolute override via system property `keel.prompts.dir`, else env `KEEL_PROMPTS_DIR` -> then classpath `prompts/<id>.md` / `.txt`
- Classpath results cached; override path **re-reads each call** (hot-reload)
- Ids = relative path without extension

**Ownership note:** AE owns the fuller catalog core. This is **one shared class** AE may subsume/expand - do **not** invent a second parallel API. Spring property binding for `keel.prompts.dir` remains Backend's slice (catalog already honors the property/env when set).

Unit coverage: `src/test/java/delivery/prompts/PromptCatalogTest.java` (classpath load, override wins, hot-reload, unset -> classpath).

## Verify

```text
mvn -B -pl keel-portal -am -DskipTests compile
-> BUILD SUCCESS (keel-portal recompiled 748 sources)

mvn -B -pl keel-portal "-Dtest=PromptCatalogTest,FinalRevisePhaseTest,TestMethodNamingTest" test
-> Tests run: 11, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS
```

(Africa/Cairo timestamps on Maven logs: compile finished ~21:21:08, tests ~21:21:46.)

No `package` / portal jar refresh from Codegen (portal may be LIVE; Maestro owns refresh after GREEN).

## Out of scope / skipped

- DomDiffAssertSuggestor / NlLocatorCache (not LLM prompts)
- `src/main/resources/static/prompts/` Guide assets - untouched
- Heal / hunt / vision / LocatorPolicy / Authoring* migrations (AE / Backend)
- Prompt wording changes
- Commit / push

## Next

- AE: land/expand PromptCatalog + remaining prompt tree; may subsume this bootstrap class in place
- Backend: bind `keel.prompts.dir` in Spring props if not already
- Maestro: portal refresh after overall prompts-extract GREEN
