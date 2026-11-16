# Live Ollama Generate models Implementation Plan

**Status:** Implemented 2026-10-18 (working tree). Portal jar rebuild still required.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate lists chat models from live Ollama (same tags as Admin LLM Settings), remembers the last full tag list on disk, and never needs a code edit when you `ollama pull`.

**Architecture:** `LlmSettingsService` owns `/api/tags`, `cachedOllamaModels` in `llm-settings.json`, and chat-vs-vision name filter. `GET /api/generate/models` is cache-first (instant if a list exists) then `?refresh=1` hits Ollama. Generate page shows a loader with no cache, or the cached list plus “Updating…”. Account vision radios stay UI-TARS / Qwen.

**Tech Stack:** Java 21, Spring MVC, TestNG, existing `LlmSettingsService` HttpClient (8s tags timeout), Thymeleaf `generate.html` / `admin-llm-settings.html`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-18-live-ollama-generate-models-design.md`
- TDD: failing TestNG test before production code. Watch it fail.
- PowerShell: quote Maven tests: `mvn -pl keel-portal "-Dtest=ClassName"` (commas inside the quotes). Always `"-Dmaven.compiler.release=21"`.
- Author/committer `rsayed <rsayed@axisapp.com>`. No `Co-authored-by: Cursor` (use `git write-tree` / `git commit-tree` / `git reset --soft` if `git commit` adds one). Do not push. Do not commit `META-INF/`, `target/`, portal DB, `api-keys.local.bat`.
- No Claude / Anthropic models.
- No Excel rewrite, no portal CSS reskin, no Midscene.
- Do not log secret values. `/api/generate/models` returns model **names** only.
- Account → Vision radios stay UI-TARS vs Qwen.
- Env still wins for `delivery.generate-model` / `DELIVERY_GENERATE_MODEL`.
- After Java/template edits the running jar is stale until rebuild + restart + Ctrl+F5. Do not claim Generate is live without that.
- Site-agnostic: no AxisPay hostnames in production code.

## File map

| File | Role |
|------|------|
| `LlmSettingsService` | `isVisionModel` / `chatModels`; persist `cachedOllamaModels`; `listOllamaModels` writes cache on success; `listForGenerate(boolean refresh)` |
| `GenerateModelService` | `configPayload(boolean refresh)` + `resolve` against served chat list |
| `GenerateConfigController` | `GET /api/generate/models?refresh=` |
| `generate.html` | Loader vs Updating…; two-fetch (cache then refresh) |
| `admin-llm-settings.html` | Disable Refresh while in flight (cache write is server-side) |
| `LlmSettingsServiceTest` | Filter + cache round-trip (no live Ollama) |
| `GenerateModelServiceTest` | live / cache / fallback / resolve (HttpServer or injected fail) |
| `GenerateConfigApiTest` | JSON `source` / `unreachable`; force unreachable base URL |
| `GenerateMvcTest` | Hint copy + `/api/generate/models?refresh=1` |
| `PRODUCT.md` / `CHANGELOG.md` | User-facing once built |

---

### Task 1: Chat vs vision name filter

**Files:**
- Modify: `src/main/java/delivery/portal/service/llm/LlmSettingsService.java`
- Modify: `src/test/java/delivery/portal/service/llm/LlmSettingsServiceTest.java`

**Interfaces:**
- Produces: `public static boolean isVisionModel(String name)` — true when `name` lowercased contains `vl`, `llava`, `ui-tars`, or `vision`
- Produces: `public static List<String> chatModels(List<String> all)` — keeps order, drops vision names, never null (empty list if all vision / null input)

- [ ] **Step 1: Write the failing tests** (add to `LlmSettingsServiceTest`)

```java
@Test
public void chatModels_dropsVisionLikeNames_keepsChat() {
    List<String> all = LlmSettingsService.parseOllamaTagsJson("""
            {"models":[
              {"name":"gemma4:e2b"},
              {"name":"qwen2.5vl:7b"},
              {"name":"llava:latest"},
              {"name":"ui-tars"},
              {"name":"foo-vision:latest"},
              {"name":"qwen2.5:latest"}
            ]}
            """);
    Assert.assertEquals(
            LlmSettingsService.chatModels(all),
            List.of("gemma4:e2b", "qwen2.5:latest"));
}

