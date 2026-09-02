# Job Activity Loader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show that Keel is working while jobs are `QUEUED`/`RUNNING` on Job status (spinner, moving bar, elapsed), Jobs/Dashboard badges, and a sidebar chip on every app page.

**Architecture:** Reuse existing `.spinner` and indeterminate progress CSS. Job status extends the current poll `render()`. Lists are server-rendered. Sidebar polls existing `GET /api/jobs` every 3s. No new endpoints.

**Tech Stack:** Thymeleaf, `portal.css`, vanilla JS, TestNG MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-31-job-activity-loader-design.md`

## Global Constraints

- Active statuses: `QUEUED` and `RUNNING` only
- New API: none — sidebar uses `GET /api/jobs`
- Job status bar: indeterminate while active, even at `1 / 1`; determinate 100% only after terminal completed statuses
- Elapsed copy: `Working for m:ss` (minutes unbounded); hide when done; timer restarts on page load
- Chip, one job: `Generating…` / `Executing…` / `Comparing…` / `Converting…` from `jobKind`
- Chip, many: `N jobs running`; still `href="/status?jobId={newest}"`
- Chip `aria-label`: visible label plus ` Open job status`
- Poll: every 3s; skip while `document.hidden`
- Generate page queue spinner: leave as-is
- Overlay / toasts / WebSockets: out
- Do not commit unless the user asks

---

### Task 1: Job status busy UI

**Files:**
- Modify: `src/test/java/delivery/portal/web/StatusPageMvcTest.java`
- Modify: `src/main/resources/templates/status.html`
- Modify: `src/main/resources/static/css/portal.css` (after `.spinner` / `.progress-fill--indeterminate` block around lines 1426–1450)

**Interfaces:**
- Consumes: existing `jobStatus`, `#progress-bar`, poll `render(data)` returning done boolean
- Produces: `#job-busy-spinner`, `#job-elapsed`, `#progress-track`, `#live-progress` with `aria-busy`; CSS `.live-progress-head`, `.job-elapsed`

- [ ] **Step 1: Write the failing test**

Add this method to `StatusPageMvcTest` (same createProject / auth helpers). Seed a `RUNNING` generate-batch job:

```java
@Test
public void statusPage_runningGenerateBatch_showsBusySpinnerAndElapsed() throws Exception {
    String projectId = createProject();
    Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
    String jobId = "genb_status_busy_mvc";
    JobEntity job = new JobEntity();
    job.setJobId(jobId);
    job.setProjectId(projectId);
    job.setOwnerUserId(ownerId);
    job.setMode("GENERATE_BATCH");
    job.setJobKind("GENERATE_BATCH");
    job.setStatus("RUNNING");
    job.setMessage("Generating US_ASYNC_001 — story");
    job.setPassedCount(0);
    job.setTodoCount(0);
    job.setProgressCurrent(1);
    job.setProgressTotal(1);
    job.setCreatedAt(Instant.now());
    jobRepository.save(job);

    String body = mockMvc.perform(get("/status").param("jobId", jobId)
                    .with(httpBasic(AUTH_USER, AUTH_PASS)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Assert.assertTrue(body.contains("id=\"job-busy-spinner\""), "busy spinner missing");
    Assert.assertTrue(body.contains("id=\"job-elapsed\""), "elapsed line missing");
    Assert.assertTrue(body.contains("aria-busy=\"true\""), "live progress should be aria-busy while RUNNING");
    Assert.assertTrue(body.contains("progress-track--indeterminate"), "bar should be indeterminate while RUNNING");
    int spinner = body.indexOf("id=\"job-busy-spinner\"");
    String spinnerTag = body.substring(spinner, body.indexOf('>', spinner));
    Assert.assertFalse(spinnerTag.contains("hidden"), "spinner must be visible while RUNNING");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=delivery.portal.web.StatusPageMvcTest" test`

Expected: FAIL — `busy spinner missing` (ids not in `status.html`).

- [ ] **Step 3: Markup + CSS + poll toggle**

In `status.html`, replace the Live progress heading + bar with:

