# TestPilot Delivery Platform — MVP Design

**Date:** 2026-08-04  
**Status:** Spec reviewed and approved by product owner (2026-08-04)  
**Product working name:** TestPilot Delivery Platform  
**Related codebase:** TestPilot (Java + Selenium + Gemini/LLM planner today → local AI authoring for MVP)

---

## 1. Problem & goal

### Problem
Many companies have manual test cases but **no SDETs**. Hiring and building an automation framework from scratch is slow and expensive. They need a delivered solution, not another expert tool.

### Goal
Provide a hosted service where a customer:

1. Uploads manual TCs in **our fixed Excel template**
2. Provides **application URL + login** for live authoring
3. Chooses **New framework** or **Update existing** (stored on our server)
4. Downloads a **ready-to-run ZIP**: Java + Selenium + TestNG framework with generated pages/tests, reporting, and CI template

They should feel clear time savings: manual suite → packaged automation without an in-house SDET team.

---

## 2. MVP scope

### In scope
- Thin customer portal: project, upload, New/Update, job status, ZIP download, automation score
- Conversion engine evolved from TestPilot: live browser authoring + validation
- **Local AI only** (no cloud AI in MVP) for authoring cost control
- One adaptable **framework core template** (domain-agnostic)
- Deterministic **code writer** that fills Page and Test class templates from proven steps
- Excel template–only ingest
- Partial success: all TCs in ZIP; failures become **TODO** tests with reason/evidence
- Server-side storage of customer frameworks + locator maps for Update
- Secrets handling: prefer not shipping customer passwords inside the ZIP

### Out of scope (later)
- Accepting random TC formats (Word/PDF/free-form without template)
- Git/PR sync to customer repos (planned after MVP)
- Billing / multi-tier SaaS packaging
- Non-Java stacks (Playwright/TS, etc.)
- Cloud AI fallback
- Full TMS connectors (TestRail/Zephyr) as primary ingest

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Thin Portal (website)                                  │
│  login → project → Excel upload → URL/login → New|Update│
│  → job status → automation score → download ZIP         │
└───────────────────────┬─────────────────────────────────┘
                        │ API
┌───────────────────────▼─────────────────────────────────┐
│  Control Plane                                          │
│  accounts, projects, jobs, framework store, zip artifacts│
└───────────────────────┬─────────────────────────────────┘
                        │ queue job
┌───────────────────────▼─────────────────────────────────┐
│  Conversion Engine (evolved TestPilot)                  │
│  parse Excel → local AI author → execute/validate       │
│  → code writer (pages/tests) → packager → ZIP           │
└─────────────────────────────────────────────────────────┘
```

### Boundaries
| Component | Responsibility |
|-----------|----------------|
| Portal | UX only; no Selenium |
| Control plane | Auth, projects, job lifecycle, stored frameworks, artifacts |
| Engine | Browser, local AI authoring, validation, code generation |
| Framework template | Fixed core used for every customer ZIP |

Approach: **Evolve TestPilot into Engine + thin Portal** (engine/API solid; portal thin).

---

## 4. Customer flows

### 4.1 New framework
1. Customer creates a Project
2. Enters Base URL + login (authoring credentials)
3. Uploads Excel (our template)
4. Selects **New**
5. Job runs on server (async)
6. Engine authors, executes, validates, writes pages/tests (or TODO stubs)
7. Packager copies **Core Template**, merges generated layer, adds CI + score report → ZIP
8. Portal offers download; server **stores** framework snapshot + locator map for future Update

### 4.2 Update framework
1. Same Project (framework already on our server)
2. Customer uploads new/changed Excel (full sheet allowed; system diffs by `TC_ID`)
3. Selects **Update**
4. Engine loads stored framework + locator map
5. **Reuses** stored steps/locators for unchanged TCs (no re-author)
6. **Local AI authors** only new/changed TCs
7. Merges into stored project; versions ZIP (v2, v3…)
8. Customer downloads updated ZIP; server keeps latest as source of truth

Git/PR delivery is explicitly **post-MVP**.

---

## 5. Excel template (only accepted input)

Customers must use **our** schema. Unknown formats are rejected.

Minimum columns:

| Column | Purpose |
|--------|---------|
| `TC_ID` | Stable id for New/Update diff |
| `Title` | Human-readable name |
| `Preconditions` | Optional setup notes |
| `Steps` | Numbered manual steps |
| `ExpectedResult` | Maps to assertions |
| `Priority` | Optional ordering |
| `Tags` | Optional (smoke, regression, …) |

MVP schema uses the column names above exactly. Extra columns are ignored; missing required columns (`TC_ID`, `Title`, `Steps`, `ExpectedResult`) reject the upload.

---

## 6. Authoring, AI, and locator policy

### 6.1 Role of AI
- **Local AI always authors** new/changed TCs (same spirit as today’s planner): given Excel steps + page state (slim DOM + screenshot) → actions, locators, waits, assertions in strict JSON.
- AI is the **authoring brain**, not the forever CI runner.
- After a TC is proven, durable TestNG tests are written and **replayed without AI**.

### 6.2 Rules vs AI
- **Rules** are not a second author. They are:
  1. **Policy inside the AI prompt** (locator preference order, rejects, uniqueness, etc.)
  2. **Validators after AI output** (reject dynamic ids, absolute xpath, non-unique locators → retry or TODO)
- **Skip AI call** only when Update can **reuse** an already stored proven step/locator for an unchanged TC (cache), not when inventing new automation.

### 6.3 Local AI (cost)
- MVP: **fully local** on our server (e.g. Ollama-class / self-hosted model).
- **No cloud AI** by default (no Gemini API dependency for MVP authoring).
- Hermes-style agent harnesses may wrap the local model later; the cost win is local inference + fewer calls + durable replay.

### 6.4 Locator policy (must enforce)
1. Prefer: `data-testid`/`data-qa` → stable `id` → `name` → aria/role+name → labeled controls → CSS → relative XPath → absolute XPath last
2. Reject dynamic ids (uuid, hashes, timestamps, framework noise)
3. Locator must be unique (and remain unique after refresh when feasible)
4. Target the interactive element, not a meaningless wrapper
5. Prefer visible/enabled elements; allow scroll-into-view
6. Prefer role-based locators when accessible name is stable
7. Avoid pure visible-text locators when i18n/copy churn is likely
8. Capture iframe / shadow DOM / modal context
9. Store primary locator + 1–2 fallbacks for future heal
10. Never invent extra steps beyond Excel intent
11. Keep assert locators separate from action locators
12. Record why a locator was chosen (for TODO/debug reports)
13. On Update: **consult stored locator map before asking AI**

---

## 7. Conversion pipeline (per TC)

```
Excel row
  → if Update & TC unchanged & stored → reuse (no AI)
  → else LOCAL AI AUTHORS (steps JSON under locator policy)
  → EXECUTE in real browser (URL + login pre-steps done)
  → VALIDATE (action success + expected-result checks)
  → PASS  → durable TestNG test + page object / locator map entries
  → FAIL  → TODO test stub + reason + evidence (screenshot/notes)
  → next TC
