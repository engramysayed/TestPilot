# Dashboard refresh — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not commit unless the user asks.**

**Goal:** Refresh `/dashboard` with balanced hero (compact stats + three verb cards), subtle motion, empty states, and extended `DashboardService` stats wired to real data.

**Architecture:** Extend `DashboardService.statsFor` with `activeProjectCount`, `runningJobs`, `hasProjects`, `hasJobs`; filter `projectOptions` to non-archived; redesign `dashboard.html` hero + empty states; add CSS reveal animations respecting `prefers-reduced-motion`.

**Tech Stack:** Spring Boot MVC, Thymeleaf, Chart.js (existing), portal.css, TestNG + MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-23-dashboard-refresh-design.md`

## Global Constraints

- Balanced hero: compact stat strip + Generate / Execute / Automate verb cards.
- Gen/Exec → coming-soon pages; Automate → `/automate`.
- Remove primary CTA to `/upload`.
- `activeProjectCount` excludes archived; jobs from archived projects stay in totals/chart.
- `runningJobs` = status `QUEUED` or `RUNNING`.
- Subtle CSS animation only; `prefers-reduced-motion: reduce` disables motion.
- Do not commit unless asked.
- Tests: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=…" test`

---

## File map

| File | Responsibility |
|------|----------------|
| Modify: `DashboardService.java` | New stat fields, archived filter |
| Create: `DashboardServiceTest.java` | Unit tests for stat logic |
| Modify: `dashboard.html` | Hero, verb cards, empty states, link fixes |
| Modify: `portal.css` | `.dash-hero`, `.verb-cards`, `.dash-reveal`, responsive |
| Create: `DashboardMvcTest.java` | Page smoke + no stale Upload CTA |

---

### Task 1: DashboardService stats extension

**Files:**
- Modify: `src/main/java/delivery/portal/service/DashboardService.java`
- Create: `src/test/java/delivery/portal/service/DashboardServiceTest.java`

**Interfaces:**
- `statsFor(Long ownerUserId)` returns `Map<String, Object>` with added keys:
  - `activeProjectCount` (int) — projects where `!isArchived()`
  - `runningJobs` (int) — jobs with status `QUEUED` or `RUNNING`
  - `hasProjects` (boolean) — `activeProjectCount > 0`
  - `hasJobs` (boolean) — `jobCount > 0`
- `projectOptions` — only non-archived projects

- [ ] **Step 1: Write failing test**

```java
@Test
public void statsFor_excludesArchivedFromActiveCount() {
    // seed archived + active project, assert activeProjectCount == 1
}

@Test
public void statsFor_countsRunningJobs() {
    // seed QUEUED + RUNNING + COMPLETED jobs, assert runningJobs == 2
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=DashboardServiceTest" test`

- [ ] **Step 3: Implement in DashboardService**

- [ ] **Step 4: Run test — expect PASS**

---

### Task 2: Dashboard template hero + verb cards

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`

**Interfaces:**
- Consumes: `stats.activeProjectCount`, `stats.runningJobs`, `stats.hasProjects`, `stats.hasJobs`, existing chart/job fields
- Hero stat strip: Projects (active), Jobs, Running, Passed, TODO
- Verb cards row: Generate (`/generate` + badge), Execute (`/execute` + badge), Automate (`/automate`)
- Remove `<a class="button" href="/upload">New conversion</a>`
- Empty states per spec §4.3

- [ ] **Step 1: Rewrite page-title + hero sections**
- [ ] **Step 2: Add verb cards using `.automate-card` + `.coming-soon-badge`**
- [ ] **Step 3: Update stat bindings and empty-state conditionals**
- [ ] **Step 4: Fix quick glance / table empty copy → `/automate` not `/upload`**

---

### Task 3: Dashboard CSS + motion

**Files:**
- Modify: `src/main/resources/static/css/portal.css`

**Interfaces:**
- `.dash-hero` — wraps stat strip + verb cards
- `.verb-cards` — 3-column grid (reuse `.automate-cards` patterns)
- `.dash-reveal` + `@keyframes dashReveal` — stagger on load
- `@media (prefers-reduced-motion: reduce)` — disable animation
- Responsive: stack verb cards on narrow viewports

- [ ] **Step 1: Add hero + verb card styles**
- [ ] **Step 2: Add reveal animation + reduced-motion override**
- [ ] **Step 3: Adjust `.stats` grid for 5 cards on desktop**

---

### Task 4: Dashboard MVC tests

**Files:**
- Create: `src/test/java/delivery/portal/web/DashboardMvcTest.java`

**Interfaces:**
- `GET /dashboard` → 200 authenticated
- Body contains "Generate", "Execute", "Automate"
- Body does NOT contain primary `href="/upload"` CTA pattern from old page-title button
- Body contains `activeProjectCount` or "Running" label

- [ ] **Step 1: Write DashboardMvcTest**
- [ ] **Step 2: Run full suite**

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=DashboardMvcTest,DashboardServiceTest,AutomateShellMvcTest,AuthOwnershipTest" test`

---

## Spec self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| Balanced hero | Task 2 |
| Running stat | Task 1, 2 |
| Archived filter | Task 1 |
| Verb cards + badges | Task 2, 3 |
| Empty states | Task 2 |
| Animation + reduced motion | Task 3 |
| No Upload primary CTA | Task 2, 4 |
| API extended, no breaking change | Task 1 |
| Tests | Task 1, 4 |