```html
<section class="panel" id="live-progress"
         th:attr="aria-busy=${jobStatus == 'QUEUED' or jobStatus == 'RUNNING'}">
    <div class="live-progress-head">
        <span id="job-busy-spinner" class="spinner"
              th:hidden="${jobStatus != 'QUEUED' and jobStatus != 'RUNNING'}"
              aria-hidden="true"></span>
        <div>
            <h2>Live progress</h2>
            <p class="panel-intro" th:if="${isExecuteJob}">This page polls until the execute run finishes.</p>
            <p class="panel-intro" th:if="${isGenerateBatchJob}">This page polls while each user story is generated one-by-one.</p>
            <p class="panel-intro" th:if="${isGenerateCompareJob}">This page polls while both models generate test cases in the background.</p>
            <p class="panel-intro" th:if="${!isExecuteJob and !isGenerateBatchJob and !isGenerateCompareJob}">This page polls until the job finishes.</p>
        </div>
    </div>
    <div id="progress-track" class="progress-track"
         th:classappend="${jobStatus == 'QUEUED' or jobStatus == 'RUNNING'} ? ' progress-track--indeterminate'">
        <div id="progress-bar" class="progress-fill"
             th:classappend="${jobStatus == 'QUEUED' or jobStatus == 'RUNNING'} ? ' progress-fill--indeterminate'"
             th:style="'width:' + ${progressPercent} + '%'"></div>
    </div>
    <p id="job-elapsed" class="muted job-elapsed"
       th:hidden="${jobStatus != 'QUEUED' and jobStatus != 'RUNNING'}">Working for 0:00</p>
```

Keep the existing `<dl class="status">` and the rest of the section. Close the same `</section>`.

In the status page script, after `const bar = ...`, add:

```javascript
    const liveProgress = document.getElementById('live-progress');
    const track = document.getElementById('progress-track');
    const busySpinner = document.getElementById('job-busy-spinner');
    const elapsedEl = document.getElementById('job-elapsed');
    let elapsedTimer = null;
    let elapsedStart = Date.now();

    function isActiveStatus(status) {
        return status === 'QUEUED' || status === 'RUNNING';
    }

    function formatElapsed(ms) {
        const totalSec = Math.max(0, Math.floor(ms / 1000));
        const m = Math.floor(totalSec / 60);
        const s = totalSec % 60;
        return m + ':' + String(s).padStart(2, '0');
    }

    function setBusy(active) {
        if (liveProgress) {
            liveProgress.setAttribute('aria-busy', active ? 'true' : 'false');
        }
        if (busySpinner) {
            busySpinner.hidden = !active;
        }
        if (track) {
            track.classList.toggle('progress-track--indeterminate', active);
        }
        if (bar) {
            bar.classList.toggle('progress-fill--indeterminate', active);
        }
        if (elapsedEl) {
            elapsedEl.hidden = !active;
        }
        if (active) {
            if (!elapsedTimer) {
                elapsedStart = Date.now();
                elapsedEl.textContent = 'Working for 0:00';
                elapsedTimer = setInterval(function () {
                    elapsedEl.textContent = 'Working for ' + formatElapsed(Date.now() - elapsedStart);
                }, 1000);
            }
        } else if (elapsedTimer) {
            clearInterval(elapsedTimer);
            elapsedTimer = null;
        }
    }

    setBusy(isActiveStatus(root && root.getAttribute('data-initial-status')));
```

Remove the later duplicate `function isActiveStatus` if one already exists (keep a single definition).

At the start of `render(data)`, after writing status text, call `setBusy(isActiveStatus(data.status || ''));`. When computing `pct`, if active, do not rely on a full bar looking “done” — indeterminate CSS uses `width: 35% !important`. When terminal completed (`COMPLETED` or `COMPLETED_WITH_BLOCK`), set `bar.style.width = '100%'`. On `FAILED`/`CANCELLED`, keep last `pct`.

Add CSS after `.progress-fill--indeterminate`:

```css
.live-progress-head {
    display: flex;
    align-items: flex-start;
    gap: 0.85rem;
    margin-bottom: 0.85rem;
}
.live-progress-head h2 { margin: 0 0 0.25rem; }
.live-progress-head .panel-intro { margin: 0; }
.job-elapsed {
    margin: 0 0 0.85rem;
    font-size: 0.82rem;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q "-Dtest=delivery.portal.web.StatusPageMvcTest" test`

Expected: PASS (both existing execute-job test and new busy test).

