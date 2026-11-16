# Keel product roadmap — three workspaces, projects hub, guide & dashboard

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23) — roadmap only; implement via per-phase designs  
**Owner product surface:** Self-hosted Keel portal; customers use the web UI only  

**Related (already shipped):** vision role split (UI-TARS ground / Qwen assert), DOM post-click, miss journal, `/tc-guide`, Upload → Jobs → Automate pipeline  

---

## 1. Vision (one sentence)

Keel becomes a **hosted workspace** where each customer configures **Projects** once, then chooses among three clear product flows: **Generate TCs**, **Execute & verify**, and **Automate** — with a modern dashboard and an animated TC guide that includes a ready AI→CSV prompt.

---

## 2. Locked decisions (from product conversation)

| Topic | Decision |
|-------|----------|
| AI TC formatting (guide) | **A** — copyable prompt for any AI chat → **CSV with Keel headers** → user opens in Excel / Save as `.xlsx` |
| True `.xlsx` from ChatGPT | Not required in v1 (unreliable in generic chats) |
| Hosting model | **You self-host**; customers access the portal |
| Upload top-level nav | Likely **removed or nested** under Automate (exact IA TBD in Projects/Nav phase) |
| Project config | **Only** in Projects hub (URL, named credentials, file/asset management) |
| Elsewhere | User **selects project** (+ credential profile if needed), does not re-enter host config |
| Build style | Big plan first → implement **one pillar at a time** |

---

## 3. Information architecture (target)

```text
Keel portal
├── Dashboard          (animated overview — all projects/jobs/scores)
├── Projects           (create / edit / files / delete / URL / named creds)
├── Generate TCs       (user stories → Keel-format TCs)          [NEW]
├── Execute & verify   (run manuals; vision+DOM; bugs; design vs actual) [NEW]
├── Automate           (manual TCs → automation; Upload lives here) [EVOLVE]
├── Guide              (revamped + animated TC guide + AI CSV prompt)
├── Jobs               (history / status / downloads — keep or nest under Automate)
└── Account / Admin    (as today)
```

**Nav principle:** Three product “verbs” (Generate / Execute / Automate) + one **Projects** home for configuration + Guide + Dashboard.

---

## 4. Pillars (detailed)

### 4.1 Projects hub

**Goal:** Single place to create and fully manage a project.

**Capabilities (target):**
- Create / rename / archive or delete project  
- Set **base URL** (and optional environments later)  
- **Named credentials** (e.g. `normal_user`, `super_admin`) — multiple per project; store secrets safely on host  
- Browse **project files** (pages, generated tests, methods, packages, evidence)  
- **Delete** deprecated pages / tests / methods / artifacts from the portal  
- Permissions: project owner vs viewer (phase 2 if needed)

**Out of scope for first Projects slice:** full Git sync UI, multi-region storage.

**Depends on:** existing `delivery-store` / domain–project layout; extend with portal APIs + UI.

---

### 4.2 Guide (revamp + AI CSV prompt)

**Goal:** Best-in-class authoring standard + one-click **Copy prompt** for external AIs.

**v1 Guide AI feature:**
- Section (upgrade current §9): full system/user prompt text  
- **Copy** button  
- Exact Keel column headers: `TC_ID`, `Title`, `Steps`, `ExpectedResult`, `Preconditions`, `Priority`, `Tags`, `VisualAssertion`, `TestData`  
- Rules mirrored from guide (TestData line alignment, no file-upload cases, keep UI labels, etc.)  
- Instruct AI to output **CSV only** (header row + data rows)  
- Short “How to use”: paste prompt → paste rough TCs → copy CSV → Excel → Upload/Automate  

**Guide UX:** Modern layout + tasteful motion (section reveals, sticky TOC) — not a new product.

---

### 4.3 Automate (evolve current Upload → Jobs)

**Goal:** Turn **manual TCs (Keel Excel)** into maintainable automation packages.

**Flow (conceptual):**
1. Select **Project** (+ credential profile if login needed)  
2. Upload / attach workbook (moved from top-level Upload)  
3. Run conversion job (prove / heal / emit) — existing engine  
4. Track Jobs; download ZIP / scores  

**Reuse:** Current conversion, vision heal (UI-TARS), visual assert (Qwen), DOM post-click, miss journal.

---

### 4.4 Generate TCs (new)

**Goal:** From **user stories / PRD / acceptance criteria** → Keel-format TC workbook (CSV/xlsx).

**Flow (conceptual):**
1. Select Project  
2. Paste stories or upload doc  
3. Keel (and/or guided external AI prompt) produces TCs in our format  
4. Download CSV/xlsx; optional hand-off to Automate or Execute  

**v1 option:** Same as Guide — strong prompt + optional in-portal generator later.  
**Not v1:** Fully autonomous story parsing without human review.

---

### 4.5 Execute & verify (new)

**Goal:** Execute manual TCs **without** generating Selenium packages first — explore / bug-find / compare.

