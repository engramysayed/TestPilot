# Authoring Preflight Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Generate “Review with AI” (user picks Cursor or Ollama) that returns findings + a proposed suite for Accept/Discard without overwriting Excel until Accept.

**Architecture:** Shared `AuthoringReviewParser` + `AuthoringReviewService` load current workbook cases, call Cursor sidecar (`mode: authoring-review`) or Ollama with the same JSON contract, repair + quality-gate the proposal, return preview. Accept reuses existing import API. Banner on gate failures; no auto-call, no silent provider fallback.

**Tech Stack:** Java 17 / Spring Boot, TestNG, `LocalLlmClient`, `CursorHealClient` + `tools/cursor-heal/heal.mjs`, Generate Thymeleaf/JS.

**Spec:** `docs/superpowers/specs/2026-09-06-authoring-preflight-review-design.md`

## Global Constraints

- Never overwrite workbook until Accept (review endpoint is read + propose only).
- User chooses `cursor` | `ollama`; fail closed if chosen provider unavailable — no silent switch.
- Banner on quality-gate/authoring issues only prompts; user still clicks Review.
- Ground truth: current suite JSON + optional `stories` + optional `requirementsNotes`.
- Distinct from Execute heal recovery and Automate final-revise.
- Soft suite cap: **50** TCs; reject above with clear error (no silent truncate).
- Skip git commits unless the user explicitly asks.

## File structure

| File | Responsibility |
|------|----------------|
| `src/main/java/delivery/excel/AuthoringReviewParser.java` | Parse LLM JSON → findings + cases + coverageNotes |
| `src/test/java/delivery/excel/AuthoringReviewParserTest.java` | Parser unit tests |
| `src/main/java/delivery/portal/service/AuthoringReviewService.java` | Orchestrate load → provider → repair → gate → preview map |
| `src/test/java/delivery/portal/service/AuthoringReviewServiceTest.java` | Service tests with mocked providers |
| `src/main/java/delivery/heal/CursorHealClient.java` | `authoringReview(...)` stdin mode |
| `tools/cursor-heal/heal.mjs` | Handle `mode: "authoring-review"` |
| `src/main/java/delivery/portal/api/GenerateTcController.java` | `POST .../generate/authoring-review` |
| `src/test/java/delivery/portal/web/GenerateMvcTest.java` (or new) | API smoke if pattern exists |
| `src/main/resources/templates/generate.html` | Review panel, banner, Accept/Discard |
| Spec status already Approved |

---

### Task 1: AuthoringReviewParser

**Files:**
- Create: `src/main/java/delivery/excel/AuthoringReviewParser.java`
- Create: `src/test/java/delivery/excel/AuthoringReviewParserTest.java`

**Interfaces:**
- Consumes: `GeneratedTcJsonParser` patterns / `ManualTestCase` construction; strip markdown fences via `TcImportRepair.stripMarkdownFences`
- Produces:
  - `record Finding(String severity, String tcId, String message)`
  - `record ParseResult(List<Finding> findings, List<ManualTestCase> cases, String coverageNotes)`
  - `static ParseResult parse(String raw)` — throws `IllegalArgumentException` on garbage / empty cases when cases array present but empty after parse; allow findings-only with empty cases list only if `cases` key absent? **Spec:** empty cases = error. Require non-empty `cases` array.

- [ ] **Step 1: Write the failing test**

```java
package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AuthoringReviewParserTest {
    @Test
    public void parse_happyPath_findingsAndCases() {
        String raw = """
                {
                  "findings":[{"severity":"warn","tcId":"TC_01","message":"Vague assert"}],
                  "cases":[{
                    "tcId":"TC_01","title":"Login empty email","preconditions":"",
                    "steps":"1. Leave the Email field empty\\n2. Click Login",
                    "expectedResult":"Shows error \\"Invalid\\"","priority":"P1","tags":"",
                    "visualAssertion":"","testData":"\\n","keelPath":"EXECUTE"
                  }],
                  "coverageNotes":"Reviewed empty email"
                }
                """;
        AuthoringReviewParser.ParseResult r = AuthoringReviewParser.parse(raw);
        Assert.assertEquals(r.findings().size(), 1);
        Assert.assertEquals(r.findings().get(0).severity(), "warn");
        Assert.assertEquals(r.cases().size(), 1);
        Assert.assertEquals(r.cases().get(0).tcId(), "TC_01");
        Assert.assertTrue(r.coverageNotes().contains("Reviewed"));
    }

    @Test
    public void parse_garbage_throws() {
        try {
            AuthoringReviewParser.parse("not json");
            Assert.fail("expected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("review"));
        }
    }

    @Test
    public void parse_emptyCases_throws() {
        try {
            AuthoringReviewParser.parse("{\"findings\":[],\"cases\":[]}");
            Assert.fail("expected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -q "-Dtest=AuthoringReviewParserTest" test`

