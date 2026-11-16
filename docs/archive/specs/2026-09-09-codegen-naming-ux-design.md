# Codegen naming UX + assert/TODO polish

Date: 2026-09-09  
Status: Approved in chat; awaiting written-spec review before implementation plan

## Problem

1. **Test class names** append `Test` / `TodoTest` (`TC_01Test`) — operators want the class to be the case id only (`TC_01`).
2. **Test method** is generic (`runCase` / `pendingCase`) instead of reflecting the Excel title.
3. **Page stems** from URL host/leaf look meaningless (`Opssit_Actions`, `NewPage_Actions` for `/operations-users/new`).
4. **Asserts** for “message visible” / `textContains` always use `bodyTextContains`, even when a solid locator exists.
5. **Todo emit** dumps long heal/reason walls into generated comments and `Assert.fail` messages.

## Goals

- Class name = sanitized TC id only (`TC_01`); incomplete cases use `TC_06Todo` (still no `Test` suffix).
- Method name derived from title (deterministic ≤5-word trim); optional Ollama polish for **methods only**.
- Smarter deterministic page stems (`LoginPage`, `NewOperationUser`); no Ollama on page names.
- Locator-first asserts when the proven step has a usable locator; `bodyTextContains` only as fallback.
- One short TODO reason + simple `Assert.fail`.
- Customer ZIP still discoverable by Maven Surefire / TestNG after dropping the `*Test` suffix.

## Non-goals

- Renaming page objects via Ollama.
- Changing prove/heal scoring beyond what emit needs for assert choice.
- Rewriting hand-authored customer tests outside generated output.
- Requiring Ollama for Automate (optional config only).

## Decisions (from brainstorming)

| Topic | Choice |
|--------|--------|
| Test class | ID only: `TC_01` (passed), `TC_06Todo` (partial/blocked). **No** `Test` / `TodoTest` suffix |
| Test method | From title → Java identifier; deterministic trim to ≤5 words |
| Ollama naming | Optional config; **methods only**; pages stay deterministic |
| Page stems | Smarter path/form heuristics (not host leaf alone) |
| Asserts | Locator-bound when possible; body text fallback |
| TODO emit | Short reason only |

---

## Design

### 1. Test class naming

`CodeWriter` (and callers: `DomainCatalogWriter`, `RevisePhase`, smell/catalog paths):

- Passed → class/file `TC_01` under `project/tests/generated/`
- Todo/partial → class/file `TC_06Todo` under `project/tests/todo/`
- Sanitize via existing `toClassName` / `CodegenNaming.sanitizeJavaIdentifier` so illegal ids stay valid Java.

**Surefire discovery (required companion change):** default Surefire includes are `**/Test*.java`, `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`. `TC_01.java` would not match.

Update `customer-framework-template/pom.xml` surefire config to also include generated case classes, e.g.:

```xml
<includes>
  <include>**/TC_*.java</include>
  <include>**/*Test.java</include>
  <include>**/*Tests.java</include>
  <include>**/Test*.java</include>
</includes>
```

Keep `*Test` includes so any remaining template samples still run.

### 2. Test method naming

Replace `runCase` / `pendingCase` with a title-derived method:

1. Take Excel/title description (fallback: tc id).
2. Tokenize → alphanumeric words; drop stop-words lightly if needed.
3. Deterministic trim to **≤5 words**.
4. Join with `_` → valid Java method name (e.g. `Successful_login_test`). If empty/illegal → `case_<sanitizedId>`.
5. If `delivery.codegen.ollama-naming=true` (default **false**):
   - Call existing Ollama base URL/model with a tiny prompt: “shorten to ≤5 English words for a Java test method; return identifier only”.
   - Validate response as a legal method name ≤5 tokens; else keep deterministic.
   - Timeout/failure → keep deterministic (never fail emit).

Ollama does **not** rename pages, fields, or action methods on page objects.

### 3. Page stems (deterministic)

Improve `PageClusterer.pageNameFromUrl` / related normalizers:

| Signal | Stem example |
|--------|----------------|
| Login-shaped screen (username/password/submit or known login path) | `LoginPage` |
| Path `/operations-users/new` | Prefer multi-segment: `NewOperationUser` (or `OperationsUsersNew`) — not bare `New` / host `Opssit` |
| Opaque hex segment | Keep existing `Page_<8hex>` via `CodegenNaming` |
| Keyword leaf (`new`, `class`, …) | Keep `NewPage` keyword escape if no richer path context |

