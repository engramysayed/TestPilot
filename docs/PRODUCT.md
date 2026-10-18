# Keel — what the product can do today

This is the **full feature list** for people who will use or review Keel.  
It describes the portal as it exists now (October 2026), not only the last commit.

- **What just shipped** (dated notes): [`CHANGELOG.md`](../CHANGELOG.md)
- **How to run it**: [`README.md`](../README.md)
- **Engineer pointers**: [`ENGINEERING_HANDOFF.md`](ENGINEERING_HANDOFF.md)

Keel is a self-hosted portal. You sign in, create a **project** (site URL + login profiles), then work in **Generate** or **Record** → **Execute** and/or **Automate**, plus exploratory **Bug Hunter**.

```text
Stories / Excel / CSV          Click through the site in Chrome
        │                                  │
        ▼                                  ▼
   Generate   →  saved workbook     Record  →  saved case + locators
        │                                  │
        ├──────────────► Execute   →  live evidence, screenshots, bug export
        │
        ├──────────────► Automate  →  prove + heal → Selenium/TestNG ZIP
        │
        └──────────────► Bug Hunter → exploratory pack (bugs + candidate scenarios)

Record can also build the ZIP directly from the locators it captured, without a second browser visit.
```

---

## Sign in and accounts

- Sign in with email and password (`/login`).
- **Request access** from the login page. An admin approves; the requester gets a set-password invite.
- **Account**: change password, change email.
- **Account → vision**: choose the model that looks at the screen (UI-TARS by default, or Qwen vision). This does not choose the model that writes test cases. Writing uses the chat model chosen on Generate (live Ollama chat tags; vision names omitted).
- **Account → alerts**: Telegram, email, or Slack. Keel sends a message when a run fails or finishes with a block. Turning a channel on sends a confirmation first and does not save the switch if that send fails. The engine dead-man check is separate and stays off.
- **Pricing** (`/pricing`): a read-only table of each service, the unit it is charged as, and the current credit price. Every signed-in account sees the same numbers.
- **Admin → Prices**: the account owner sets the service name, Charged as, What it covers, and the price of one unit. Saving applies it to every account. A user whose role is only Admin is refused by the server.
- **Admin → LLM & API keys** (/admin/llm-settings): OWNER-only (same gate as Prices). Secrets are masked on GET (*Configured / *Last4). Process environment wins over published System properties and in-memory URL/model props. Clearing a key drops the runtime System property without restart. A workspace Admin role is refused by the server like Prices.
- **Admin → Users**, credit grants, and **Roles** are the same: only the account owner. Hiding the link is not the check.
- **Admin → Domains**: see stored site folders (projects, locator memory, maps); relocate legacy folders; delete a whole domain (typed confirm). A user with the Admin role can still open Domains.

---

## Dashboard

- Counts: active projects, proven pass rate, jobs that need a look, running jobs.
- Chart: cases per day, proven vs blocked, stacked. Filter by project or date.
- Recent runs: progress bar, short status, Status and Results buttons.
- **Needs your attention** cards are tinted: failed red, blocked amber, in-progress blue, design-mismatch green.
- Recent Automate jobs with links to status.

---

## Projects

A project is one target app.

### Create and list

- Create with a name and optional base URL.
- Each card has **Open project**, then **Archive** and **Delete**. Generate, Execute, Automate, and Bug Hunter stay in the sidebar.
- Inside a project, **Summary**, **Test cases**, **Packages**, and **Settings** are a short pill row. The open section is the filled pill.
- Delete-all is available on the list (admin-style danger).

### Settings

- Rename the project.
- Set **base URL** (required before Execute or Automate).
- **Authoring engine**: **Keel** (default) or **Precision — higher accuracy on complex UIs, Higher Cost**; optional per-project **max Cursor calls per job** (blank = server default).
- **Credential profiles**: named logins (username + write-only password). Used as `${TARGET_USERNAME}` / `${TARGET_PASSWORD}` at run time.
- **Design mockups**: optional PNG per `TC_ID` (e.g. Figma export). Execute compares that image to the live last screen. This is **not** a live Figma integration.
- Archive (hide) or permanently delete the project and its files.