---

### Task 2: Jobs and Dashboard running badges

**Files:**
- Modify: `src/test/java/delivery/portal/web/JobsPageMvcTest.java`
- Modify: `src/main/resources/templates/jobs.html`
- Modify: `src/main/resources/templates/dashboard.html`
- Modify: `src/main/resources/static/css/portal.css`
- Test: `src/test/java/delivery/portal/web/DashboardMvcTest.java` only if a dashboard HTML assert already exists and would break; otherwise JobsPageMvcTest is enough plus a small dashboard string assert in `DashboardMvcTest` if it GETs `/dashboard`.

**Interfaces:**
- Consumes: Thymeleaf `j.status`; CSS `.spinner`
- Produces: `.spinner.spinner--badge` inside `QUEUED`/`RUNNING` badges

- [ ] **Step 1: Write the failing test**

In `JobsPageMvcTest`, add `JobRepository` and `PortalUserRepository` autowires (same as `StatusPageMvcTest`). Add:

```java
@Test
public void jobsPage_runningConversion_showsBadgeSpinner() throws Exception {
    String projectId = createProject();
    Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
    JobEntity job = new JobEntity();
    job.setJobId("conv_jobs_busy_mvc");
    job.setProjectId(projectId);
    job.setOwnerUserId(ownerId);
    job.setMode("NEW");
    job.setJobKind("CONVERT");
    job.setStatus("RUNNING");
    job.setProgressCurrent(1);
    job.setProgressTotal(3);
    job.setCreatedAt(Instant.now());
    jobRepository.save(job);

    String body = mockMvc.perform(get("/jobs").with(httpBasic(AUTH_USER, AUTH_PASS)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Assert.assertTrue(body.contains("conv_jobs_busy_mvc"));
    Assert.assertTrue(body.contains("spinner--badge"), "running job badge should include spinner");
}
```

Add imports: `JobEntity`, `JobRepository`, `PortalUserRepository`, `Instant`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=delivery.portal.web.JobsPageMvcTest" test`

Expected: FAIL — `running job badge should include spinner`.

- [ ] **Step 3: Badge markup + CSS**

`jobs.html` status cell — match dashboard warn styling for running/queued and insert spinner:

```html
<td>
    <span class="badge"
          th:classappend="${j.status == 'COMPLETED'} ? ' ok' : (${j.status == 'COMPLETED_WITH_BLOCK'} ? ' warn' : (${j.status == 'FAILED'} ? ' err' : (${j.status == 'RUNNING' or j.status == 'QUEUED'} ? ' warn badge--busy' : ' muted')))">
        <span class="spinner spinner--badge" th:if="${j.status == 'RUNNING' or j.status == 'QUEUED'}" aria-hidden="true"></span>
        <span th:text="${j.status}">QUEUED</span>
    </span>
</td>
```

`dashboard.html` recent-jobs status cell (same spinner + `badge--busy` on the existing RUNNING/QUEUED warn branch).

CSS:

```css
.badge--busy {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
}
.spinner--badge {
    width: 0.7rem;
    height: 0.7rem;
    border-width: 2px;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q "-Dtest=delivery.portal.web.JobsPageMvcTest,delivery.portal.web.DashboardMvcTest" test`

Expected: PASS.

---

### Task 3: Sidebar activity chip

**Files:**
- Modify: `src/test/java/delivery/portal/web/AutomateShellMvcTest.java`
- Modify: `src/main/resources/templates/fragments.html`
- Modify: `src/main/resources/static/css/portal.css` (near `.sidebar-foot`)

**Interfaces:**
- Consumes: `GET /api/jobs` JSON array with `jobId`, `jobKind`, `status`, `createdAt`
- Produces: `#sidebar-job-chip` (starts `hidden`), `#sidebar-job-chip-label`

- [ ] **Step 1: Write the failing test**

In `sidebar_hasAutomateGroup_notTopLevelUploadJobs` (or a new method `sidebar_hasJobActivityChip`):

```java
@Test
public void sidebar_hasJobActivityChip() throws Exception {
    String body = mockMvc.perform(get("/dashboard").with(httpBasic(AUTH_USER, AUTH_PASS)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Assert.assertTrue(body.contains("id=\"sidebar-job-chip\""));
    Assert.assertTrue(body.contains("id=\"sidebar-job-chip-label\""));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=delivery.portal.web.AutomateShellMvcTest#sidebar_hasJobActivityChip" test`

