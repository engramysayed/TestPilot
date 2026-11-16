# Post-roadmap hardening — Fix-all plan

> **For agentic workers:** Use `superpowers:subagent-driven-development` task-by-task.  
> **Do not commit unless the user asks.**

**Goal:** Fix defects, IA conflicts, and gaps found in the P0–P8 audit (execute vs conversion job mixing, bogus ZIP downloads, status page, bug report, copy, project UX, docs).

**Architecture:** Centralize job “downloadable” semantics on `JobRecord` + `jobKind`; filter conversion surfaces to `CONVERT`; branch execute UX on `/status` and dashboard; extend bug report and project Settings for design refs; re-enable conversion screenshots with same allowlist pattern as execute.

**Spec:** Audit findings from 2026-08-23 conversation (no separate design doc — this plan is authoritative).

## Global Constraints

- Keep `jobKind`: `CONVERT` | `EXECUTE` on shared `jobs` table.
- `/jobs` under Automate = **conversion history only** (`CONVERT`).
- Execute history surfaced on `/execute` via `GET /api/jobs?kind=EXECUTE`.
- Do not commit unless asked.
- Tests: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=…" test`
- PowerShell: quote Maven `-Dtest` when comma-separated.

---

## File map

| Area | Files |
|------|--------|
| Job semantics | `JobRecord.java`, `JobController.java`, `DashboardService.java` |
| MVC / UI | `PortalUiController.java`, `jobs.html`, `status.html`, `dashboard.html`, `automate.html`, `execute.html`, `tc-guide.html`, `projects.html`, `project-detail.html` |
| Bug report | `BugReportService.java`, `ExecuteRunService.java` |
| Screenshots | `ProjectController.java`, `ProjectTcService.java` |
| Design refs | `project-detail.html` (Settings tab) |
| Schema | `JobsSchemaPatch.java` (backfill `job_kind`) |
| Docs | `2026-08-23-keel-product-roadmap.md` |
| Tests | New/updated API + MVC tests |

---

## Locked decisions (audit resolutions)

| Topic | Decision |
|-------|----------|
| Downloadable | Only `CONVERT` jobs with terminal status **and** resolvable ZIP |
| Dashboard metrics | **Conversion-only** (`CONVERT`) for counts, chart, recent jobs table |
| `/jobs` page | `CONVERT` only; link to `/execute` for execute history |
| `/status` | Branch on `jobKind`: execute → results/bug-report CTAs; hide ZIP + final-revise banner |
| Bug report rows | `qaStatus == FAIL` **OR** `designCompareStatus == MISMATCH` |
| Conversion screenshots | **Re-enable** `GET .../screenshots/{file}` with existing path allowlist (same as pre-P2 behavior, ownership-scoped) |
| Design references UI | Settings tab on project detail: list + upload (reuse design-references API) |
| Copy | “Upload” → “Automate” where it means starting a conversion |

---

### Task 1: Job downloadable semantics (P0)

**Files:**
- Modify: `src/main/java/delivery/portal/model/JobRecord.java`
- Modify: `src/main/java/delivery/portal/api/JobController.java`
- Modify: `src/main/java/delivery/portal/service/DashboardService.java`
- Create: `src/test/java/delivery/portal/model/JobRecordDownloadableTest.java`

**Interfaces:**
- Add `JobRecord.isDownloadable(JobKind kind, Status status)` and overload with `String status`
- `CONVERT` + `COMPLETED`/`COMPLETED_WITH_BLOCK` → true (status-only, ZIP resolved at download time)
- `EXECUTE` → always false for download
- Deprecate direct status-only calls in portal code; migrate callers in Tasks 1–4

**Steps:**

- [ ] **Step 1: Failing test**

```java
@Test
public void executeCompleted_isNotDownloadable() {
    Assert.assertFalse(JobRecord.isDownloadable(JobRecord.JobKind.EXECUTE, JobRecord.Status.COMPLETED));
}
@Test
public void convertCompleted_isDownloadable() {
    Assert.assertTrue(JobRecord.isDownloadable(JobRecord.JobKind.CONVERT, JobRecord.Status.COMPLETED));
}
```

