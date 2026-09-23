# Keel redesign — feature parity checklist

Status key:

| Symbol | Meaning |
|--------|---------|
| ✅ | Parity — prototype shows the workflow clearly (may still be mock data) |
| 🟡 | Partial — surface exists but missing fields, behavior, or depth |
| ❌ | Missing — production feature not represented in prototype |
| 🆕 | New — prototype-only concept not in production today |
| 🔀 | Moved — same feature, different route or IA |

**Depth key:**

| Label | Meaning |
|-------|---------|
| **Mock** | Screen-mapped in `index.html`; local state only; no API |
| **Prod** | Would need real backend / job execution to match production |

**Phases A–D:** All complete at **mock** depth. IA decisions recorded in [`IA-DECISION.md`](IA-DECISION.md).

Last reviewed: **2026-09-22** — after Phases A, B, C, and D.

**Production parity (2026-09-22):** Phases 0–8 shipped on the Thymeleaf portal (incremental plan). Prototype remains mock reference; prod column below marks what is live.

| Item | Production route / API | Shipped |
|------|------------------------|---------|
| Deep links | `/execute?jobId=&tcId=`, `GET /api/jobs` link hints | ✅ |
| N1 Attention | `/dashboard` panel + `GET /api/dashboard/attention` | ✅ |
| N2 Runs links | `/runs` Results + dashboard recent runs | ✅ |
| N9/N7/N8 Evidence | `/evidence?jobId=&tcId=` (tabs, IR, triage) | ✅ |
| N10 IA | Sidebar labels, project workflow links, `?projectId=` | ✅ |
| N4 Packages | `/packages` workspace list + inline delete/IR clear | ✅ |
| M2/M3 Generate | Bulk queue UI + post-save Open Automate banner | ✅ |
| M4 Packages | Project Automation tab (existing) + workspace list | ✅ |
| Workspace launchers | `/generate`, `/execute`, `/automate` pinned project bar | ✅ |
| Execute preload | `?projectId=` server + URL bootstrap | ✅ |
| Status evidence CTA | View evidence hidden until terminal execute job | ✅ |
| Evidence signed-out tab | `/evidence` tab for signed-out assertion steps | ✅ |
| F1 Screenshots | Real `/api/execute-runs/.../screenshots/` URLs | ✅ |
| F2 Excel | Execute + evidence export buttons | ✅ |
| F3 Uploads | Generate/import/automate/execute real endpoints | ✅ |
| F4 Roles | Mutate buttons gated by OWNER/ADMIN on Automation surfaces | ✅ |
| N5/N6 Mobile | — | Deferred (separate epic) |

---

## 1. Navigation & information architecture

| Production route | Production nav | Prototype route | Status | Notes |
|------------------|----------------|-----------------|--------|-------|
| `/dashboard` | Dashboard | `overview` | 🔀 🟡 Mock | KPIs + **attention queue wired to run registry** |
| `/projects` | Projects | `projects` | 🟡 Mock | Create modal; archive/delete on project settings only |
| `/generate` | Generate (workspace) | `wgenerate` + `generate` tab | 🔀 ✅ Mock | **Hybrid:** workspace launcher + project tab |
| `/execute` | Execute (workspace) | `wexecute` + `execute` tab | 🔀 ✅ Mock | **Hybrid:** workspace launcher + project tab |
| `/automate` → `upload.html` | Automate (workspace) | `wautomate` + `automate` tab | 🔀 ✅ Mock | **Hybrid:** workspace launcher + project tab |
| `/bug-hunter` | Bug Hunter (workspace) | `hunt` (project tab, web only) | 🔀 🟡 Mock | Status job + download pack; promote still toast-only |
| `/runs`, `/jobs` | Runs | `runs` + `evidence` | 🔀 🟡 Mock | List links to correct evidence kind via `run:` actions |
| `/status?jobId=` | (sidebar chip → status) | `status` | ✅ Mock | Polling, proof, cancel, download · **linked to evidence** |
| `/tc-guide` | Guide | `tcguide` | ✅ Mock | All 9 sections · scroll TOC · copy prompt |
| `/account` | Account | `settings` → Account | 🔀 🟡 Mock | Change email flow (preview toast) |
| `/admin/users` | Users | `admin` | 🔀 🟡 Mock | Access · Operations tabs |
| `/admin/domains` | Domains | `admin` → Domains | 🟡 Mock | Allowlist CRUD (local) |
| `/login` | — | `signin` | 🟡 Mock | Visual only |
| `/request-access` | login footer | signin modal | 🟡 Mock | Mock only |
| `/invite/{token}` | — | `invite` | 🟡 Mock | Accept-invite preview page |
| — | — | `devices` | 🆕 🟡 Mock | Inventory + **enroll runner** tab |
| — | — | `packages` | 🆕 🔀 🟡 Mock | Workspace packages; prod uses project Automation tab |
| — | — | `projectsummary` | 🆕 🟡 Mock | Default project landing; recent jobs + inline Execute expand |