- [ ] **Step 3: Implement parser**

```java
package delivery.excel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AuthoringReviewParser {
    private AuthoringReviewParser() {}

    public record Finding(String severity, String tcId, String message) {
        public Finding {
            severity = severity == null || severity.isBlank() ? "info" : severity.trim().toLowerCase(Locale.ROOT);
            tcId = tcId == null ? "" : tcId.trim();
            message = message == null ? "" : message.trim();
        }
    }

    public record ParseResult(List<Finding> findings, List<ManualTestCase> cases, String coverageNotes) {
        public ParseResult {
            findings = findings == null ? List.of() : List.copyOf(findings);
            cases = cases == null ? List.of() : List.copyOf(cases);
            coverageNotes = coverageNotes == null ? "" : coverageNotes;
        }
    }

    public static ParseResult parse(String raw) {
        String stripped = TcImportRepair.stripMarkdownFences(raw == null ? "" : raw);
        JSONObject root = extractObject(stripped);
        if (root == null) {
            throw new IllegalArgumentException("Authoring review response is not valid JSON");
        }
        List<Finding> findings = new ArrayList<>();
        JSONArray fArr = root.optJSONArray("findings");
        if (fArr != null) {
            for (int i = 0; i < fArr.length(); i++) {
                JSONObject o = fArr.optJSONObject(i);
                if (o == null) continue;
                findings.add(new Finding(
                        o.optString("severity", "info"),
                        o.optString("tcId", ""),
                        o.optString("message", "")));
            }
        }
        // Reuse GeneratedTcJsonParser on a wrapper, or map cases array manually:
        JSONArray cArr = root.optJSONArray("cases");
        if (cArr == null || cArr.length() == 0) {
            throw new IllegalArgumentException("Authoring review response has no cases");
        }
        GeneratedTcJsonParser.ParseResult parsed = GeneratedTcJsonParser.parse(
                new JSONObject().put("cases", cArr).put("coverageNotes", root.optString("coverageNotes", "")).toString());
        if (parsed.cases().isEmpty()) {
            throw new IllegalArgumentException("Authoring review response has no cases");
        }
        String notes = root.optString("coverageNotes", "");
        if (notes.isBlank()) {
            notes = parsed.coverageNotes();
        }
        return new ParseResult(findings, parsed.cases(), notes);
    }

    private static JSONObject extractObject(String text) {
        // Prefer full parse; else scan like heal.mjs extractJsonObject
        try {
            return new JSONObject(text.trim());
        } catch (Exception ignored) {
        }
        int end = text.lastIndexOf('}');
        for (int start = text.indexOf('{'); start >= 0 && start < end; start = text.indexOf('{', start + 1)) {
            try {
                return new JSONObject(text.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
```

Adjust if `GeneratedTcJsonParser` expects a different envelope — read that class and match its happy-path shape.

- [ ] **Step 4: Run tests — PASS**

- [ ] **Step 5: Commit** — skip unless user asked

---

### Task 2: AuthoringReviewService (+ provider hooks)

**Files:**
- Create: `src/main/java/delivery/portal/service/AuthoringReviewService.java`
- Create: `src/test/java/delivery/portal/service/AuthoringReviewServiceTest.java`
- Modify: `src/main/java/delivery/heal/CursorHealClient.java` — add `authoringReview(String suiteJsonPromptPayload)` returning raw string
- Modify: `tools/cursor-heal/heal.mjs` — handle `mode: "authoring-review"`

**Interfaces:**
- Consumes: `GeneratedWorkbookService`, `PortalStore`, `AuthoringReviewParser`, `LocalLlmClient`, `CursorHealClient`, `TcImportRepair`, `GenerateQualityGate`, `GeneratedTcCsvParser`
- Produces:
  - `public static final int MAX_CASES = 50;`
  - `Map<String, Object> review(String projectId, Long ownerUserId, String provider, String requirementsNotes, String stories) throws Exception`
  - Map keys: `provider`, `findings`, `csv`, `coverageNotes`, `gateErrors` (list), `tcCount`, `previewOk` (boolean — true when gateErrors empty)

