# Portal UX overhaul — plan

Signed off by the owner 2026-11-15. One PR per step.

## The 6 rules (every page must follow)

1. **One job per page.** >1 primary action → split into sidebar children. No tabs.
2. **Choose before submit.** Settings that change the outcome sit above the submit button on the same card. No "Advanced" accordions hiding the real knobs.
3. **Kill helper prose.** Headings and field labels only. Explanations → `(?)` tooltip or `/guide`.
4. **Overflow `⋯` menu for secondary actions.** Each card: 1 primary + optional 1 secondary button, the rest in `⋯`.
5. **Empty states are one line + one button.** "No suites yet — [Create suite]".
6. **Reuse `keel-*.css` tokens.** No new design system, no Bootstrap/Tailwind swap.

## Sidebar — new structure

```
Dashboard
Projects

Generate
  ├─ From stories           /generate
  ├─ Compare models         /generate/compare      NEW route
  └─ From knowledge         /generate-from-knowledge
Record                     /record

Plain cases                /plain-cases
Structured plans           /structured-plans

Suites
  ├─ Your suites           /suites                (list + tags only)
  ├─ Cases & plan          /suites/plan           NEW route
  └─ Scheduling            /suites/schedule       NEW route (automatic runs + alerts + monitor)
Execute                    /execute
Run memory                 /run-memory
Automate                   /automate

Runs                       /runs
Bug Hunter                 /bug-hunter

Pricing · Guide · Account
Admin …
```

## Per-page audit (what to do on each)

Columns: **lines / clickables / panels** today → **target** after cleanup.

| Page | Now | What goes | What stays | Split? |
|------|-----|-----------|------------|--------|
| **dashboard** | 319 / 1 / 12 | guide-promo card, "A clearer view…" marketing H1 | Execution outcomes chart, Needs attention, Recent runs | add 3 KPI tiles (success rate, avg run time, jobs today) above chart |
| **generate** | 531 / 20 / 25 | Compare section (whole section + buttons), "What this does" prose | Upload stories, generate button, result preview | → `/generate` (from stories) + `/generate/compare` |
| **generate-from-knowledge** | 813 / 7 / 7 | all intro paragraphs, duplicated tip boxes | form + results | no |
| **record** | 601 / 4 / 3 | inline "how it works" prose | record flow | no |
| **suites** | 350 / 15 / 24 | tags/cases/plan/alerts/monitor panels | suite list + tags | → `/suites` + `/suites/plan` + `/suites/schedule` |
| **execute** | 488 / 17 / 20 | inline help text, duplicated status strings | main form, run button | no — but move 2nd-tier buttons to `⋯` |
| **plain-cases** | 585 / 5 / 7 | section intros | list + upload | no |
| **structured-plans** | 153 / 6 / 4 | header prose | form | no |
| **run-memory** | 137 / 3 / 4 | — | — | no (already clean) |
| **bug-hunter** | 258 / 5 / 12 | "How it works" panel, "Tips" panel | form + results | no |
| **evidence** | 227 / 9 / 10 | repeated per-section intros | evidence list, actions | no |
| **status** | 150 / 5 / 9 | — | — | no (already clean) |
| **jobs** | 111 / 1 / 3 | — | — | no (already clean) |
| **projects** | 407 / 12 / 4 | intro prose | list + create | no |
| **project-detail** | 948 / 53 / 49 | **biggest offender** — 21 h2, 49 panels on one page | — | → tabs collapsed into side-nav children: Overview / Settings / Members / Credentials / Danger zone |
| **upload** | 940 / 8 / 10 | step-by-step prose | upload form, result | no |
| **tc-guide** | 530 / 1 / 18 | duplicate code blocks | guide content | no (reference doc) |
| **guide** | 1066 / 1 / 64 | — | — | leave (is the guide) |
| **account** | 246 / 3 / 8 | — | profile, prefs, danger | split Danger zone to `/account/danger` |
| **packages** | 205 / 6 / 4 | hype copy | pick a plan | no |
| **pricing** | 86 / 0 / 1 | — | — | no |
| **admin-users** | 619 / 19 / 7 | — | user list + filters | split "Create / invite" to `/admin/users/invite` |
| **admin-roles** | 363 / 3 / 3 | — | — | no |
| **admin-domains** | 197 / 4 / 2 | — | — | no |
| **admin-llm-settings** | 459 / 5 / 1 | — | — | no (settings are the job) |
| **admin-prompts** | 370 / 2 / 2 | — | — | no |
| **admin-refunds** | 193 / 6 / 2 | — | — | no |
| **admin-pricing** | 53 / 1 / 1 | — | — | no |

## Dashboard — new layout

Replace "A clearer view of every run." marketing H1 with a real title: **"Dashboard"**. Drop the guide promo card.

```
┌─────────────────────────────────────────────────────────┐
│  KPI row (3 tiles, big number + trend sparkline)        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐          │
│  │ Success    │  │ Avg run    │  │ Runs       │          │
│  │ 94%  ▲2%   │  │ 2m 14s     │  │ today  12  │          │
│  │ ∿ sparkline│  │ ∿ sparkline│  │ ∿ sparkline│          │
│  └────────────┘  └────────────┘  └────────────┘          │
│                                                         │
│  Execution outcomes  (existing chart, same component)   │
│                                                         │
│  Needs your attention  │  Recent runs (last 10, tight)  │
│  (two-column on wide)  │                                │
└─────────────────────────────────────────────────────────┘
```

No new JS framework. Data for the 3 KPI tiles comes from the same endpoint the chart already uses — one extra field per period, or a tiny `/api/dashboard/kpi` endpoint if cleaner.

## Execution order

| # | Step | PR | Risk |
|---|------|----|------|
| 1 | Generate split (`/generate` + `/generate/compare`) | 1 | low |
| 2 | Suites split (3 pages) | 1 | medium — most JS to move |
| 3 | Dashboard redesign + KPI tiles | 1 | low |
| 4 | Project-detail split into child pages | 1 | medium |
| 5 | Prose cull on: execute, record, upload, plain-cases, structured-plans | 1 | low |
| 6 | Prose cull on: bug-hunter, evidence, generate-from-knowledge | 1 | low |
| 7 | Prose cull on: projects, packages, account | 1 | low |
| 8 | Admin: invite split + prose cull | 1 | low |

Each step: templates + any new controller mapping + sidebar entry + CSS additions + `mvn -pl keel-portal test` green before commit.

## Out of scope

- No framework swap (still Thymeleaf).
- No i18n expansion (keeps existing `#{keys}`, we may add a handful).
- `/guide`, `/tc-guide`, `/admin-llm-settings` — intentionally dense reference pages, leave content alone (only visual polish if cheap).
- Mobile layouts stay as they are unless already broken.