### Automation tab

After Automate has produced a package:

- Browse **packages** (version ZIPs).
- Browse generated **page** classes and **tests** (generated vs todo).
- Preview Java source in the portal.
- Delete artifacts when no job is running.

### Test cases tab

- **Add test case** opens the same editor as an existing row. Keel assigns the next `TC_` id. **Save** writes the project library workbook. An empty library is OK — the first save creates the workbook.
- **Generated library**: cases saved from Generate. Refresh, edit fields (including KeelPath), delete selected (cannot delete the last case).
- **Version history**: every save is kept. Source reads as Edited / Recorded / Generated (not `CASE_EDIT`). Author is the person who saved; old Record rows that stored a test id such as `TC_3` show as —. **Current** is the live version. **Restore this version** writes a new save — history is never rewritten.
- **Automate proven cases**: last successful conversion IR (what the browser actually proved), not Generate-only drafts.

---

## Generate

Turn stories or AI output into a Keel workbook stored on the project (`generated/latest.xlsx` + CSV).

### From stories (Ollama)

- Paste user stories / acceptance criteria.
- Pick the Ollama **AI model**. The list comes from live Ollama chat tags (vision-like names such as `vl` / `llava` / `ui-tars` / `vision` are omitted). Admin → LLM & API keys Save sets the install default. If Ollama is down, the last list is used.
- **Generate** — background job + status page (avoids browser timeouts).
- **Second pass** — slower: generate, then ask the model to fix mistakes and add only gaps vs the stories. Keel also auto-repairs known empty-field TestData mistakes when this is off.
- **TestData inventing is off unless you say it is OK** in the stories. Unknown values stay blank or `<PLACEHOLDER>`. Login fields use `${TARGET_USERNAME}` / `${TARGET_PASSWORD}`, not faker names.

### Compare two models

- Pick model A and optional model B.
- **Compare two models** — side-by-side counts and preview.
- Nothing is saved until you **Save model A** or **Save model B**.

### All in one pipeline

- Confirm, then: Generate (with second coverage pass) → Automate → Execute.
- Stage links show where each part landed (workbook on Generate, ZIP on Automate, run on Execute).
- Authoring engine is taken from **project Settings** (not chosen on this page).

### Bulk generate

- Upload a bulk stories `.csv`.
- Queue a background batch; see recent bulk jobs.

### Paste from external AI

- Paste CSV or JSON from ChatGPT / another tool.
- **Import into project** — repair + quality gate.
- Copy the Keel prompt from this page (same idea as Guide).

### Preview and edit

- Table of generated cases.
- Per-row **KeelPath** (`AUTOMATE` / `EXECUTE` / `VISION_ONLY` / `MANUAL` / blank).
- Click a case to edit: title, preconditions, step × test-data grid, expected, priority, tags, visual assertion, KeelPath.
- Add/remove step rows.
- **Save coverage notes**.
- **Download CSV**.

### Quality gate (always)

On generate, import, save, and before run:

- Required columns: `TC_ID`, `Title`, `Steps`, `ExpectedResult`.
- Leave-empty / do-not-fill steps must have a blank TestData line.
- Vague asserts without a quoted UI message are rejected.
- Angle-bracket placeholders are not typed as literal text.
- No literal `\n` in step text.

### Review with AI (Generate)

Optional, after a workbook exists:

- Provider: **Cursor** or **Ollama**.
- Optional requirements notes (+ stories when the project has them).
- Returns findings and a proposed suite.
- **Accept proposal** writes the project workbook.
- **Discard** leaves the current file.
- Banner when the gate found issues; review never starts by itself.

---

## Record

`/record` — show Keel the steps in Chrome, then optionally write the framework from those locators.