**IA decision (Phase D):** **Hybrid** — workspace launchers (`wgenerate` / `wexecute` / `wautomate`) plus project tabs. Hunt remains project-tab-only (web). See [`IA-DECISION.md`](IA-DECISION.md).

---

## 2. Workspace overview (`/dashboard` → `overview`)

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Active project count | ✅ | ✅ | 🟡 Mock |
| Running jobs chip in sidebar | ✅ | ✅ | 🟡 Mock |
| Conversions / execute / completed / failed counts | ✅ | Derived KPIs | 🟡 Mock |
| Proven pass rate with sample note | ✅ | ✅ | 🟡 Mock |
| Results-by-day chart + project/date filters | ✅ | Static chart | 🟡 Mock |
| TC guide promo | ✅ | ✅ Mock | — |
| **Needs your attention** queue | — | ✅ | 🆕 ✅ Mock |
| Actionable links to evidence | — | ✅ | 🆕 ✅ Mock — **from `runRegistry` + devices + hunt** |
| Recent runs table | partial | ✅ | 🟡 Mock — **row actions → evidence / status** |

---

## 3. Projects

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| List owned projects | ✅ | ✅ | 🟡 Mock |
| Create project | ✅ | Mock modal | 🟡 Mock |
| Open project | ✅ | ✅ → **projectsummary** | 🔀 Mock |
| Archive / unarchive | ✅ | Project settings Danger | 🟡 Mock |
| Delete project permanently | ✅ | Project settings Danger | 🟡 Mock |
| Web vs mobile project type | web only | web + Android planned | 🆕 🟡 Mock |
| Pass rate / case count on card | partial | ✅ | 🟡 Mock |
| Filter by platform | — | ✅ | 🆕 Mock |

---

## 4. Project detail (prod: `/projects/{id}?tab=…`)

### 4a. Summary tab → `projectsummary`

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Library revision / case count strip | ✅ | ✅ | 🟡 Mock |
| Latest package / last prove strip | ✅ | ✅ | 🟡 Mock |
| Recent activity (10 jobs) | ✅ | ✅ | 🟡 Mock |
| Inline Execute result expand | ✅ | ✅ | 🟡 Mock → **run:RUN-2048 evidence** |
| Running-job hint | ✅ | ✅ | 🟡 Mock → status chip |

### 4b. Test cases tab → `library`

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Search / filter library | ✅ | ✅ | 🟡 Mock |
| Collapsed rows + expand preview | ✅ | ✅ | ✅ Mock |
| Step × Test data × Expected preview table | ✅ | ✅ | ✅ Mock |
| Upload Excel/CSV merge by TC_ID | ✅ | Upload modal | 🟡 Mock |
| Delete selected cases | ✅ | Preview delete (local) | 🟡 Mock |
| Edit case — aligned step grid | ✅ | ✅ | ✅ Mock |
| Edit — preconditions, call-before, priority, tags, KeelPath, visual | ✅ | ✅ | 🟡–✅ Mock |
| Library history list | ✅ | Mock modal | 🟡 Mock |
| History — diff view | ✅ | Side-by-side mock | 🟡 Mock |
| Link to TC guide | ✅ | ✅ | 🟡 Mock |