- [ ] **Step 1: Write failing service tests** (temp store + fake LLM via package-visible seam)

Prefer constructor injection of a small port:

```java
@FunctionalInterface
public interface ReviewLlmPort {
    String complete(String provider, String system, String user) throws Exception;
}
```

In production, default port:
- `ollama` → `LocalLlmClient` from project/portal props (same base URL/model as Generate)
- `cursor` → `CursorHealClient.authoringReview(userPayload)` (system baked into sidecar prompt)

Test with a stub port returning fixed JSON.

```java
@Test
public void review_ollama_returnsPreview_withoutSavingExcel() throws Exception {
    // save workbook with 1 TC via GeneratedWorkbookService
    // stub returns valid review JSON with leave-empty fix
    // assert csv contains Leave; assert excel steps unchanged until import
}

@Test
public void review_unknownProvider_fails() { ... }

@Test
public void review_overCap_fails() { ... } // 51 trivial cases
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement service + Cursor client + heal.mjs**

**Prompt essentials (system):** You are Keel authoring reviewer. Given suite JSON + optional stories + notes, return ONLY JSON with findings[], cases[] (full suite), coverageNotes. Fix leave-empty/TestData, vague asserts, ambiguities; flag missing coverage vs stories; do not invent huge unrelated suites; keep TC_id / KeelPath rules.

**CursorHealClient:**

```java
public String authoringReview(String suiteJson) {
    JSONObject req = new JSONObject();
    req.put("mode", "authoring-review");
    req.put("suite", suiteJson == null ? "" : suiteJson);
    return invoke(req);
}
```

Or put structured fields: `cases`, `stories`, `requirementsNotes` on the req object instead of a string blob — prefer structured:

```java
public String authoringReview(JSONObject payload) {
    payload.put("mode", "authoring-review");
    return invoke(payload);
}
```

**heal.mjs:** When `mode === "authoring-review"`, prompt Cursor Auto to return the findings/cases/coverageNotes JSON (no recoverySteps). Print raw model text to stdout (same as invent). Increase timeout if needed for large suites (e.g. 180s for this mode only).

**Service sketch:**

```java
public Map<String, Object> review(...) {
  store.getOwnedProject(...);
  List<ManualTestCase> current = new ExcelTcReader().read(workbooks.requireExcel(projectId));
  if (current.size() > MAX_CASES) throw new IllegalArgumentException("Suite exceeds " + MAX_CASES + " cases");
  String providerNorm = provider.trim().toLowerCase(Locale.ROOT);
  if (!providerNorm.equals("cursor") && !providerNorm.equals("ollama")) {
    throw new IllegalArgumentException("provider must be cursor or ollama");
  }
  JSONObject suite = toSuiteJson(current, stories, requirementsNotes);
  String raw = llm.complete(providerNorm, SYSTEM, suite.toString(2));
  if (raw == null || raw.isBlank()) {
    throw new IllegalStateException(providerNorm + " returned empty review");
  }
  ParseResult parsed = AuthoringReviewParser.parse(raw);
  List<ManualTestCase> repaired = TcImportRepair.repairCases(parsed.cases());
  List<String> gateErrors = GenerateQualityGate.validate(repaired, project.getBaseUrl());
  Map<String, Object> out = new LinkedHashMap<>();
  out.put("provider", providerNorm);
  out.put("findings", parsed.findings().stream().map(this::findingMap).toList());
  out.put("csv", GeneratedTcCsvParser.toCsv(repaired));
  out.put("coverageNotes", parsed.coverageNotes());
  out.put("gateErrors", gateErrors);
  out.put("previewOk", gateErrors.isEmpty());
  out.put("tcCount", repaired.size());
  return out;
}
```

Resolve Ollama URL/model like `TcGenerateService` / portal props.

- [ ] **Step 4: Run service + parser tests — PASS**

- [ ] **Step 5: Commit** — skip unless asked

---

### Task 3: API endpoint

**Files:**
- Modify: `src/main/java/delivery/portal/api/GenerateTcController.java`
- Test: extend `GenerateMvcTest` or add `AuthoringReviewMvcTest` if Spring MVC tests exist for generate

**Interfaces:**
- Consumes: `AuthoringReviewService.review`
- Produces: `POST /{projectId}/generate/authoring-review`

- [ ] **Step 1: Write MVC/API test** expecting 200 preview without excel mutation; 400 on bad provider; map QUALITY-style errors if service throws gate on empty — review itself returns gateErrors in body, not throw, unless parse fails → 400

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Wire controller**

```java
public record AuthoringReviewRequest(String provider, String requirementsNotes, String stories) {}