**Capabilities (target):**
- Run steps with **DOM** + **visual grounding** (UI-TARS)  
- Assert visually (Qwen) + DOM post-conditions  
- Report **possible bugs** (failed expected, unexpected UI, flaky hits)  
- **Design vs actual** comparison (screenshot / reference image / Figma later — phased)  

**Phasing suggestion:**
- **E1:** Run steps + pass/fail + evidence screenshots (DOM-heavy)  
- **E2:** Vision ground on weak binds  
- **E3:** Design-vs-actual (reference images)  
- **E4:** Bug clustering / export  

**Hardest pillar** — depends on Projects + stable vision stack (already improved).

---

### 4.6 Dashboard (modern animated)

**Goal:** One glance at everything the user owns: projects, jobs, pass/TODO, recent packages, health of last runs.

**Target:** Keep metric cards + scores-over-time; add motion, clearer empty states, deep links into Project / Job / flow entry points (“New conversion”, “Generate TCs”, “Execute”).

**Not v1 of dashboard alone:** Real-time websockets unless already trivial.

---

## 5. Implementation phases (ordered)

| Phase | Name | Deliverable | Depends on |
|-------|------|-------------|------------|
| **P0** | Roadmap approval | This doc signed off | — |
| **P1** | Guide AI CSV prompt + light guide polish | Copyable prompt on `/tc-guide`; CSV instructions | P0 |
| **P2** | Projects hub v1 | Create/edit, URL, named creds, list/delete key files | P0 |
| **P3** | Nav IA + Automate shell | Three sections; Upload nested under Automate; project picker | P2 |
| **P4** | Dashboard refresh | Modern animated overview wired to real data | P2–P3 |
| **P5** | Guide motion polish | Stronger animation / UX on guide | P1 |
| **P6** | Generate TCs v1 | Stories → CSV/xlsx (prompt-first or light portal generator) | P2 |
| **P7** | Execute & verify E1–E2 | Run + evidence + vision ground | P2, vision stack |
| **P8** | Execute & verify E3+ | Design-vs-actual, bug reports | P7 |

**Rule:** Do not start P6–P8 until P2 project config is usable. Do not parallelize all pillars.

---

## 6. Cross-cutting concerns

| Concern | Notes |
|---------|--------|
| Security | Creds encrypted at rest on host; never send secrets to external AI prompts by default |
| External AI prompt | Prompt must say: do not invent credentials; use placeholders; TestData for values |
| Multi-tenant | Self-hosted; invite-gated users; project isolation in store |
| Capacity | One GPU/Ollama host — queue jobs; document concurrent limits for pilots |
| Commits | Product owner preference: no auto-commits unless asked |

---

## 7. Success criteria (roadmap level)

1. Stakeholders agree on **three flows + Projects + Guide + Dashboard** IA.  
2. Each phase has a later **feature design + plan** before code.  
3. P1 ships AI→CSV guide feature without blocking P2.  
4. Customers never configure URL/creds outside Projects.  

---

## 8. Explicit non-goals (this roadmap)

- Public multi-region SaaS  
- Fine-tuning VLMs  
- Replacing Excel as the interchange format in v1  
- Building all three flows in one sprint  

---

## 9. Next after approval

1. ~~User reviews/edits this roadmap.~~ **Done (approved 2026-08-23).**  
2. ~~**P1** Guide AI CSV prompt~~ — [`2026-08-23-guide-ai-csv-prompt-design.md`](./2026-08-23-guide-ai-csv-prompt-design.md)  
3. ~~**P2** Projects hub~~ — [`2026-08-23-projects-hub-design.md`](./2026-08-23-projects-hub-design.md)  
4. ~~**P3** Nav IA + Automate shell~~ — [`2026-08-23-nav-automate-shell-design.md`](./2026-08-23-nav-automate-shell-design.md)  
5. ~~**P4** Dashboard refresh~~ — [`2026-08-23-dashboard-refresh-design.md`](./2026-08-23-dashboard-refresh-design.md)  
6. ~~**P5** Guide motion polish~~ — [`2026-08-23-guide-motion-design.md`](./2026-08-23-guide-motion-design.md)  
7. ~~**P6** Generate TCs v1~~ — [`2026-08-23-generate-tcs-design.md`](./2026-08-23-generate-tcs-design.md)  
8. ~~**P7** Execute & verify E1–E2~~ — [`2026-08-23-execute-verify-design.md`](./2026-08-23-execute-verify-design.md)  
9. ~~**P8** Design-vs-actual + bug reports~~ — [`2026-08-23-execute-design-bugs-design.md`](./2026-08-23-execute-design-bugs-design.md)  
10. **Post-roadmap hardening** (execute vs conversion job separation) — [`../plans/2026-08-23-post-roadmap-hardening.md`](../plans/2026-08-23-post-roadmap-hardening.md) — in progress on branch `feat/heal-cascade-hardening`  

---

## 10. Spec self-review

- [x] No TBD placeholders for locked decisions  
- [x] Pillars separated; dependencies called out  
- [x] AI CSV decision (A) encoded  
- [x] Scope not conflated with “implement everything now”  