### 4c. Automation tab → `packages` + project context

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Package version ZIP list | ✅ | table mock | 🟡 Mock |
| Browse / view source | ✅ | File tree + source view | 🟡 Mock |
| Delete artifact / clear leftover IR | ✅ | ✅ Mock | — |
| Download package | ✅ | mock download | 🟡 Mock |

### 4d. Project settings tab → `projectsettings`

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Project name, base URL, hooks | ✅ | General tab | 🟡 Mock |
| Authoring engine + precision max | ✅ | General tab | 🟡 Mock |
| Design mockups (Execute PNG per TC) | ✅ | General tab (sample row) | 🟡 Mock |
| Credential profiles CRUD | ✅ | Credentials tab | 🟡 Mock |
| Members / invites / runners link | ✅ | Members tab | 🟡 Mock |
| Environment profiles + diff | ✅ | Environments tab | 🟡 Mock |
| Usage budget + job ledger | ✅ | Usage tab | 🟡 Mock |
| Job-status webhook | ✅ | Integrations tab | 🟡 Mock |
| Archive / delete project | ✅ | Danger tab | 🟡 Mock |

---

## 5. Generate (`/generate`)

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Paste user stories → Ollama generate | ✅ | Mock + status job | 🟡 Mock |
| KeelPath assignment on save | ✅ | Preview table + editor | 🟡 Mock |
| Save to project library | ✅ | ✅ | 🟡 Mock |
| Import CSV/JSON | ✅ | Tab + file picker | 🟡 Mock |
| Bulk upload queued job | ✅ | ✅ Mock | — |
| AI review pass | ✅ | Review panel | 🟡 Mock |
| Compare two model outputs | ✅ | Compare job + status | 🟡 Mock |
| Step grid editor on generated rows | ✅ | Row editor modal | 🟡 Mock |
| Open Automate after save | ✅ | ✅ Mock | — |

---

## 6. Execute (`/execute`)

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Credential profile select | ✅ | ✅ | 🟡 Mock |
| Library case multiselect | ✅ | Case picker | 🟡 Mock |
| Upload one-off workbook | ✅ | Source toggle | 🟡 Mock |
| Design mockup PNG upload per TC | ✅ | Configure mention | 🟡 Mock |
| Pre-run AI review panel | ✅ | ✅ | 🟡 Mock |
| Start job + live progress | ✅ | Status page | 🟡 Mock |
| Force stop | ✅ | Status cancel | 🟡 Mock |
| Results table + TC drill-down | ✅ | Results tab + evidence | 🟡 Mock |
| Export bug report / Excel | ✅ | CSV mock | 🟡 Mock |
| Recent runs (last 5) | ✅ | Results panel | 🟡 Mock |

---

## 7. Automate (`/automate` / `upload.html`)

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Project + base URL + credential pick | ✅ | Run setup | 🟡 Mock |
| Workbook upload | ✅ | File picker | 🟡 Mock |
| Case selection from library | ✅ | Dropdown | 🟡 Mock |
| Start conversion job | ✅ | Status page | 🟡 Mock |
| Final revise option | ✅ | Toggle | 🟡 Mock |
| Live status page | ✅ | ✅ | 🟡 Mock |
| Download framework ZIP | ✅ | Status download | 🟡 Mock |
| Soft-block banner (COMPLETED_WITH_BLOCK) | ✅ | ✅ | 🟡 Mock |

---