Expected: FAIL — `id="sidebar-job-chip"` missing.

- [ ] **Step 3: Chip markup, CSS, poller**

Insert **above** `.sidebar-foot` in `fragments.html`:

```html
    <a id="sidebar-job-chip" class="sidebar-job-chip" hidden href="/status" aria-label="Open job status">
        <span class="spinner spinner--badge" aria-hidden="true"></span>
        <span id="sidebar-job-chip-label">Working…</span>
    </a>
```

Add a second IIFE after the automate-nav script (same sidebar fragment):

```javascript
        (function () {
            var chip = document.getElementById('sidebar-job-chip');
            var labelEl = document.getElementById('sidebar-job-chip-label');
            if (!chip || !labelEl) return;
            var ACTIVE = { QUEUED: true, RUNNING: true };
            function kindLabel(kind) {
                if (kind === 'GENERATE_BATCH') return 'Generating…';
                if (kind === 'EXECUTE') return 'Executing…';
                if (kind === 'GENERATE_COMPARE') return 'Comparing…';
                return 'Converting…';
            }
            function hide() {
                chip.hidden = true;
            }
            function show(href, text) {
                chip.hidden = false;
                chip.href = href;
                labelEl.textContent = text;
                chip.setAttribute('aria-label', text + ' Open job status');
            }
            async function tick() {
                if (document.hidden) {
                    schedule();
                    return;
                }
                try {
                    var res = await fetch('/api/jobs', {
                        credentials: 'same-origin',
                        headers: { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' }
                    });
                    if (!res.ok) {
                        hide();
                        schedule();
                        return;
                    }
                    var rows = await res.json();
                    if (!Array.isArray(rows)) {
                        hide();
                        schedule();
                        return;
                    }
                    var active = rows.filter(function (j) {
                        return j && ACTIVE[j.status];
                    }).sort(function (a, b) {
                        var ac = a.createdAt || '';
                        var bc = b.createdAt || '';
                        if (ac === bc) return 0;
                        if (!ac) return 1;
                        if (!bc) return -1;
                        return ac < bc ? 1 : -1;
                    });
                    if (active.length === 0) {
                        hide();
                    } else {
                        var newest = active[0];
                        var href = '/status?jobId=' + encodeURIComponent(newest.jobId);
                        var text = active.length === 1
                            ? kindLabel(newest.jobKind)
                            : (active.length + ' jobs running');
                        show(href, text);
                    }
                } catch (e) {
                    hide();
                }
                schedule();
            }
            function schedule() {
                setTimeout(tick, 3000);
            }
            tick();
        })();
```

CSS near `.sidebar-foot`:

```css
.sidebar-job-chip {
    display: flex;
    align-items: center;
    gap: 0.45rem;
    margin: 0 0.35rem 0.65rem;
    padding: 0.45rem 0.65rem;
    border-radius: 10px;
    text-decoration: none;
    color: var(--sidebar-muted);
    background: rgba(58, 160, 232, 0.12);
    border: 1px solid rgba(58, 160, 232, 0.28);
    font-size: 0.78rem;
    font-weight: 600;
}
.sidebar-job-chip[hidden] { display: none !important; }
.sidebar-job-chip:hover { color: inherit; }
```

If `.sidebar-job-chip { display: flex }` fights `[hidden]`, the `[hidden] { display: none !important; }` rule is required.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q "-Dtest=delivery.portal.web.AutomateShellMvcTest,delivery.portal.web.StatusPageMvcTest,delivery.portal.web.JobsPageMvcTest,delivery.portal.web.DashboardMvcTest" test`

Expected: PASS.

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| Job status spinner, aria-busy, elapsed, indeterminate bar | 1 |
| Jobs + Dashboard badge spinners | 2 |
| Sidebar chip + GET /api/jobs poll + 3s + document.hidden | 3 |
| Kind labels + N jobs running → newest status URL | 3 |
| Poll error hides chip | 3 |
| No new API / no overlay | all |
| MVC tests named in spec §8 | 1–3 |
| Generate page unchanged | — (no task) |