- Pick a web project. Android projects are refused.
- Chrome opens headed, on the project website (or another address on that same site).
- Each click, field, and page change becomes an English step. A stable locator is kept with the action when the page has one (`data-testid`, `data-test`, `data-qa`, id, name, a short css or xpath).
- The Keel bar in that window: **Steps**, **Check this** (click the text that must be on the page), **This page** (confirm the address).
- Passwords are not stored. Choosing “sign in” only adds a note that the later run should use a project login.
- **Save** adds the next `TC_` case to the library. Blank KeelPath, so Execute and Automate can both use it.
- **Build the framework** starts the same framework writer Automate uses, and skips the browser prove pass. Chrome stays closed. It charges the framework price once for this test case. A step with no stable locator stays English only.
- **Run it once** is optional and off by default. It starts a normal Execute check and charges the run price once for this test case. Keel opens the site again and finds the buttons from the written steps. It does not reuse the recorded locators. Cursor is used only when that project’s authoring engine is Precision.
- One recording per person, 20 minutes maximum.

Plan: `docs/superpowers/plans/2026-10-15-record-to-framework.md`.

---

## Pricing

`/pricing` — view only. `/admin/pricing` — account owner only.

| Service | Charged as |
|---------|------------|
| Generate | One writing job |
| Execute | Each test case in the run, including Run it once |
| Automation | Each test case in the build, including a recording |
| Bug Hunter | Each step. A step is one click or one thing typed. The hunt page shows how many steps the run may try. |

The number on the page is the price of one unit. Keel multiplies it when the job starts. A manual-only row is not included in a run or a build. The default hunt may try 80 steps (2 iterations × 8 cycles × 5 actions).

---

## Guide

`/tc-guide` — authoring standard for anyone writing Excel.

- Excel columns and which are required / recommended / optional.
- Login & credentials (preconditions vs run config).
- How to write exact steps (not vague shorthand).
- Expected results (one observable outcome per step).
- Test data line-alignment (step *n* ↔ TestData line *n*).
- Visual assertion column (text only).
- What each case must include.
- **Not supported:** file-upload flows in the app under test.
- **AI rewrite → CSV**: copyable prompt + downloadable CSV template.

Workbook columns:

`TC_ID`, `Title`, `Steps`, `ExpectedResult`, `Preconditions`, `Priority`, `Tags`, `VisualAssertion`, `TestData`, `KeelPath`

---

## KeelPath (routing)

Each case can declare where it should run:

| Value | Meaning |
|-------|---------|
| `AUTOMATE` | Conversion / codegen path |
| `EXECUTE` | Live execute & verify |
| `VISION_ONLY` | Execute, vision-heavy |
| `MANUAL` | Skip both run surfaces |
| *(blank)* | Legacy: eligible on both Automate and Execute |

- **Automate** and **Execute** share the same runnable set: `AUTOMATE`, `EXECUTE`, `VISION_ONLY`, and blank. Only **MANUAL** is skipped on both surfaces.
- Banners show path counts. Wrong-surface submit is **blocked** when a surface has zero runnable rows. Minority-surface is a confirm warning.

---

## Bug Hunter

Exploratory live hunting from the project library. Goal: **break features** and **invent capped edge-case scenarios**. Humans review the pack; **nothing auto-merges** into the generated library.

### Start a hunt (`/bug-hunter`)

- Pick project (needs base URL when not dry-run).
- **Credential profile** — hunter receives `$TARGET_USERNAME` / `$TARGET_PASSWORD` / `$TARGET_OTP` **tokens only** (never plaintext password in planner prompts or pack text). Choose **Open site only** for hunts without credentials.
- Multi-select library TCs; optional free-text user story (OTP hints can be parsed from story text).
- Planner: **Ollama** or **Cursor**.
- Caps: scenario invent budget, iterations, cycles per iteration, **actions per cycle** (defaults 2 × 8 × 5). Each click or typed value is one step. The credit charge is the number of steps this run may try, times the Bug Hunter unit price. A compact bar above **Start Bug Hunter** shows that total and updates when the caps change. Execute and Automate use the same bar for the test cases in the run or the build.
- **DOM mode**: Auto (structured page map; full slim HTML if the map is thin) · Map only · Full slim.
- **Use hunt strategies** (default on): happy path → empty fields → boundary → abuse → session → invent.

**Preferred hooks:** if the project has preferred locator attributes configured, auto-login and hunt grounding prefer those patterns before generic CSS (configure on the project — no hardcoded vendor attrs in code).

### What each cycle does