Rules of thumb:

- Prefer **last 2–3 meaningful path segments** over host brand when path is not `/`.
- Map common verbs (`new`, `edit`, `create`) as prefixes/suffixes onto the entity segment (`operations-users` → `OperationUser`).
- Still run through `CodegenNaming.pageStem` so keywords and opaque hashes stay Java-safe.
- Alias legacy login names through `PageNameNormalizer` into `LoginPage` when login-shaped.

Actions/locators remain `{Stem}_Actions` / `{Stem}_Locators`; local vars via `safeLocalVarName` (`loginPage`, `newOperationUser`).

### 4. Assert strategy (locator-first)

In `PageAccumulator` / emit:

| Condition | Emit |
|-----------|------|
| Assert has a proven **solid** locator (non-body XPath; prefer `data-axis-test-id`, css id, stable By) | Page assert method calling `textContains(By, …)` / `elementVisable(By)` / typed helper |
| Pure free-floating message / no solid locator / existing “body-text visibility” path | Keep `Assertion.bodyTextContains(expected)` |
| `urlContains` | Unchanged (`urlContains` helper) |

Do not invent brittle giant XPaths just to avoid body text. “Solid” = same bar used today to skip brittle XPath fields for body-text asserts, inverted: if we would have skipped the field as brittle, use body text; otherwise bind the locator.

Long Excel phrases remain codegen method names via `assertMethodName` (unchanged policy); behavior of the method body is what changes.

### 5. TODO emit

In Freemarker + reason prep before model:

- Drop multi-line HEAL dumps / REVIEW wall spam from the fail path when possible.
- Emit at most:
  - one short `// STOPPED HERE: <≤120 char summary>`
  - `Assert.fail("TODO TC_06: <same short summary>")`
- Summarize by taking first line / stripping `HEAL_EXHAUSTED` payload blobs to a single clause (e.g. “final assert failed after Create user”).
- Optional `reviewComments` list: cap to 0–2 short lines or omit if redundant with the reason.

### 6. Configuration

Add to `application.properties` (defaults keep current offline behavior for naming LLM):

```properties
# Optional Ollama polish for generated *test method* names only (pages stay deterministic)
delivery.codegen.ollama-naming=false
# Reuse delivery.llm-base-url / delivery.llm-model unless overridden later
```

No UI required in this pass (config/properties only). Portal UI toggle can be a follow-up.

### 7. Touch points (implementation map)

| Area | Files (expected) |
|------|------------------|
| Naming API | `CodegenNaming.java`, new helpers for title→method, path→stem |
| Emit | `CodeWriter.java`, `PageAccumulator.java`, `PageClusterer.java`, `PageNameNormalizer.java` |
| Templates | `TodoTest.java.ftl`, generated-test ftl if separate |
| Catalog/revise paths | `DomainCatalogWriter.java`, `RevisePhase.java` |
| Surefire | `customer-framework-template/pom.xml` |
| Config | `application.properties`, delivery config bean if used |
| Tests | `CodegenNamingTest`, `CodeWriterTest`, PageClusterer tests, assert-path unit tests |

---

## Acceptance criteria

1. Fresh Automate emit produces `TC_01.java` / `TC_06Todo.java` (no `Test` suffix).
2. `@Test` method name reflects title (≤5 words deterministic; Ollama when enabled and healthy).
3. Login screen → `LoginPage_*`; `/operations-users/new` → multi-segment stem, not `NewPage` alone / not host-only `Opssit` when path exists.
4. Message asserts with solid locators call locator helpers; body-only messages still use `bodyTextContains`.
5. Todo classes show one short stop reason; no multi-screen heal dump in the fail comment/`Assert.fail`.
6. `mvn test` (or project compile + surefire include) discovers `TC_*.java`.
7. With `ollama-naming=false`, emit never calls Ollama for names.

## Spec self-review

- No placeholders left.
- Class naming (`TC_01`) conflicts with Surefire defaults → explicitly mitigated via pom includes.
- Ollama scope limited to methods to avoid page-object drift.
- Scope does not include Automate UI for the flag (config only this pass).