## 8. Runs, status & evidence

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| All jobs list (all kinds) | ✅ | ✅ | 🟡 Mock |
| Job kind badges | ✅ | MODE column | 🟡 Mock |
| Live status polling | ✅ | ✅ | 🟡 Mock |
| Proof kind / source / library revision | ✅ | Status panel | 🟡 Mock |
| Provider allowlist / used / fallback | ✅ | Status + evidence stats | 🟡 Mock |
| Attempts list + compare runs | ✅ | Compare modal + attempt chips | 🟡 ✅ Mock |
| Intermittency verdict | ✅ | Evidence triage box | 🟡 ✅ Mock |
| Rerun as new attempt | ✅ | Rerun → linked mock job | 🟡 ✅ Mock |
| Failure classification save | ✅ | Select + save (local) | 🟡 ✅ Mock |
| Download ZIP / CSV / hunter pack | ✅ | Partial mocks | 🟡 Mock |
| Evidence — assertion step timeline | ✅ | ✅ | 🟡 Mock |
| Evidence — explicit IR block | — | ✅ | 🆕 ✅ Mock |
| Evidence — checkout / capture / signed-out tabs | — | ✅ | 🆕 ✅ Mock |
| Action log / locator details | partial | ✅ | 🟡 Mock |
| Real screenshots | ✅ | Illustrated placeholder | 🟡 Mock |

**Phase C wiring:** Shared `runRegistry` drives overview attention, runs table row actions, evidence run id, compare attempts, and device blocked-run links.

---

## 9. Bug Hunter (`/bug-hunter` → `hunt`)

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Start hunt job | ✅ | Status job mock | ✅ Mock |
| Live status + download pack | ✅ | Status + download | 🟡 Mock |
| Review candidates | ✅ | ✅ | 🟡 Mock |
| Promote to library | ✅ | toast | 🟡 Mock |
| Duplicate detection hints | ✅ | ✅ | 🟡 Mock |

---

## 10. Account & administration

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Change password | ✅ | Account tab | 🟡 Mock |
| Change email | ✅ | Account tab | 🟡 Mock |
| Admin — user list / roles | ✅ | admin table | 🟡 Mock |
| Admin — pending access requests | partial | ✅ | 🟡 Mock |
| Admin — send invite | ✅ | mock modal + link | 🟡 Mock |
| Admin — domain allowlist | ✅ | Domains tab | 🟡 Mock |
| Accept invite / register | ✅ | `invite` page | 🟡 Mock |

---

## 11. Cross-cutting production behaviors

| Feature | Prod | Prototype | Status |
|---------|------|-----------|--------|
| Sidebar active-job chip | ✅ | ✅ | 🟡 Mock |
| Real API / job execution | ✅ | none (by design) | — |
| Role-based UI | ✅ | copy only | 🟡 Mock |
| TC guide + CSV templates | ✅ | tcguide + CSV download | 🟡 Mock |
| Mobile / Appium execution | — | devices + enroll tab | 🆕 🟡 Mock |

---

## 12. Migration order

### Phase A — Daily QA parity ✅ Mock complete

1. Library depth (preview, upload, delete, full edit fields)
2. Generate (stories/import, compare, review, save)
3. Execute (configure, results, exports, status)
4. Automate (workbook, final revise, soft-block, status)
5. `/status` equivalent (polling, proof, cancel, download)

### Phase B — Project & workspace settings ✅ Mock complete

6. Project summary tab
7. Project settings (7 tabs)
8. TC guide, account email, admin domains, invite acceptance

### Phase C — New design value ✅ Mock complete

9. Overview attention queue **wired to `runRegistry`**
10. Evidence IR tabs **linked by run id / evidence kind**
11. Failure classification + compare + rerun UX **polished**
12. Mobile devices/runners **inventory + enroll runner tab**

### Phase D — IA decisions ✅ Mock complete

13. **Hybrid IA** — workspace Generate / Execute / Automate launchers + project tabs (`IA-DECISION.md`)
14. **TC guide** — all 9 sections with scroll TOC + copy prompt
15. **Status vs evidence** — split retained; linked via banners and actions
16. **Hunt** live status · **package** source view · **library history** diff

---

## 13. Summary scorecard (mock prototype depth)

See **§14** for the reconciled scorecard, full 🆕/❌/🟡 inventories, and recommended post-approval order.

