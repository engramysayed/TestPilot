# Guide AI rewrite → CSV prompt — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not commit unless the user asks.**

**Goal:** Replace `/tc-guide` §9 with a how-to + copyable static prompt that rewrites rough TCs into Keel-header CSV for Excel → Upload.

**Architecture:** Serve canonical prompt text from `static/prompts/keel-tc-rewrite-to-csv.txt`. Guide §9 fetches it, shows a scrollable preview, and copies to the clipboard. Permit `/prompts/**` like other static assets. No in-portal LLM.

**Tech Stack:** Spring Boot static resources, Thymeleaf `tc-guide.html`, `portal.css`, inline page JS, TestNG + MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-23-guide-ai-csv-prompt-design.md`

## Global Constraints

- Placement: §9 only (no sticky/hero copy).
- Job: rewrite only (not Generate-from-stories).
- Soft batch guidance: recommend 5–15 TCs per message.
- Output format: CSV with exact Keel headers (not ChatGPT `.xlsx`).
- Do not commit unless asked.
- Compile/test: `mvn -q "-Dmaven.compiler.release=21" -Dtest=… test`

---

## File map

| File | Responsibility |
|------|----------------|
| Create: `src/main/resources/static/prompts/keel-tc-rewrite-to-csv.txt` | Canonical AI prompt |
| Modify: `src/main/java/delivery/portal/security/SecurityConfig.java` | `permitAll` for `/prompts/**` |
| Modify: `src/main/resources/templates/tc-guide.html` | §9 + TOC + script |
| Modify: `src/main/resources/static/css/portal.css` | Prompt card styles (replace/extend `.ai-tip`) |
| Create: `src/test/java/delivery/portal/web/GuideAiCsvPromptResourceTest.java` | Classpath + HTTP smoke |

---

### Task 1: Prompt file + security + resource test

**Files:**
- Create: `src/main/resources/static/prompts/keel-tc-rewrite-to-csv.txt`
- Modify: `src/main/java/delivery/portal/security/SecurityConfig.java` (add `"/prompts/**"` next to `"/css/**"`)
- Create: `src/test/java/delivery/portal/web/GuideAiCsvPromptResourceTest.java`

**Interfaces:**
- Public URL: `GET /prompts/keel-tc-rewrite-to-csv.txt` → `200`, `text/plain` (charset may vary)
- Classpath: `static/prompts/keel-tc-rewrite-to-csv.txt`
- Header line must appear exactly once as the required CSV header in the prompt body

- [ ] **Step 1: Write the failing test**

```java
package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:guideaicsv;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-guide-ai",
        "delivery.work-dir=./target/test-delivery-work-guide-ai"
})
@AutoConfigureMockMvc
public class GuideAiCsvPromptResourceTest extends AbstractTestNGSpringContextTests {

    private static final String HEADER =
            "TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void classpathPrompt_containsKeelHeaderAndFidelityRules() throws Exception {
        try (var in = GuideAiCsvPromptResourceTest.class.getClassLoader()
                .getResourceAsStream("static/prompts/keel-tc-rewrite-to-csv.txt")) {
            Assert.assertNotNull(in, "prompt file missing on classpath");
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(body.contains(HEADER), "missing exact CSV header");
            Assert.assertTrue(body.toLowerCase().contains("csv"), "must require CSV output");
            Assert.assertTrue(body.contains("5–15") || body.contains("5-15"), "batch guidance");
            Assert.assertTrue(body.contains("TC_ID"), "preserve ids");
            Assert.assertTrue(body.toLowerCase().contains("testdata")
                    || body.contains("TestData"), "TestData rules");
            Assert.assertFalse(body.toLowerCase().contains("```csv"),
                    "prompt must not teach markdown fences as required output wrapper");
        }
    }

    @Test
    public void httpGet_prompts_isPublicAndReturnsBody() throws Exception {
        String body = mockMvc.perform(get("/prompts/keel-tc-rewrite-to-csv.txt"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains(HEADER));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=GuideAiCsvPromptResourceTest test
```

Expected: FAIL (missing resource and/or 302/401 on `/prompts/**`).

- [ ] **Step 3: Create the prompt file**

Create `src/main/resources/static/prompts/keel-tc-rewrite-to-csv.txt` with this exact content (UTF-8):

```text
You are rewriting manual software test cases into Keel’s Excel interchange format.

OUTPUT RULES
- Reply with CSV only. No markdown fences. No commentary before or after the CSV.
- First row must be exactly this header:
TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData
- Escape CSV correctly: wrap any field that contains a comma, double-quote, or newline in double quotes; escape internal quotes by doubling them.
- For multi-line Steps, ExpectedResult, and TestData, keep line breaks inside quoted fields.

FIDELITY (do not hallucinate)
- Rewrite ONLY the cases present in the user’s message. Do not invent extra TCs.
- Preserve existing TC_ID values. Do not renumber, merge, or drop cases unless the user asks.
- If the batch is large or incomplete, rewrite only what was pasted and ask for the next batch. Do not invent missing cases.
- Soft limit: prefer about 5–15 cases per message (or one feature/tag). If the user pasted more, still only output rows for what they pasted—do not pad with new cases.
- Do not invent real passwords or secrets. For shared login, use Preconditions like "Login required." and placeholders such as <USERNAME> / <PASSWORD> only if values are missing.
- Do not invent UI labels that were not in the input; keep exact visible labels from the draft when present.
- Do not create file-upload-only cases (choose file / attach / drag-and-drop upload).

AUTHORING RULES
- Steps: numbered list, one clear action per line; use exact UI labels (button text, field label, menu item).
- Prefer "Open the … page at /path" when navigation is needed.
- Move typed values out of Enter steps into TestData. Example Step: "Enter in the First name field" with TestData line "Nora".
- TestData is line-aligned with Steps (blank lines count). Line N of TestData pairs with step N.
- ExpectedResult: prefer one observable outcome per step, numbered to match Steps; quote exact UI messages when given.
- Preconditions: login need, role, starting context. Public pages: "No login required."
- Priority / Tags / VisualAssertion: keep if present; leave empty if unknown.
- VisualAssertion: short plain-language screen look claim only when useful; optional.

TINY SHAPE EXAMPLE (illustrative only — not extra cases to emit unless the user provided them)
TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData
TC_01,Valid login,"1. Open the Login Page at /login
2. Enter the username
3. Enter the password
4. Click the Login button","1. Login page is shown
2. Username is accepted
3. Password is accepted
4. Secure Area is visible",Login required.,P1,smoke,,"
<USERNAME>
<PASSWORD>
"

After these instructions, the user will paste their rough test cases. Rewrite that batch into CSV matching the header above.
```

- [ ] **Step 4: Permit static prompts**

In `SecurityConfig.java`, change the static matchers from:

```java
.requestMatchers(
        "/css/**", "/js/**", "/img/**",
        "/favicon.ico", "/favicon.svg",
        "/login", "/request-access", "/invite/**", "/error").permitAll()
```

to:

```java
.requestMatchers(
        "/css/**", "/js/**", "/img/**", "/prompts/**",
        "/favicon.ico", "/favicon.svg",
        "/login", "/request-access", "/invite/**", "/error").permitAll()
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=GuideAiCsvPromptResourceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit only if asked**

---

### Task 2: §9 UI + copy script + CSS

**Files:**
- Modify: `src/main/resources/templates/tc-guide.html` (TOC link `#ai`, section `#ai`, add `<script>` before `</body>`)
- Modify: `src/main/resources/static/css/portal.css` (replace `.ai-tip` block ~905–923 with prompt-card styles)

**Interfaces:**
- DOM ids: `guide-ai-prompt-preview`, `guide-ai-copy-btn`, `guide-ai-status`
- Prompt URL constant in script: `'/prompts/keel-tc-rewrite-to-csv.txt'`
- Consumes Task 1 public prompt URL

- [ ] **Step 1: Update TOC**

Change TOC entry from `9. AI rewrite tip` to `9. AI rewrite → CSV` (HTML entity for arrow: `AI rewrite &rarr; CSV` or literal `→`).

- [ ] **Step 2: Replace §9 markup**

Replace the entire `<section … id="ai">…</section>` with:

```html
            <section class="panel guide-section guide-reveal" id="ai">
                <header class="guide-section-head">
                    <span class="guide-num">09</span>
                    <div>
                        <h2>AI rewrite &rarr; CSV</h2>
                        <p class="panel-intro">Copy a ready prompt into any AI chat. The AI returns CSV (not .xlsx) with Keel headers — then open in Excel and Upload.</p>
                    </div>
                </header>
                <ol class="guide-howto">
                    <li>Click <strong>Copy prompt</strong>.</li>
                    <li>Paste into any AI chat (ChatGPT, Claude, Cursor, etc.).</li>
                    <li>Paste <strong>one batch</strong> of rough TCs (about <strong>5–15</strong>, or one feature/tag).</li>
                    <li>Copy the AI’s <strong>CSV only</strong> into Excel (or save as <code>.xlsx</code>).</li>
                    <li>Spot-check a few rows, then open Upload.</li>
                </ol>
                <p class="guide-howto-note">Large suites: repeat per batch and append rows in Excel. Do not paste the whole workbook at once.</p>
                <div class="ai-prompt-card">
                    <div class="ai-prompt-card-head">
                        <h3>Prompt</h3>
                        <button type="button" class="button" id="guide-ai-copy-btn" disabled>Copy prompt</button>
                    </div>
                    <pre id="guide-ai-prompt-preview" class="ai-prompt-preview" aria-live="polite">Loading prompt…</pre>
                    <p id="guide-ai-status" class="ai-prompt-status" role="status"></p>
                </div>
                <div class="ai-tip-actions">
                    <a class="button" href="/upload">Ready — open Upload</a>
                </div>
            </section>
```

- [ ] **Step 3: Add page script** before `</body>` in `tc-guide.html`

```html
<script>
(function () {
    var PROMPT_URL = '/prompts/keel-tc-rewrite-to-csv.txt';
    var preview = document.getElementById('guide-ai-prompt-preview');
    var copyBtn = document.getElementById('guide-ai-copy-btn');
    var statusEl = document.getElementById('guide-ai-status');
    if (!preview || !copyBtn || !statusEl) {
        return;
    }
    var promptText = '';

    function setStatus(msg, isError) {
        statusEl.textContent = msg || '';
        statusEl.classList.toggle('is-error', !!isError);
    }

    function loadPrompt() {
        return fetch(PROMPT_URL, { credentials: 'same-origin' })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('Could not load prompt (' + res.status + ')');
                }
                return res.text();
            })
            .then(function (text) {
                promptText = (text || '').trim();
                if (!promptText) {
                    throw new Error('Prompt file was empty');
                }
                preview.textContent = promptText;
                copyBtn.disabled = false;
                setStatus('', false);
            })
            .catch(function (err) {
                promptText = '';
                preview.textContent = 'Prompt failed to load. Open ' + PROMPT_URL + ' in a new tab, or refresh the page.';
                copyBtn.disabled = true;
                setStatus(err && err.message ? err.message : 'Failed to load prompt', true);
            });
    }

    copyBtn.addEventListener('click', function () {
        if (!promptText) {
            setStatus('Prompt not loaded yet', true);
            return;
        }
        var done = function () {
            copyBtn.textContent = 'Copied';
            setStatus('Copied — paste into your AI chat', false);
            window.setTimeout(function () {
                copyBtn.textContent = 'Copy prompt';
            }, 2000);
        };
        var fail = function () {
            setStatus('Clipboard blocked — select the prompt text and press Ctrl+C (Cmd+C on Mac)', true);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(promptText).then(done).catch(fail);
        } else {
            fail();
        }
    });

    loadPrompt();
})();
</script>
```

- [ ] **Step 4: CSS** — replace `.ai-tip { … }` through `.ai-tip .button { margin: 0; }` with:

```css
.guide-howto {
    margin: 0 0 0.75rem;
    padding-left: 1.25rem;
    color: var(--muted);
    font-size: 0.92rem;
    line-height: 1.55;
}
.guide-howto li { margin: 0.35rem 0; }
.guide-howto-note {
    margin: 0 0 1rem;
    color: var(--muted);
    font-size: 0.88rem;
    line-height: 1.45;
}
.ai-prompt-card {
    border-radius: 12px;
    border: 1px solid rgba(58, 160, 232, 0.28);
    background: rgba(58, 160, 232, 0.08);
    padding: 0.85rem 1rem 1rem;
}
.ai-prompt-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    flex-wrap: wrap;
    margin-bottom: 0.65rem;
}
.ai-prompt-card-head h3 {
    margin: 0;
    font-size: 0.95rem;
}
.ai-prompt-preview {
    margin: 0;
    max-height: 16rem;
    overflow: auto;
    padding: 0.75rem 0.85rem;
    border-radius: 8px;
    background: rgba(0, 0, 0, 0.22);
    border: 1px solid rgba(255, 255, 255, 0.06);
    color: var(--text);
    font-size: 0.78rem;
    line-height: 1.45;
    white-space: pre-wrap;
    word-break: break-word;
}
.ai-prompt-status {
    margin: 0.55rem 0 0;
    min-height: 1.2em;
    font-size: 0.85rem;
    color: var(--muted);
}
.ai-prompt-status.is-error { color: #f0b4b4; }
.ai-tip-actions {
    margin-top: 1rem;
    display: flex;
    flex-wrap: wrap;
    gap: 0.6rem;
}
```

- [ ] **Step 5: Manual verify** (portal running, logged in)

1. Open `/tc-guide` → §9 title **AI rewrite → CSV**.
2. Preview fills with prompt text (not stuck on “Loading…”).
3. **Copy prompt** → paste into Notepad → matches file.
4. Upload link still works.
5. Optional: DevTools → block clipboard → error message appears.

- [ ] **Step 6: Re-run resource test**

```bash
mvn -q "-Dmaven.compiler.release=21" -Dtest=GuideAiCsvPromptResourceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit only if asked**

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| §9 only placement | Task 2 |
| Rewrite-only prompt | Task 1 prompt text |
| Soft 5–15 batch | Task 1 + Task 2 how-to |
| Static `.txt` path | Task 1 |
| Preview + Copy + Upload | Task 2 |
| Exact Keel headers + fidelity rules | Task 1 |
| Fetch/clipboard errors | Task 2 script |
| `/prompts` smoke test | Task 1 |
| No in-portal AI / no P5 motion | Not in plan |

**Placeholder scan:** none.  
**Type consistency:** single prompt URL `/prompts/keel-tc-rewrite-to-csv.txt` everywhere.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-23-guide-ai-csv-prompt.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — implement in this session with checkpoints  

Which approach?