- [ ] **Step 2: Implement overloads; keep old methods delegating `CONVERT` for backward compat in non-portal code**
- [ ] **Step 3: JobController** `listJobs` / `getJob` — `downloadable` uses `job.getJobKind()` + status; `download` endpoint returns `409` if `jobKind != CONVERT`
- [ ] **Step 4: DashboardService** — `downloadable` row field uses entity `jobKind` + status
- [ ] **Step 5: Run** `JobRecordDownloadableTest`, `DashboardMvcTest`

---

### Task 2: Filter conversion surfaces (P0)

**Files:**
- Modify: `src/main/java/delivery/portal/web/PortalUiController.java` (`jobs()`)
- Modify: `src/main/resources/templates/jobs.html`
- Modify: `src/main/resources/templates/automate.html`
- Modify: `src/main/java/delivery/portal/service/DashboardService.java`
- Create: `src/test/java/delivery/portal/web/JobsPageMvcTest.java`

**Steps:**

- [ ] **Step 1:** `jobs()` — pass only `CONVERT` jobs (`stream().filter(CONVERT)`)
- [ ] **Step 2:** `jobs.html` — update empty copy (“Start from Automate”); remove bogus download preconditions if any remain
- [ ] **Step 3:** `automate.html` — fetch `/api/jobs?projectId=&kind=CONVERT`
- [ ] **Step 4:** `DashboardService.statsFor` — filter `jobList` to `CONVERT` for counts, chart, `recentJobs` (keep `runningJobs` counting **both** kinds — operational truth)
- [ ] **Step 5:** `JobsPageMvcTest` — page has no `exec_` job ids when only execute job exists in DB (seed via API)

---

### Task 3: Status page execute branch (P1)

**Files:**
- Modify: `src/main/java/delivery/portal/web/PortalUiController.java` (`status()`)
- Modify: `src/main/resources/templates/status.html`
- Modify: `src/main/java/delivery/portal/api/JobController.java` (`getJob` — ensure `jobKind` in JSON)
- Create: `src/test/java/delivery/portal/web/StatusPageMvcTest.java`

**Steps:**

- [ ] **Step 1:** `status()` — add `jobKind` model attribute from owned job
- [ ] **Step 2:** `status.html` — if `jobKind == EXECUTE`: hide `#download-wrap`, `#block-banner`; show “View results on Execute” → `/execute` (with note to re-open last run) + link to bug-report CSV; poll script reads `data-job-kind`
- [ ] **Step 3:** Poll handler: if `data.jobKind === 'EXECUTE'`, skip ZIP show logic
- [ ] **Step 4:** `StatusPageMvcTest` — mock execute job id in model or render with attribute; assert no Download ZIP for EXECUTE

---

### Task 4: Bug report includes design mismatches (P1)

**Files:**
- Modify: `src/main/java/delivery/portal/service/BugReportService.java`
- Modify: `src/main/java/delivery/portal/service/ExecuteRunService.java` (helper: `includeInBugReport(draft)`)
- Modify: `src/test/java/delivery/portal/api/BugReportApiTest.java`

**Steps:**

- [ ] **Step 1:** Extend test — seed draft with `PASSED` + `design-compare.json` status `MISMATCH` → expect row in report
- [ ] **Step 2:** Include row when `qaStatus == FAIL` OR `designCompareStatus` is `MISMATCH` (optional: `UNCERTAIN` — **exclude** unless product asks)
- [ ] **Step 3:** Run `BugReportApiTest`

---

### Task 5: Re-enable conversion TC screenshots (P2)

**Files:**
- Modify: `src/main/java/delivery/portal/api/ProjectController.java`
- Modify: `src/test/java/delivery/portal/api/ProjectPatchApiTest.java` (replace `screenshotRoute_returns410` with positive test using fixture file)

**Steps:**

- [ ] **Step 1:** Restore handler: delegate to `ProjectTcService.resolveScreenshot` + `FileSystemResource`, 404 if missing
- [ ] **Step 2:** Test — place dummy png under `{project}/evidence/{tcId}/step-001.png`, GET returns 200
- [ ] **Step 3:** `project-detail.html` — verify timeline `screenshotUrl` renders `<img>` if not already (read existing JS)

---

### Task 6: Design references on project Settings (P2)

