# Nav IA + Automate shell — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not commit unless the user asks.**

**Goal:** Restructure sidebar to Generate | Execute | Automate (nested Upload/Jobs), add `/automate` hub with project picker + recent jobs, and coming-soon pages for Generate/Execute.

**Architecture:** Extend `fragments.html` with expandable nav group + `navActive` prefixes; three new Thymeleaf pages; optional `projectId` filter on `GET /api/jobs`; update `PortalUiController` navActive values for upload/jobs/status.

**Tech Stack:** Spring Boot MVC, Thymeleaf, portal.css, TestNG + MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-23-nav-automate-shell-design.md`

## Global Constraints

- Keep `/upload`, `/jobs`, `/status` URLs unchanged.
- Sidebar: Generate, Execute, Automate (Upload + Jobs nested); remove top-level Upload/Jobs.
- `/automate`: project picker, two cards, last 5 jobs for selected project.
- `navActive`: `automate`, `automate-upload`, `automate-jobs`, `generate`, `execute`.
- `GET /api/jobs?projectId=` — empty list if unknown/unowned project (no id leak).
- Do not commit unless asked.
- Tests: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=…" test`

---

## File map

| File | Responsibility |
|------|----------------|
| Modify: `templates/fragments.html` | Nav groups + expand JS |
| Modify: `static/css/portal.css` | Nav group, automate hub, coming-soon |
| Create: `templates/automate.html` | Hub page |
| Create: `templates/generate.html` | Coming soon |
| Create: `templates/execute.html` | Coming soon |
| Modify: `PortalUiController.java` | Routes + navActive |
| Modify: `JobController.java` | `projectId` query filter |
| Modify: `upload.html`, `jobs.html` | Back links / context |
| Create: `AutomateShellMvcTest.java` | Page smoke |
| Create/extend: `JobListFilterApiTest.java` | API filter |

---

### Task 1: Jobs API project filter

**Files:**
- Modify: `src/main/java/delivery/portal/api/JobController.java`
- Create: `src/test/java/delivery/portal/api/JobListFilterApiTest.java`

**Interfaces:**
- `GET /api/jobs?projectId=prj_x` → only jobs where `job.projectId` equals param
- Unknown/unowned `projectId` → `200` with `[]` (not 404)

- [ ] **Step 1: Write failing test**

```java
package delivery.portal.api;

import delivery.portal.PortalApplication;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:jobfilter;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-jobfilter",
        "delivery.work-dir=./target/test-delivery-work-jobfilter"
})
@AutoConfigureMockMvc
public class JobListFilterApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void listJobs_filtersByProjectId() throws Exception {
        String p1 = createProject("P1");
        String p2 = createProject("P2");
        patchBaseUrl(p1);
        patchBaseUrl(p2);
        startJob(p1);
        startJob(p2);

        mockMvc.perform(get("/api/jobs?projectId=" + p1)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(p1));
    }

    @Test
    public void listJobs_unknownProject_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/jobs?projectId=prj_nonexistent")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String createProject(String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    private void patchBaseUrl(String projectId) throws Exception {
        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());
    }

    private void startJob(String projectId) throws Exception {
        byte[] excel = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile file = new MockMultipartFile("excel", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(file)
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted());
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -q "-Dmaven.compiler.release=21" "-Dtest=JobListFilterApiTest" test
```

- [ ] **Step 3: Implement filter in `JobController.listJobs`**

Add `@RequestParam(value = "projectId", required = false) String projectId`.

After loading owned jobs, if `projectId` non-blank:
- If `store.getOwnedProject(projectId, ownerId).isEmpty()` → return `List.of()`
- Else filter stream where `e.getProjectId().equals(projectId)`

Keep newest-first sort (match existing order).

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit only if asked**

---

### Task 2: Sidebar nav groups

**Files:**
- Modify: `src/main/resources/templates/fragments.html`
- Modify: `src/main/resources/static/css/portal.css`

**Interfaces:**
- `navActive` values: `generate`, `execute`, `automate`, `automate-upload`, `automate-jobs`
- Parent Automate row has class `active` when `navActive` starts with `automate`
- Sub-nav visible when `navActive` starts with `automate` OR `sessionStorage keel.automateNavOpen === '1'`

- [ ] **Step 1: Replace Workspace links in `fragments.html`**

Order: Dashboard, Projects, Generate (`/generate`), Execute (`/execute`), Automate group:

```html
<div class="nav-group" th:classappend="${navActive != null and navActive.startsWith('automate')} ? ' is-open' : ''"
     data-nav-group="automate">
  <a href="/automate" class="nav-group-head"
     th:classappend="${navActive != null and navActive.startsWith('automate')} ? 'active'">
    <!-- icon --> Automate <span class="nav-chevron" aria-hidden="true"></span>
  </a>
  <div class="nav-group-children">
    <a href="/upload" th:classappend="${navActive == 'automate-upload'} ? 'active'">Upload</a>
    <a href="/jobs" th:classappend="${navActive == 'automate-jobs'} ? 'active'">Jobs</a>
  </div>
</div>
```