@PostMapping(value = "/{projectId}/generate/authoring-review", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> authoringReview(@PathVariable String projectId, @RequestBody AuthoringReviewRequest body) {
  try {
    Long ownerId = currentUser.requireUserId();
    Map<String, Object> result = authoringReviewService.review(
        projectId, ownerId,
        body == null ? null : body.provider(),
        body == null ? null : body.requirementsNotes(),
        body == null ? null : body.stories());
    return ResponseEntity.ok(result);
  } catch (IllegalStateException e) {
    // NO_GENERATED_WORKBOOK / provider empty
    return ResponseEntity.status(HttpStatus.NOT_FOUND) // or 503 for provider
        .body(new ApiError(...).asMap());
  } catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
  }
}
```

Map `NO_GENERATED_WORKBOOK` like other controllers. Provider unavailable → `503` + `PROVIDER_UNAVAILABLE`.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit** — skip unless asked

---

### Task 4: Generate UI — panel, banner, Accept/Discard

**Files:**
- Modify: `src/main/resources/templates/generate.html`
- Optionally light CSS in existing generate styles

**Interfaces:**
- Consumes: `POST .../authoring-review`; Accept → existing `POST .../generate/import` with `{ raw: csv, format: "csv" }` + coverage notes save if present
- Produces: Review UX per spec

- [ ] **Step 1: Add markup**

- Section `generate-authoring-review` (shown when workbook/csv preview exists):
  - Radio/select: Cursor | Ollama (localStorage key `keel.authoringReview.provider`)
  - Textarea `review-requirements-notes`
  - Button `Start review`
  - Status span
  - Findings `<ul>`
  - When preview returned: show proposed csv in existing preview OR secondary preview; buttons **Accept proposal** / **Discard**
- Banner `#authoring-review-banner` hidden by default; text “Issues found — Review with AI?” + button to scroll/focus panel

- [ ] **Step 2: Wire JS**

- On import/generate failure with `QUALITY_GATE` (existing error handling): show banner  
- Start review: POST with `provider`, `requirementsNotes`, `stories` from stories textarea if present  
- If `previewOk === false`: show `gateErrors`, disable Accept  
- Accept: call import with proposed csv; on success refresh workbook describe/preview; clear proposal  
- Discard: clear proposal state only  

- [ ] **Step 3: Manual check / GenerateMvcTest for marker ids if used elsewhere**

Assert HTML contains `authoring-review` id / “Review with AI” string in `GenerateMvcTest` if that test already checks coverage notes.

- [ ] **Step 4: Commit** — skip unless asked

---

### Task 5: Docs + verification

**Files:**
- Update README Generate section one short paragraph
- Keep spec Status: Approved / Implemented when done

- [ ] **Step 1: README blurb** — optional Review with AI (Cursor or Ollama) before Execute/Automate; Accept saves workbook

- [ ] **Step 2: Run focused suite**

```bash
mvn -q "-Dtest=AuthoringReviewParserTest,AuthoringReviewServiceTest,GenerateMvcTest" test
```

Expected: PASS (adjust test class names to what exists)

- [ ] **Step 3: Manual smoke checklist** (document in plan notes, user runs):
  1. Import suite with leave-empty TestData filled → gate/banner  
  2. Review with Ollama → Accept → Excel fixed  
  3. Review with Cursor (if configured) → Discard → Excel unchanged  
  4. Cursor disabled + provider cursor → clear error  

---

## Spec coverage checklist

| Spec item | Task |
|-----------|------|
| Parser contract | T1 |
| Cursor + Ollama user choice, no silent fallback | T2 |
| Soft cap 50 | T2 |
| API no save on review | T3 |
| Accept via existing import | T4 |
| Banner only | T4 |
| Stories + notes | T2/T4 (stories from Generate textarea) |
| Tests | T1–T3, T5 |

## Self-review

- Types consistent: `ParseResult`, `review(...)`, `AuthoringReviewRequest`.  
- Accept path locked to existing import (no duplicate save).  
- Stories v1 = optional request field from UI (no durable story store required).  
- No TBD placeholders.