**Files:**
- Modify: `src/main/resources/templates/project-detail.html` (Settings tab)
- Optional: `src/main/resources/static/css/portal.css` (compact list styles)

**Steps:**

- [ ] **Step 1:** Settings panel section “Design references (Execute)” — list `GET /api/projects/{id}/design-references`, upload form (`tcId` + file → POST), thumbnail link to GET serve URL
- [ ] **Step 2:** Hint: “Used by Execute design-vs-actual compare”
- [ ] **Step 3:** Manual or light MVC: project detail page contains `design-references` upload form id

---

### Task 7: Execute run history on `/execute` (P2)

**Files:**
- Modify: `src/main/resources/templates/execute.html`

**Steps:**

- [ ] **Step 1:** Below project picker, panel “Recent execute runs” — `GET /api/jobs?projectId=&kind=EXECUTE`, last 5, link Status + reopen results (store `lastExecuteJobId` in sessionStorage)
- [ ] **Step 2:** Selecting a past run loads results panel without new upload

---

### Task 8: Copy / IA polish (P3)

**Files:**
- Modify: `src/main/resources/templates/tc-guide.html` (§9 “open Automate”)
- Modify: `src/main/resources/templates/jobs.html`, `projects.html` (Automate wording)
- Modify: `src/test/java/delivery/portal/web/GuideMotionMvcTest.java` if assert text changes

**Steps:**

- [ ] Replace stale “Upload” CTAs where meaning is “start conversion” → Automate
- [ ] `projects.html` top button: “Open Automate” → `/automate` (or keep `/upload` with label “Upload in Automate”)

---

### Task 9: Schema backfill + docs (P3)

**Files:**
- Modify: `src/main/java/delivery/portal/config/JobsSchemaPatch.java`
- Modify: `docs/superpowers/specs/2026-08-23-keel-product-roadmap.md` §9

**Steps:**

- [ ] `UPDATE jobs SET job_kind = 'CONVERT' WHERE job_kind IS NULL OR job_kind = ''`
- [ ] Roadmap §9: mark P1–P8 implemented on branch with links to design docs

---

### Task 10: Integration verification

**Run (sequential batches):**

```bash
mvn -q "-Dmaven.compiler.release=21" "-Dtest=JobRecordDownloadableTest,JobsPageMvcTest,StatusPageMvcTest,BugReportApiTest" test
mvn -q "-Dmaven.compiler.release=21" "-Dtest=DashboardMvcTest,ExecuteRunApiTest,AutomateShellMvcTest,PortalApiTest,ProjectPatchApiTest" test
```

**Manual smoke (optional):**
1. Start execute run → `/status` shows no ZIP; `/execute` shows results
2. Start conversion → `/status` shows ZIP when complete
3. Dashboard quick glance — no ZIP on `exec_*` rows
4. Upload design ref on project Settings → execute run shows compare column

---

## Task dependency graph

```text
Task 1 (downloadable) ──┬──► Task 2 (filters)
                        ├──► Task 3 (status)
                        └──► Task 10
Task 4 (bug report) ──────────► Task 10
Task 5 (screenshots) ─────────► Task 10
Task 6 (design refs UI) ──────► Task 10
Task 7 (execute history) ─────► Task 10
Task 8 (copy) ────────────────► Task 10
Task 9 (schema/docs) ─────────► Task 10
```

**Parallelizable after Task 1:** Tasks 4, 5, 6, 7, 8, 9 in any order. Task 2 before Task 3 recommended.

---

## Spec self-review

| Audit item | Task |
|------------|------|
| Bogus ZIP on execute | 1, 2, 3 |
| Jobs/automate mix | 2 |
| Dashboard metrics mix | 2 |
| Bug report MISMATCH | 4 |
| Status page execute | 3 |
| Conversion screenshots 410 | 5 |
| Design refs only on execute | 6 |
| No execute history list | 7 |
| Copy Upload → Automate | 8 |
| Stale roadmap | 9 |
| Missing tests | 1–3, 5 |

---

## Out of scope (this plan)

- Jira/export integrations (E4 full)
- Figma plugin
- Separate `execute_runs` table migration
- Re-enabling evidence in artifact tree browser (only Settings upload + execute results)