| Metric | Approximate |
|--------|-------------|
| **Full mock** (✅ end-to-end clickable) | ~35% of feature rows |
| **Partial mock** (🟡 UI, fake/local behavior) | ~60% |
| **Missing UI** (❌) | **0** — all M1–M4 closed at mock depth |
| **New concepts** (🆕) | **11** (2 IA items approved in `IA-DECISION.md`) |

**Production readiness:** Direction deck complete through **Phase E** (mock + product decisions). Next: production implementation per `IA-DECISION.md` order.

---

## 14. Remaining 🆕 · ❌ · 🟡 (post Phase D)

Use this appendix when planning **implementation** (after design approval). It reconciles the scorecard with row-level tables above.

### How to read the buckets

| Bucket | Meaning | Typical fix |
|--------|---------|-------------|
| **🆕 New** | Redesign adds something prod does not have today | Product decision: ship, merge, or cut |
| **❌ Missing** | Prod feature with no prototype surface | Add screen or wire existing route |
| **🟡 Partial mock** | UI exists; behavior is toast, static, or local-only | Backend, persistence, or real I/O |
| **🟡 Partial fidelity** | Screen exists but not prod-accurate (e.g. illustrated screenshots) | Real artifacts / API integration |

Most **🟡** rows are partial **because the prototype is mock-only by design**, not because the screen was skipped.

---

### 🆕 New concepts — need product sign-off

| # | Concept | Prototype location | Prod today | Decision |
|---|---------|-------------------|------------|----------|
| N1 | **Needs your attention** queue | `overview` | Dashboard KPIs/chart only | **Approved — Keep** · [`IA-DECISION.md`](IA-DECISION.md) |
| N2 | **Actionable evidence links** from overview & runs | `runRegistry` → `run:` / devices / hunt | Partial (jobs list) | **Approved — Keep** |
| N3 | **Project Summary** as default project home | `projectsummary` | Opens Test cases tab | **Approved — Keep** |
| N4 | **Workspace Automation (`packages`)** page | Sidebar | Project Automation tab only | **Approved — Keep workspace view** |
| N5 | **Devices & runners** + enroll tab | `devices` | Private runners elsewhere | **Approved** (mobile track) |
| N6 | **Android project type** + platform filter | `projects` | Web only | **Approved** (mobile track) |
| N7 | **Evidence IR tabs** (checkout / capture / signed-out) | `evidence` | Flatter execute results | **Approved — Keep** |
| N8 | **Explicit IR block** in evidence | `evidence` | — | **Approved — Keep** |
| N9 | **Status vs evidence split** with cross-links | `status` + `evidence` banners | Status page + results views | **Approved** in `IA-DECISION.md` |
| N10 | **Hybrid workspace launchers** | `wgenerate` / `wexecute` / `wautomate` | Workspace-only G/E/A | **Approved** in `IA-DECISION.md` |

---

### ❌ Missing — explicit gaps (add UI or drop from prod parity)

| # | Feature | Area | Notes |
|---|---------|------|-------|
| ~~M1~~ | ~~**TC guide promo** on dashboard~~ | Overview | ✅ Mock — promo with Test data rules + Full guide |
| ~~M2~~ | ~~**Generate bulk upload queued job**~~ | Generate | ✅ Mock — bulk CSV queue + recent jobs table |
| ~~M3~~ | ~~**Open Automate after save**~~ | Generate | ✅ Mock — post-save banner + Open Automate CTA |
| ~~M4~~ | ~~**Delete package artifact / clear IR**~~ | Packages | ✅ Mock — delete artifact + clear ir/ drafts |

**Not missing UI, but missing prod fidelity** (often counted in scorecard ❌):

| # | Feature | Area | Notes |
|---|---------|------|-------|
| F1 | **Real run screenshots** | Evidence | Illustrated placeholders only |
| F2 | **Excel results export** (Execute) | Execute | CSV mock only |
| F3 | **Real file upload processing** | Library, Generate, Automate | Modals/pickers only |
| F4 | **Enforced role-based UI** | Settings, admin, project members | Copy/labels only |
| F5 | **Auth session / CSRF** | Sign-in, all writes | Out of scope for preview |

---

