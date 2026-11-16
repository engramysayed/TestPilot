# Keel redesign — IA & product decisions

Status: **approved for prototype and production direction** (2026-09-22).  
Phases A–E complete at mock depth; UI parity gaps M1–M4 closed. Implementation (real API) is next.

---

## 13. Workspace vs project workflows — **Hybrid**

**Decision:** Keep **workspace sidebar entry points** (Generate, Execute, Automate) *and* **project tabs** for the same flows.

| Entry | Route | Behavior |
|-------|-------|----------|
| Workspace sidebar | `wgenerate` / `wexecute` / `wautomate` | Project picker → opens the same configure UI inside the chosen project |
| Project tabs | `generate` / `execute` / `automate` | Full flow with project context already pinned |
| Overview “+ New run” | modal | Shortcut to Execute or Automate in current preview project |

**Rationale:** Production today uses workspace-level G/E/A with implicit last project. The redesign prototype showed project-first tabs, which testers liked for context but which hid cross-project operators. Hybrid preserves muscle memory for power users while keeping project breadcrumbs and settings co-located.

**Not in prototype:** Persisting “last used project” per workspace user (would be server-side).

---

## 15. Status page vs inline evidence — **Split, linked**

**Decision:** Retain a dedicated **`/status` equivalent** for *live* jobs; use **evidence** for *post-run* triage.

| Surface | When | Contains |
|---------|------|----------|
| **Status** | QUEUED / RUNNING / just COMPLETED | Polling, cancel, proof metadata, download, “View evidence” when done |
| **Evidence** | Failed / blocked / compare / classify | Step timeline, IR tabs, screenshots, attempts, rerun |
| **Project summary expand** | Execute rows only | Inline case pass/fail strip → link to full evidence |

**Rationale:** Live jobs need a lightweight polling page without loading heavy evidence chrome. Triage (classification, intermittency, compare) belongs on evidence where attempts are grouped.

**Rejected alternative:** Single combined run detail page — rejected because it overloads the live polling UX and blurs “working” vs “judged” states.

---

## 14 & 16. Remaining depth (prototype mock)

| Item | Prototype treatment |
|------|---------------------|
| TC guide 9 sections | Full section content + scroll TOC |
| Hunt live status | HUNT job → status page → download pack mock |
| Package source view | Inspect modal → file tree → view Java snippet |
| Library history diff | History modal → side-by-side revision diff |

---

## Navigation map (after Phase D)

```
Workspace
├── Overview
├── Projects → Project summary (default)
│   └── Tabs: Summary · Library · Generate · Execute · Automate · Hunt · Settings
├── Generate   (workspace launcher → project)
├── Execute    (workspace launcher → project)
├── Automate   (workspace launcher → project)
├── Test library (shortcut — same as project library for Commerce Web)
├── TC guide
├── Runs & evidence → Status (live) · Evidence (triage)
├── Automation (packages)
├── Devices & runners
└── Settings · Admin
```

---

## Phase E — New concept decisions (N1–N8)

Status: **approved** (2026-09-22). N9–N10 decided in Phase D above.

| # | Concept | Decision | Production notes |
|---|---------|----------|------------------|
| **N1** | Needs your attention queue | **Keep** | Derive from failed/blocked/running jobs + device offline state. Cap at ~5 items; link each to evidence or status. Do not duplicate full runs table. |
| **N2** | Actionable evidence links | **Keep** | Core redesign value. Overview attention + runs row actions must resolve to the correct evidence kind / run id (same as prototype `runRegistry`). |
| **N3** | Project Summary default home | **Keep** | Opening a project lands on **Summary** (recent jobs, library rev, latest package). Library remains one tab away. |
| **N4** | Workspace Automation (`packages`) | **Keep workspace view** | Sidebar **Automation** lists packages across projects. Project **Automate** tab stays the conversion entry; project Automation tab in prod maps to inspect + IR. |
| **N5** | Devices & runners + enroll | **Keep** (mobile track) | Ship with [mobile execution design](../../superpowers/specs/2026-09-22-mobile-execution-automation-design.md). Enroll tab + inventory mirror private runner ops docs. |
| **N6** | Android project type + filter | **Keep** (mobile track) | Web-only create until mobile build; filter on projects list is valid once Android projects exist. |
| **N7** | Evidence IR tabs | **Keep** | Tabbed evidence (failure / capture / signed-out) for triage training and explicit-assertion cases. Not required for every run — default tab from failure kind. |
| **N8** | Explicit IR block in evidence | **Keep** | Render capture/compare/signed-out metadata block when IR exists in job results; pairs with delivery explicit-assertion work. |

**Rejected alternatives**

- **N1 → merge into chart only:** rejected — operators need one-click triage without opening Runs first.
- **N3 → default to library:** rejected — summary answers “what happened lately” before authoring.
- **N4 → fold packages into project only:** rejected — workspace operators need cross-project package history.

---

## Phase E — UI parity closure (M1–M4)

All explicit missing UI items from the parity checklist are implemented in `index.html`:

| # | Feature | Prototype |
|---|---------|-----------|
| M1 | TC guide promo on dashboard | Overview authoring panel |
| M2 | Generate bulk upload queued job | Bulk CSV panel + `GENERATE_BULK` status job + recent jobs |
| M3 | Open Automate after save | Post-save banner on Generate |
| M4 | Delete package / clear IR | Automation table delete + inspect delete + clear `ir/` drafts |

---

## Implementation order (production, after this packet)

| Step | Scope | Production shipped |
|------|--------|-------------------|
| 0 | Deep links + job API hints | **2026-09-22** |
| 1 | N1 dashboard attention | **2026-09-22** |
| 2 | N2 runs / recent runs links | **2026-09-22** |
| 3 | N9/N7/N8 evidence split | **2026-09-22** |
| 4 | N10 IA / nav / launchers | **2026-09-22** |
| 5 | N4 workspace packages | **2026-09-22** |
| 6 | M2/M3 generate polish | **2026-09-22** |
| 7 | F1–F4 fidelity audit | **2026-09-22** |
| — | N3 summary default tab | Already in prod |
| — | M1 TC guide promo | Already in prod |
| — | M4 project Automation delete/clear | Already in prod |
| — | N5/N6 mobile | Deferred |

Mock-only bucket A behaviors remain in the **prototype** (`index.html`) for design reference; production portal uses real APIs for the rows above.