@Test
public void isVisionModel_nullOrBlank_isFalse() {
    Assert.assertFalse(LlmSettingsService.isVisionModel(null));
    Assert.assertFalse(LlmSettingsService.isVisionModel("  "));
    Assert.assertFalse(LlmSettingsService.isVisionModel("gemma4:e2b"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl keel-portal "-Dmaven.compiler.release=21" "-Dtest=LlmSettingsServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL compiling (`cannot find symbol chatModels` / `isVisionModel`).

- [ ] **Step 3: Implement filter only**

```java
public static boolean isVisionModel(String name) {
    if (name == null || name.isBlank()) {
        return false;
    }
    String n = name.toLowerCase(java.util.Locale.ROOT);
    return n.contains("vl") || n.contains("llava") || n.contains("ui-tars") || n.contains("vision");
}

public static List<String> chatModels(List<String> all) {
    if (all == null || all.isEmpty()) {
        return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String name : all) {
        if (name == null || name.isBlank() || isVisionModel(name)) {
            continue;
        }
        out.add(name.trim());
    }
    return List.copyOf(out);
}
```

- [ ] **Step 4: Re-run Task 1 tests**

Same Maven line. Expected: PASS (existing tests still pass).

- [ ] **Step 5: Commit**

Message: `Add chat-vs-vision Ollama name filter for Generate.`

Use `commit-tree` as rsayed. Do not push.

---

### Task 2: Persist `cachedOllamaModels`

**Files:**
- Modify: `src/main/java/delivery/portal/service/llm/LlmSettingsService.java` (`StoredSettings`, `writeToDisk`, `fromFileMap`, `update` must keep cache)
- Modify: `src/test/java/delivery/portal/service/llm/LlmSettingsServiceTest.java`

**Interfaces:**
- Produces: `void rememberOllamaCache(List<String> names)` — trims, drops blanks, writes file, updates `stored`
- Produces: `List<String> cachedOllamaModels()` — never null
- JSON key: `cachedOllamaModels` (array of strings). Secrets fields unchanged.
- `update(UpdateRequest)` must **not** wipe the cache.

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void rememberOllamaCache_roundTripsNames_notSecrets() throws Exception {
    LlmSettingsService svc = new LlmSettingsService(props);
    svc.update(new UpdateRequest("persist-secret-ZZ99", null, null, null, "mychat", null, false, false, false));
    svc.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5vl:7b", "  ", "qwen2.5:latest"));
    Assert.assertEquals(svc.cachedOllamaModels(), List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
    String raw = Files.readString(storeRoot.resolve("llm-settings.json"), StandardCharsets.UTF_8);
    Assert.assertTrue(raw.contains("cachedOllamaModels"));
    Assert.assertTrue(raw.contains("gemma4:e2b"));
    Assert.assertFalse(raw.contains("persist-secret-ZZ99"));

    DeliveryPortalProperties props2 = new DeliveryPortalProperties();
    props2.setStoreRoot(storeRoot.toString());
    props2.setLlmBaseUrl("http://127.0.0.1:11434");
    LlmSettingsService reloaded = new LlmSettingsService(props2);
    Assert.assertEquals(reloaded.cachedOllamaModels(), List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
}

@Test
public void updateSecrets_preservesCachedModels() {
    LlmSettingsService svc = new LlmSettingsService(props);
    svc.rememberOllamaCache(List.of("gemma4:e2b"));
    svc.update(new UpdateRequest(null, null, null, null, "other-chat", null, false, false, false));
    Assert.assertEquals(svc.cachedOllamaModels(), List.of("gemma4:e2b"));
}
```

- [ ] **Step 2: Run to verify fail**

Same `LlmSettingsServiceTest` Maven line. Expected: FAIL (`rememberOllamaCache` missing).

- [ ] **Step 3: Implement cache field**

Extend `StoredSettings` with `List<String> cachedOllamaModels` (never null; use `List.of()`).

`writeToDisk`: `raw.put("cachedOllamaModels", s.cachedOllamaModels());`

`fromFileMap`: parse JSON array of strings; ignore junk.

`rememberOllamaCache`: synchronized; copy trimmed non-blank names; `writeToDisk`; `stored = next`.

`update(...)`: copy `cur.cachedOllamaModels` into `next`.

`empty()` / `defaultsFromProps()`: empty cache list.

- [ ] **Step 4: Re-run tests** — PASS.

- [ ] **Step 5: Commit**

Message: `Persist last Ollama tag list in llm-settings.json.`

---

### Task 3: `listForGenerate` (live / cache / fallback)

**Files:**
- Modify: `src/main/java/delivery/portal/service/llm/LlmSettingsService.java`
- Create: `src/test/java/delivery/portal/service/llm/GenerateModelListTest.java` (or add methods on `LlmSettingsServiceTest` if you inject `HttpClient`)

**Interfaces:**
- Produces: `public record GenerateModelList(List<String> models, String defaultModel, String source, boolean unreachable)` with `source` one of `live` | `cache` | `fallback`
- Produces: `public GenerateModelList listForGenerate(boolean refresh)`
- Fallback names: `gemma4:e2b`, `qwen2.5:latest`
- `refresh == false` and chat(cache) non-empty → return cache **without** calling Ollama
- otherwise try `listOllamaModels(null)`; on success `rememberOllamaCache(all)`; chat filter; if chat empty treat as fail
- on fail: chat(cache) if non-empty (`source=cache`, `unreachable=true`); else fallback (`source=fallback`, `unreachable=true`)
- `defaultModel`: `props.getGenerateModel()` if in chat list, else Admin `ollamaChatModel` if in list, else `models.get(0)`, else `""`
- On live success, `listOllamaModels` itself should `rememberOllamaCache` so Admin Refresh also writes cache (call remember inside `listOllamaModels` after parse, before return)

- [ ] **Step 1: Write failing tests** using a local `HttpServer` like `LocalLlmClientTest` **or** `LlmSettingsService(props, http)` with a client that throws.

```java
@Test
public void listForGenerate_cacheFirst_skipsHttp() {
    LlmSettingsService svc = new LlmSettingsService(props);
    svc.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5vl:7b", "mistral:latest"));
    var list = svc.listForGenerate(false);
    Assert.assertEquals(list.source(), "cache");
    Assert.assertEquals(list.models(), List.of("gemma4:e2b", "mistral:latest"));
    Assert.assertFalse(list.unreachable());
}

@Test
public void listForGenerate_refreshFail_usesCache() {
    props.setLlmBaseUrl("http://127.0.0.1:9");
    LlmSettingsService svc = new LlmSettingsService(props);
    svc.rememberOllamaCache(List.of("cached-chat:latest", "qwen2.5vl:7b"));
    var list = svc.listForGenerate(true);
    Assert.assertEquals(list.source(), "cache");
    Assert.assertTrue(list.unreachable());
    Assert.assertEquals(list.models(), List.of("cached-chat:latest"));
}

@Test
public void listForGenerate_refreshFail_emptyCache_fallbackTwo() {
    props.setLlmBaseUrl("http://127.0.0.1:9");
    LlmSettingsService svc = new LlmSettingsService(props);
    var list = svc.listForGenerate(true);
    Assert.assertEquals(list.source(), "fallback");
    Assert.assertTrue(list.unreachable());
    Assert.assertEquals(list.models(), List.of("gemma4:e2b", "qwen2.5:latest"));
}
```

Add a live-success test with `HttpServer` on `127.0.0.1:0` serving `src/test/resources/fixtures/ollama-tags.json`, `props.setLlmBaseUrl` to that port, `listForGenerate(true)` → `source=live`, models `gemma4:e2b` + `qwen2.5:latest` (vl dropped), cache stored full three names.

- [ ] **Step 2: Run** `"-Dtest=LlmSettingsServiceTest,GenerateModelListTest"` — FAIL missing `listForGenerate`.

- [ ] **Step 3: Implement `listForGenerate` + cache write in `listOllamaModels`.** Do not log bodies that might contain keys.

- [ ] **Step 4: Re-run — PASS.**

- [ ] **Step 5: Commit**

Message: `Resolve Generate models from live Ollama, cache, or fallback.`

---

### Task 4: Wire Generate API + `resolve`

**Files:**
- Modify: `src/main/java/delivery/portal/service/GenerateModelService.java`
- Modify: `src/main/java/delivery/portal/api/GenerateConfigController.java`
- Modify: `src/test/java/delivery/portal/api/GenerateConfigApiTest.java`
- Create: `src/test/java/delivery/portal/service/GenerateModelServiceTest.java`

**Interfaces:**
- Consumes: `LlmSettingsService.listForGenerate(boolean)`
- Produces: `GenerateModelService.configPayload(boolean refresh)` map keys `defaultModel`, `models`, `source`, `unreachable`
- Produces: `resolve(String requested)` allows any name in `listForGenerate(false).models()` (cache/fallback, no live wait). Empty requested → `defaultModel`.
- Controller: `@RequestParam(value = "refresh", defaultValue = "false") boolean refresh`

- [ ] **Step 1: Failing unit test**

```java
@Test
public void resolve_acceptsCachedNameNotInHardcodedPair() {
    // svc with cache containing "mistral:latest"
    Assert.assertEquals(models.resolve("mistral:latest"), "mistral:latest");
}
```

```java
@Test
public void configPayload_includesSourceAndUnreachable() {
    Map<String, Object> body = models.configPayload(false);
    Assert.assertTrue(body.containsKey("source"));
    Assert.assertTrue(body.containsKey("unreachable"));
    Assert.assertTrue(body.get("models") instanceof List);
}
```

Update `GenerateConfigApiTest`: set `delivery.llm-base-url=http://127.0.0.1:9` so the Spring test does **not** hit a real Ollama. Expect fallback models + `$.source` = `fallback` + `$.unreachable` = true (empty cache in that store-root). Keep auth header `X-Keel-Requested-With`.

- [ ] **Step 2: Run** `"-Dtest=GenerateModelServiceTest,GenerateConfigApiTest"` — FAIL.

- [ ] **Step 3: Inject `LlmSettingsService` into `GenerateModelService`. Implement `configPayload(boolean)` and `resolve`. Add `refresh` query param.** Constructor becomes `(DeliveryPortalProperties props, LlmSettingsService llmSettings)`. Spring still autowires.

`allowedModels()` must delegate to `listForGenerate(false).models()` so Generate/Compare jobs accept a pulled chat model.

- [ ] **Step 4: Re-run plus `LlmSettingsServiceTest` — PASS.**

- [ ] **Step 5: Commit**

Message: `Serve live-or-cached Ollama chat models on GET /api/generate/models.`

---

### Task 5: Generate loader + Admin Refresh wait

**Files:**
- Modify: `src/main/resources/templates/generate.html` (`loadGenerateModels`, `#generate-model-hint`)
- Modify: `src/main/resources/templates/admin-llm-settings.html` (`refreshModels`)
- Modify: `src/test/java/delivery/portal/web/GenerateMvcTest.java`

**Interfaces:**
- Consumes: `GET /api/generate/models` then `GET /api/generate/models?refresh=1` when `source === 'cache'`
- Hint copy (exact):
  - no list yet / in-flight first fetch: `Loading models from Ollama…`
  - cache shown, second fetch: `Updating…`
  - `unreachable === true`: `Ollama unreachable — using last list.`
  - live ok: restore `The default works for most projects.`
- Keep `sessionStorage` last pick if still in `models`.
- `generateBtn` / `compare-btn`: do not enable for missing project/stories **and** do not enable while the first fetch has no models yet (`modelsReady` flag). After fallback two names, modelsReady is true.
- Admin: disable `#btn-refresh-models` while the fetch is in flight; re-enable in `finally`. Server already writes cache from Task 3.

- [ ] **Step 1: Failing MVC assertions** (add to `generate_wiresModelSelectIntoGenerateRequest` or a new test)

```java
Assert.assertTrue(body.contains("/api/generate/models?refresh=1")
                || body.contains("refresh=1"),
        "must refresh live tags after cache paint");
Assert.assertTrue(body.contains("Loading models from Ollama"),
        "empty-cache loader copy missing");
Assert.assertTrue(body.contains("Updating…") || body.contains("Updating..."),
        "cache updating copy missing");
Assert.assertTrue(body.contains("Ollama unreachable — using last list.")
                || body.contains("Ollama unreachable"),
        "unreachable copy missing");
```

Use one consistent ellipsis character in production copy (`…`) and assert that.

- [ ] **Step 2: Run** `"-Dtest=GenerateMvcTest"` — FAIL missing strings.

- [ ] **Step 3: Implement `loadGenerateModels`**

```javascript
async function loadGenerateModels() {
    const hint = document.getElementById('generate-model-hint');
    const setHint = function (t) { if (hint) hint.textContent = t; };
    generateModelSelect.disabled = true;
    setHint('Loading models from Ollama…');
    try {
        const first = await fetch('/api/generate/models', { credentials: 'same-origin', headers: API_HEADERS });
        if (!first.ok) throw new Error('Could not load model list');
        const a = await first.json();
        renderGenerateModels(a);
        if (a.source === 'cache') {
            setHint('Updating…');
            const second = await fetch('/api/generate/models?refresh=1', {
                credentials: 'same-origin', headers: API_HEADERS
            });
            if (second.ok) {
                const b = await second.json();
                if (b.source === 'live' || (Array.isArray(b.models) && b.models.length)) {
                    renderGenerateModels(b);
                }
                if (b.unreachable) setHint('Ollama unreachable — using last list.');
                else setHint('The default works for most projects.');
            } else {
                setHint('Ollama unreachable — using last list.');
            }
        } else if (a.unreachable) {
            setHint('Ollama unreachable — using last list.');
        } else {
            setHint('The default works for most projects.');
        }
    } catch (err) {
        generateModelSelect.innerHTML = '';
        const opt = document.createElement('option');
        opt.value = 'gemma4:e2b';
        opt.textContent = 'gemma4:e2b';
        generateModelSelect.appendChild(opt);
        defaultGenerateModel = 'gemma4:e2b';
        generateModels = ['gemma4:e2b'];
        renderCompareModelB({ models: generateModels, defaultModel: defaultGenerateModel });
        renderAuthoringReviewModels();
        setHint('Ollama unreachable — using last list.');
    }
}
```

Admin `refreshModels`: `btn.disabled = true` at start, `finally btn.disabled = false`. Optional status on `#llm-error` only on failure (already).

- [ ] **Step 4: Re-run `GenerateMvcTest` — PASS.**

- [ ] **Step 5: Commit**

Message: `Show Generate model loader, then last list while Ollama refreshes.`

---

### Task 6: Docs + spec status

**Files:**
- Modify: `docs/PRODUCT.md` (Generate AI model + Admin LLM bullets)
- Modify: `CHANGELOG.md` (dated 2026-10-18 section)
- Modify: `docs/superpowers/specs/2026-10-18-live-ollama-generate-models-design.md` Status → implemented (after code exists)
- Modify: `docs/ENGINEERING_HANDOFF.md` plan path
- Modify: `D:\priv\testpilot\handoffs\RESUME-2026-10-15.md` (outside git)

**Copy (PRODUCT, Generate):** AI model list comes from live Ollama chat tags (vision names omitted). Admin Save sets the default. If Ollama is down, last list is used.

**CHANGELOG:** Generate model dropdown follows Ollama `/api/tags` with `cachedOllamaModels`; loader / Updating…; Account vision radios unchanged.

- [ ] **Step 1: Write the PRODUCT/CHANGELOG lines** (no test).
- [ ] **Step 2: Gate**

```
mvn -pl keel-portal "-Dmaven.compiler.release=21" "-Dtest=LlmSettingsServiceTest,GenerateModelServiceTest,GenerateConfigApiTest,GenerateMvcTest,AdminLlmSettingsApiTest,GenerateModelListTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: 0 failures. If `GenerateModelListTest` was folded into `LlmSettingsServiceTest`, drop that name.

- [ ] **Step 3: Commit docs**

Message: `Document live Ollama Generate model list.`

- [ ] **Step 4: Rebuild/restart is not this task.** Tell the owner the jar is stale until `package` + portal start. Do not push.

---

## Spec coverage (self-check)

| Spec item | Task |
|-----------|------|
| Live `/api/tags` same URL/token as Settings | 3 |
| Save full names as `cachedOllamaModels` | 2–3 |
| Generate drops vl/llava/ui-tars/vision | 1, 3 |
| Admin lists all tags | 3 (`listOllamaModels` unchanged shape) |
| Default = env then Admin chat then first | 3 |
| `resolve` accepts pulled chat name | 4 |
| Loader vs Updating… vs unreachable | 5 |
| `GET /api/generate/models` names only + source | 4 |
| Empty chat after live → cache then fallback | 3 |
| Account vision unchanged | 6 (docs) + no code on `account.html` |
| Tests without live Ollama | 2–4 (`127.0.0.1:9` / HttpServer) |