### 🟡 Partial mock — grouped by why

#### A. No backend (by design until implementation)

All job starts, saves, invites, uploads, webhooks, email change, classification save, library delete/merge, credential CRUD, hunt accept → **toast or local state only**.

Affected areas: Generate, Execute, Automate, Hunt (promote), Library, Project settings, Settings, Admin, Invite.

#### B. Static or illustrative data

| Item | Location |
|------|----------|
| Overview chart (no date/project filters) | `overview` |
| KPI / pass rate sample numbers | `overview`, project cards |
| Evidence screenshots | `evidence` |
| History diff (single sample rev 11 → head) | Library history modal |
| Package source snippets | Package inspect |
| Admin operations metrics | `admin` → Operations |

#### C. Surface exists, prod depth thin

| Area | Examples |
|------|----------|
| **Projects** | Create/archive/delete preview only |
| **Execute** | Mockup PNG upload mention only; workbook upload toggle only |
| **Generate** | Ollama/compare/review — timed status mock |
| **Runs** | Polling interval mock; fake download blobs |
| **Hunt** | Live status ✅; candidate promote = toast |
| **Sign-in / request access** | Visual only |

#### D. Route / IA differences (🔀, still mock)

| Prod | Prototype |
|------|-----------|
| Bug Hunter at workspace | `hunt` project tab (web only) |
| Account + admin split routes | `settings` + `admin` merged surfaces |
| Library project-scoped | Workspace **Test library** shortcut + project tab |
| Packages on project Automation tab | Workspace **Automation** + project Automate flow |

---

### ✅ Full mock parity (reference — ~35% of rows)

Workflows that are **end-to-end clickable** with consistent mock state:

- Hybrid **Generate / Execute / Automate** (launcher + project tab)
- **Status** (polling, proof, cancel, download, evidence link)
- **TC guide** (9 sections, TOC, copy prompt)
- **Hunt start** → status → download pack
- Library **collapse, step grid edit, expand preview**
- **Attention queue → evidence** (`runRegistry`)
- **Compare / rerun / classification** on evidence
- **Devices** inventory + blocked-run links + enroll tab

---

### Recommended order after design approval

1. ~~**Product:** N1–N8~~ — **approved** in [`IA-DECISION.md`](IA-DECISION.md) (N9–N10 Phase D).
2. ~~**Quick UI parity:** M1–M4~~ — all closed in prototype.
3. **Implementation:** Replace 🟡 bucket A with real API — see implementation order in `IA-DECISION.md`.
4. **Fidelity:** F1–F2 (real screenshots, Excel export).
5. **Mobile:** N5–N6 when [`mobile-execution-automation-design`](../../superpowers/specs/2026-09-22-mobile-execution-automation-design.md) is approved for build.

---

### Revised scorecard (reconciled with §14)

| Area | ✅ Full mock | 🟡 Partial mock | ❌ Missing UI | 🆕 New |
|------|-------------|-----------------|---------------|--------|
| Navigation / IA | 7 | 9 | 0 | 3 |
| Overview | 3 | 5 | 0 | 2 |
| Projects | 0 | 6 | 0 | 2 |
| Library / TC edit | 4 | 6 | 0 | 0 |
| Generate | 2 | 7 | 0 | 0 |
| Execute | 0 | 10 | 0 | 0 |
| Automate | 0 | 8 | 0 | 0 |
| Packages | 1 | 3 | 0 | 1 |
| Runs / evidence / status | 6 | 8 | 0 | 2 |
| Bug Hunter | 1 | 4 | 0 | 0 |
| Settings / admin | 0 | 9 | 0 | 0 |
| Devices / mobile | 0 | 3 | 0 | 1 |

**Totals (feature rows, approximate):** ~24 full mock · ~69 partial mock · **0 missing UI** · **11 new concepts** (2 IA items approved).

**Interpretation:** ~38% full mock screen parity, ~60% partial (mostly mock-only behavior), **0% missing UI at prototype depth** — plus 🆕 redesign value to sign off separately from prod parity.