1. Capture slim DOM + **one screenshot at cycle start** + optional network failures.
2. Build a **page map** (controls, headings, alerts, dialogs) for the planner; keep full slim on disk.
3. Send brief + **steps journal** + **coverage map** (including untested hints) + page map (± slim) + screenshot (when the model is multimodal, e.g. `gemma4:e2b`).
4. Execute allowlisted actions only; **reject invented locators** (`ungrounded_locator`). Actions include `restart_browser` (capped per hunt).
5. **Credential tokens** resolve at `type` time only (`HuntSecretResolver`); actions-log keeps token form.
6. **Post-click settle** (~1s) before the next action; optional alert/toast poll after login API failures.
7. Default **wait = 5s** when the model omits `ms`.
8. Append journal + coverage; **auto-advance strategy** when happy-path is blocked on login (≥2 cycles, still on login URL); **STUCK** on last mode / when strategies are off.

### End of hunt

- **Bug dedupe** — fingerprint duplicate titles/themes before pack write (`bug-dedupe.json`).
- **Bug triage** — one optional planner pass to keep/drop/merge accumulated bugs (`bug-triage.json`; soft-skips on bad JSON; when unsure, **keep**).
- **Login-feature hunts** — oracle does not flag “unexpected redirect to login” when the hunt is explicitly testing login.

### Outputs (downloadable ZIP)

- `brief.md`, `SUMMARY.md`, `steps-journal.md`, `coverage-map.md`
- `bug-report.json` / `.csv` (severity, repro, evidence hints) — post-dedupe/triage
- `bug-dedupe.json`, `bug-triage.json`, `login-prelude.txt` (hooks used / soft-fail; no password)
- `candidate-scenarios.json` / `.csv` (≤ invent cap)
- `cycles/cycle-NN/` — `page-map.*`, `dom-slim.txt`, screenshot, `planner-prompt.txt`, `planner-response.txt`, actions + network

Stop reasons: planner `finish`, cycle ceiling, **STUCK**, or **COMPLETE** (strategies exhausted + invent budget met + ≥1 bug).

### Limits (today)

- No two-pass “DOM neighborhood” planner (Phase 3 — only if AxisPay still blows context).
- Oracle drafts cover HTTP 4xx/5xx, failed asserts, and alerts; not full blank-main / unexpected-URL / `net::ERR_*` promotion yet.
- Dry-run simulates one cycle (stubs map/coverage); live needs `delivery.dry-run=false`.
- Candidate scenarios stay review-only until you import them yourself.
- End triage adds one extra planner call (cost/latency).

---

## Execute & verify

Run cases on the live site **without** building a customer ZIP.

### Start a run

- Pick project (needs base URL).
- Pick a credential profile when the project has them.
- Source: **project library** (all or selected `TC_ID`s) and/or upload `.xlsx` / `.csv`.
- Mix: library ∪ upload; **same `TC_ID` → upload wins**.
- Optional PNG design mockup per TC (collapsed by default; also on project Settings).
- A compact **This run** bar shows how many test cases × the Execute unit, and the total credits. Automate and Bug Hunter use the same bar.
- Optional **Review cases before run** (Cursor or Keel). Cases go out as JSON plus Keel authoring rules (quotes mean visible text at runtime, `and` splits, masked OTP wording, and similar). **Accept & run** saves the edited cases into the **library** (version history) and then starts the reviewed `TC_ID`s, even when the source was an uploaded workbook. Uncheck review to run the library as-is. Pre-run review is not charged; the Execute run price still applies when the run starts.
- **Start execute run**. The run price is charged once for each runnable test case. Cancel while running. (Authoring engine: **project Settings**.)

### While it runs