Remove standalone Upload and Jobs links. Keep Guide after Automate group.

Add script at end of sidebar fragment:

```javascript
(function () {
  var KEY = 'keel.automateNavOpen';
  var group = document.querySelector('[data-nav-group="automate"]');
  if (!group) return;
  var head = group.querySelector('.nav-group-head');
  if (sessionStorage.getItem(KEY) === '1' || group.classList.contains('is-open')) {
    group.classList.add('is-open');
  }
  if (head) {
    head.addEventListener('click', function (e) {
      if (e.target.closest('.nav-group-children')) return;
      if (e.metaKey || e.ctrlKey) return;
      if (head.getAttribute('href') === '/automate' && !group.classList.contains('is-open')) {
        e.preventDefault();
        group.classList.add('is-open');
        sessionStorage.setItem(KEY, '1');
        return;
      }
      group.classList.toggle('is-open');
      sessionStorage.setItem(KEY, group.classList.contains('is-open') ? '1' : '0');
    });
  }
})();
```

(Chevron click toggles; first click on closed group opens without navigating — refine in implementation.)

- [ ] **Step 2: CSS** — add `.nav-group`, `.nav-group-children`, `.nav-chevron`, indent children

- [ ] **Step 3: Manual** — load any page; verify Generate/Execute/Automate visible; Upload/Jobs nested

- [ ] **Step 4: Commit only if asked**

---

### Task 3: Controller routes + navActive

**Files:**
- Modify: `src/main/java/delivery/portal/web/PortalUiController.java`

- [ ] **Step 1: Add routes**

```java
@GetMapping("/automate")
public String automate(Model model) {
    addNav(model);
    model.addAttribute("navActive", "automate");
    return "automate";
}

@GetMapping("/generate")
public String generate(Model model) {
    addNav(model);
    model.addAttribute("navActive", "generate");
    return "generate";
}

@GetMapping("/execute")
public String execute(Model model) {
    addNav(model);
    model.addAttribute("navActive", "execute");
    return "execute";
}
```

- [ ] **Step 2: Update existing**

- `upload()` → `navActive` = `"automate-upload"`
- `jobs()` → `navActive` = `"automate-jobs"`
- `status()` → `navActive` = `"automate-jobs"`

- [ ] **Step 3: Commit only if asked**

---

### Task 4: New pages (automate, generate, execute)

**Files:**
- Create: `src/main/resources/templates/automate.html`
- Create: `src/main/resources/templates/generate.html`
- Create: `src/main/resources/templates/execute.html`
- Modify: `portal.css` (hub cards, coming-soon badge)

**Interfaces:**
- `automate.html` JS: load `GET /api/projects`, picker `keel.automateProjectId`, `GET /api/jobs?projectId=`, slice 5, render table
- Card links: `/upload?projectId=`, `/jobs`
- `generate.html` / `execute.html`: static copy per spec §5.2–5.3

- [ ] **Step 1: Create `generate.html` and `execute.html`** (minimal panels + coming-soon badge + CTAs)

- [ ] **Step 2: Create `automate.html`** with project select, two cards, recent jobs table, empty state

- [ ] **Step 3: CSS** for `.automate-cards`, `.coming-soon-badge`

- [ ] **Step 4: Manual** — `/automate` picker changes jobs list

- [ ] **Step 5: Commit only if asked**

---

### Task 5: Cross-page links + MVC tests

**Files:**
- Modify: `src/main/resources/templates/upload.html` — add `<p class="muted"><a href="/automate">← Automate</a></p>` under title
- Modify: `src/main/resources/templates/jobs.html` — optional muted “Part of Automate” under lede
- Create: `src/test/java/delivery/portal/web/AutomateShellMvcTest.java`

- [ ] **Step 1: Write test**

```java
@SpringBootTest(classes = PortalApplication.class, properties = { /* same as PortalApiTest */ })
@AutoConfigureMockMvc
public class AutomateShellMvcTest extends AbstractTestNGSpringContextTests {
    @Autowired MockMvc mockMvc;

    @Test
    public void automatePages_okWhenAuthenticated() throws Exception {
        for (String path : List.of("/automate", "/generate", "/execute")) {
            mockMvc.perform(get(path)
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                    .andExpect(status().isOk());
        }
    }
}
```

- [ ] **Step 2: Run full suite**

```bash
mvn -q "-Dmaven.compiler.release=21" "-Dtest=AutomateShellMvcTest,JobListFilterApiTest,PortalApiTest" test
```

Expected: PASS

- [ ] **Step 3: Commit only if asked**

---

## Spec coverage (self-review)

| Requirement | Task |
|-------------|------|
| Nested Automate nav | 2 |
| Generate/Execute coming soon | 3, 4 |
| `/automate` hub | 4 |
| Jobs API filter | 1 |
| navActive wiring | 3 |
| Upload/Jobs URLs unchanged | 3, 5 |
| Back links | 5 |

**Placeholder scan:** none.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-23-nav-automate-shell.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — implement in this session with checkpoints  

Which approach?