→ package ZIP
```

Partial packages are always allowed: **all TCs appear in the ZIP**; failures are TODO-marked, not omitted.

---

## 8. Customer framework design

### 8.1 Principle
One **company-owned adaptable core** works for any customer/domain. Per customer, only **pages** and **test classes** (plus config values) are filled in.

```
FRAMEWORK CORE (fixed)     +     CUSTOMER LAYER (generated)
drivers, waits, base test,       pages/
reporting, CI, utils             tests/generated/
                                 tests/todo/
                                 config placeholders
                                 locator map
```

### 8.2 Who designs what
| Artifact | Designer | Mechanism |
|----------|----------|-----------|
| Class structure, naming, folders, base classes | **Us** (template) | Designed once, versioned |
| Locators, page actions, test steps for this app | **Engine** | Local AI authors proven steps → **deterministic code writer** fills templates |
| Domain knowledge | Not in core | Only in generated pages/tests |

AI must **not** freely invent arbitrary Java style. The **code writer** prints consistent, editable classes from proven step JSON using our templates.

### 8.3 Suggested ZIP layout

```
CustomerFramework/
  pom.xml
  .github/workflows/ci.yml          # or equivalent CI template
  README.md
  src/main/java/.../core/           # drivers, waits, base, reporting, utils
  src/test/java/.../
    pages/                          # generated + editable
    tests/generated/                # passed TCs
    tests/todo/                     # failed TCs as TODO
    runners/                        # optional
  src/test/resources/
    config/webapp.properties.example
    suites/
  docs/
    AUTOMATION_SCORE.md
    LOCATOR_MAP.json
```

### 8.4 Page & test pattern
- Pages: one class per screen/area; locators + small actions
- Tests: call pages, not raw Selenium; one class per `TC_ID` or feature group
- Core never encodes a specific business domain

---

## 9. Portal, jobs, security

### Portal (MVP)
Sign-in → Project → URL/login → Excel upload → New|Update → job progress → score → ZIP download.

### Jobs
Async worker per upload; progress by TC; clear failure reasons (bad Excel, login fail, site down).

### Security
| Topic | MVP rule |
|-------|----------|
| App credentials | Used for authoring job; prefer **not** embedded in ZIP; customer fills local config |
| Artifacts | Per-project; account-scoped download |
| Page HTML/screenshots | Stay on our server; processed by **local AI only** |
| Browser | Headless (or dedicated workers) on our infrastructure |

### Server stores per project
- Framework snapshot(s) / versions  
- Locator map  
- Last Excel + job results  
- ZIP artifacts (v1, v2, …)

---

## 10. Success criteria (MVP)

- Customer with no SDET can upload our Excel + URL/login and download a runnable ZIP
- `mvn test` (or documented command) runs generated tests; reporting works; CI file is present
- New and Update both work against server-stored frameworks
- Passed TCs are real tests; failed TCs are visible TODOs with reasons
- No cloud AI calls required for authoring
- Framework core is reusable across unrelated customer domains

---

## 11. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Local model quality weaker than cloud | Strong locator policy + execute/validate gate + TODO path |
| Flaky apps / dynamic UIs | Fallbacks in locator map; TODO over false-green |
| Secret leakage in ZIP | Example config only; README for local secrets |
| Update overwrites customer edits | Generate into dedicated folders; protect core; document editable zones |
| Long jobs | Async portal status; per-TC progress |

---

## 12. Phased follow-ons (not MVP)

1. Git/PR update to customer repos  
2. Optional core rebase on Update (newer reporting/drivers)  
3. Self-heal on CI failure  
4. TMS connectors  
5. Time-saved / ROI dashboard for sales  
6. Additional language stacks  

---

## 13. Implementation stance

- **No development** until the product owner explicitly requests build.
- Next process step after this spec is reviewed: implementation plan (Superpowers `writing-plans` / Spec Kit plan+tasks), then implement only on go-ahead.

---

## Approval record

- Architecture, flows, Excel, AI/local, framework template, portal/security: **approved in design workshop 2026-08-04**
- Written spec status: **awaiting user review of this file**