- Progress, passed, blocked/TODO, status page link.
- Mid-run TC list as each case finishes.
- Login cases that already contain login steps run **as written** (no extra prelude that strips the body).
- After opening a login URL, Keel waits for the page and a password field before typing (~12s).
- A click step that quotes a name (`Clicks "Support"`) can use a label that is not a button, link, or menu row, anywhere on the page. Words such as “in the sidebar” or “in the toolbar” do not have to be written on the element. A longer block of text that merely contains the name is not the target. Two elements with that exact name stay unresolved.
- After each action, Keel checks that row’s **Expected** cell when it is not empty. Confirm lines are classified (page / field / quote / echo) before the control shortlist is scored. Enabled/disabled confirms use real isEnabled checks (not mere visible). Nameless confirms skip-echo; unquoted error/success toasts bind textContains on the kind token. Extra expected lines with no step (a leftover `and the "…" is visible`) attach to the last real step. Numbered lists, dash bullets, and outcome `and` split; a sentence such as “expands and sub-menu items are shown” stays one check. A failed expected check is a failed test — it does not spend heal time looking for a locator.
- Locators on a page are collected once and reused until you navigate or the live DOM fingerprint changes (for example a sidebar expands). Bind uses that catalog. Vision may confirm a catalog row or add a control the catalog missed.
- If **every** case stays TODO / unproven, the job is **FAILED** (`ALL_CASES_TODO`), not a green complete.

### Results

- Recent runs for the project; **Results** opens that run’s evidence.
- Expand a row: step timeline + screenshots (including the last heal shot when a bind missed).
- QA column (pass / fail / blocked).
- **Design** column: live last screen vs uploaded PNG (dash = no reference).
- Export: bug report JSON, bug report CSV, results Excel + screenshots.

---

## Automate

Turn the workbook into a **downloadable Java Selenium TestNG** package.

### Hub (`/automate`)

- Pick project.
- New conversion → Upload.
- Job history.
- Last five jobs with status, progress, score.

### Upload & convert (`/upload`)

- Same library / upload / mix / CSV rules as Execute.
- **NEW** — full conversion for all eligible cases.
- **UPDATE** — only TCs whose Steps/ExpectedResult changed; needs a prior NEW (`UPDATE_WITHOUT_FRAMEWORK` if none).
- **Client delivery** checkbox — extra final-revise audit before download (off unless the server has `delivery.final-revise.enabled=true` and `AGENTROUTER_API_KEY`). You can still download; the job may be marked as needing review.
- Optional **Review cases before run** (Cursor or Keel), same library-save **Accept & run** behavior and quote rules as Execute. Accept starts the reviewed `TC_ID`s, even when the source was an uploaded workbook. Pre-run review is not charged; the framework price still applies per runnable test case when the job starts.
- KeelPath guard: cannot start Automate if there are no runnable rows (everything except `MANUAL`). The framework price is charged once for each runnable test case, not once for the package. (Authoring engine: **project Settings**.)

### What the job does (in order)

1. **Prove** — bind each Excel step to the live DOM.
2. **Heal** if bind fails: DOM candidates → Ollama shortlist → optional Cursor invent → structured **recovery** plan (validate on page, run, evidence, retry the same step).
3. Successful recovery may **patch the generated workbook** (e.g. leave-empty + blank TestData) so the next run matches the page.
4. **IR** (proven drafts) → optional revise → **Emit** page objects + tests.
5. Package the **customer-framework-template** into a ZIP.

### Jobs & status

- `/jobs` — Automate history, status, download.
- `/status?jobId=…` — live log, progress, download when ready, links back to Execute results or Generate compare.

Locator memory remembers working locators **per live page path**. Auth-only locators (`login-button`, username, password, …) are not stored against cart/checkout clicks.

---

## Authoring engine (Keel vs Precision)

Per project, under **Settings**, you choose how **Automate**, **Execute**, and **All in one** jobs bind intents to the live DOM during Prove.

| Engine | Bind path | Needs |
|--------|-----------|--------|
| **Keel** | Deterministic DOM bind; on failure → heal cascade (Ollama shortlist → Cursor pick/invent → recovery) | Local Ollama (for heal tiers) |
| **Precision** | DOM shortlist → one Cursor `groundRank` (multimodal) → bind on high/medium confidence; one `solve` on low; Keel fallback on cap/errors | `CURSOR_API_KEY` + `delivery.cursor-heal.enabled=true` |

**Project Settings** store `authoringEngine` and optional `precisionMaxCallsPerJob` per project.

**Server knobs** (`application.properties`):

- `delivery.authoring.precision.enabled` — when `false`, Precision projects fall back to Keel immediately; Settings shows a warning if Precision is selected.
- `delivery.authoring.precision.max-calls-per-job` — default cap when the project leaves max calls blank (default `50`).

**Per-job artifacts**: `automate-runs/{jobId}/request.json` or `execute-runs/{jobId}/request.json` records `authoringEngine`. IR drafts store per-TC precision call counts and fallback flags. Status page shows a muted banner when any intent used Keel fallback (`PRECISION_FALLBACK`).

**CLI**: `DeliveryCli --authoring-engine keel|precision`.

Design: `docs/superpowers/specs/2026-09-11-authoring-precision-engine-design.md`.

---

## Vision (optional)

The Account page chooses the vision model only: UI-TARS by default, or Qwen vision. It does not change the Generate writer (live Ollama chat models on Generate). The server still needs that vision model in Ollama (`delivery.vision.provider=qwen` is the Qwen fallback).

- Help find a control when DOM bind is weak (grounding).
- **VisualAssertion** column: after steps succeed, one VLM check (PASS / FAIL / UNCERTAIN) + evidence.
- Design PNG compare on Execute (separate from the text column).
- Heal prompts can include a short “vision already tried this” history (text, not raw PNG bytes).

---

## Command line

- `delivery.cli.DeliveryCli` — batch Automate conversion without the UI.
- `--authoring-engine keel|precision` (default `keel`).
- Ops notes: `docs/ops/` (Ollama, portal restart, Windows).

Start scripts:

- `start-keel.cmd` — portal, engine, and Ollama/Appium if they are down
- `start-portal.cmd` — portal only
- `start-engine.cmd` — engine only (`KEEL_RUNNER_TOKEN` in `api-keys.local.bat`)

---

## What Keel does **not** do (today)

Be explicit so a demo does not promise these:

| Not included | Notes |
|--------------|--------|
| File-upload steps in the **app under test** | Called out in the Guide |
| Live Figma / Jira / ALM sync | Design PNG is a manual upload |
| Captcha, passkeys, 2FA obstacles | Not an obstacle-solving agent |
| Silent TestData faker on Generate | Only if you allow inventing |
| Auto Cursor review before every run | Checkbox is opt-in |
| Claude / Anthropic models | Hard ban for this project |
| LLM Wiki | Parked |
| Public SaaS billing / multi-tenant cloud | Self-hosted |
| iframe / canvas / WebGL as first-class actions | Limited / follow-up |
| Stop-on-first-fail toggle on Execute | Continues past failed TCs |
| Bug Hunter auto-merge into library | Review pack only; import manually |
| Bug Hunter two-pass DOM (Phase 3) | Not built; map + slim fallback first |
| Recording an Android app | Record opens Chrome; mobile stays a separate path |
| Storing the password typed while recording | The later run uses a project login account |

---

## Suggested first-time path

1. Admin: change the default password. Start the portal. Hard-refresh after upgrades.
2. Create a **project**. Set base URL. Add a credential profile.
3. **Guide** — share columns and TestData rules with authors.
4. **Generate** from stories (or import CSV), or **Record** a case in Chrome. Check placeholders, not invented emails.
5. Optional: **Review with AI** on Generate → Accept to save.
6. Project → **Test cases** → edit the library if needed.
7. **Execute** a small subset. Open Results. Export a bug report if something failed.
8. **Automate** the AUTOMATE rows. Download the ZIP when the job completes.
9. Optional: **Bug Hunter** on a few library TCs — download the pack and review bugs/scenarios.

---

## Where the other docs fit

| Doc | Use it for |
|-----|------------|
| This file | “What can Keel do?” for everyone |
| `CHANGELOG.md` | “What changed in this publish?” |
| `README.md` | Install, stack, security |
| `docs/superpowers/specs/` | Why a slice was designed that way |
| `docs/ops/` | How to run Ollama / restart / Windows |

## Locator bind (Execute)

Bind prefers test-id / role+name from a scored shortlist; Ollama only on a close shortlist (TIE up to 5). After a fill, expected text like “Username is accepted” asserts that same field — it does not stop as a TIE against a same-name label.

