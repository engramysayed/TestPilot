# Keel — what the product can do today

This is the **full feature list** for people who will use or review Keel.  
It describes the portal as it exists now (September 2026), not only the last commit.

- **What just shipped** (dated notes): [`CHANGELOG.md`](../CHANGELOG.md)
- **How to run it**: [`README.md`](../README.md)
- **Engineer pointers**: [`ENGINEERING_HANDOFF.md`](ENGINEERING_HANDOFF.md)

Keel is a self-hosted portal. You sign in, create a **project** (site URL + login profiles), then work in **Generate** → **Execute** and/or **Automate**, plus exploratory **Bug Hunter**.

```text
Stories / Excel / CSV
        │
        ▼
   Generate   →  saved workbook (project library)
        │
        ├──────────────► Execute   →  live evidence, screenshots, bug export
        │
        ├──────────────► Automate  →  prove + heal → Selenium/TestNG ZIP
        │
        └──────────────► Bug Hunter → exploratory pack (bugs + candidate scenarios)
```

---

## Sign in and accounts

- Sign in with email and password (`/login`).
- **Request access** from the login page. An admin approves; the requester gets a set-password invite.
- **Account**: change password, change email.
- **Admin → Users**: user list, access requests, pending invites, invite email status.
- **Admin → Domains**: see stored site folders (projects, locator memory, maps); relocate legacy folders; delete a whole domain (typed confirm).

---

## Dashboard

- Counts: active projects, Automate conversions, Execute runs, running / completed / failed, passed, blocked, pass rate.
- Charts: results by day, job status, Automate results, Execute runs.
- Recent Automate jobs with links to status.

---

## Projects

A project is one target app.

### Create and list

- Create with a name and optional base URL.
- Open, archive, or delete one project. Delete-all is available on the list (admin-style danger).

### Settings

- Rename the project.
- Set **base URL** (required before Execute or Automate).
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

- **Generated library**: cases saved from Generate. Refresh, edit fields (including KeelPath), delete selected (cannot delete the last case).
- **Automate proven cases**: last successful conversion IR (what the browser actually proved), not Generate-only drafts.

---

## Generate

Turn stories or AI output into a Keel workbook stored on the project (`generated/latest.xlsx` + CSV).

### From stories (Ollama)

- Paste user stories / acceptance criteria.
- Pick the Ollama model.
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

- **Automate** runs `AUTOMATE` + blank. Skips EXECUTE / VISION_ONLY / MANUAL.
- **Execute** runs AUTOMATE + EXECUTE + VISION_ONLY + blank. Skips **MANUAL** only.
- Banners show path counts. Wrong-surface submit is **blocked**. Minority-surface is a confirm warning.

---

## Bug Hunter

Exploratory live hunting from the project library. Goal: **break features** and **invent capped edge-case scenarios**. Humans review the pack; **nothing auto-merges** into the generated library.

### Start a hunt (`/bug-hunter`)

- Pick project (needs base URL when not dry-run).
- Multi-select library TCs; optional free-text user story.
- Planner: **Ollama** or **Cursor**.
- Caps: scenario invent budget, cycle ceiling, **actions per cycle** (default 5).
- **DOM mode**: Auto (structured page map; full slim HTML if the map is thin) · Map only · Full slim.
- **Use hunt strategies** (default on): happy path → empty fields → boundary → abuse → session → invent.

### What each cycle does

1. Capture slim DOM + **one screenshot at cycle start** + optional network failures.
2. Build a **page map** (controls, headings, alerts, dialogs) for the planner; keep full slim on disk.
3. Send brief + **steps journal** + **coverage map** + page map (± slim) + screenshot (when the model is multimodal, e.g. `gemma4:e2b`).
4. Execute allowlisted actions only; **reject invented locators** (`ungrounded_locator`).
5. Default **wait = 5s** when the model omits `ms`.
6. Append journal + coverage; advance strategy on repeated fails (or **STUCK** on last mode / when strategies are off).

### Outputs (downloadable ZIP)

- `brief.md`, `SUMMARY.md`, `steps-journal.md`, `coverage-map.md`
- `bug-report.json` / `.csv` (severity, repro, evidence hints)
- `candidate-scenarios.json` / `.csv` (≤ invent cap)
- `cycles/cycle-NN/` — `page-map.*`, `dom-slim.txt`, screenshot, `planner-prompt.txt`, `planner-response.txt`, actions + network

Stop reasons: planner `finish`, cycle ceiling, **STUCK**, or **COMPLETE** (strategies exhausted + invent budget met + ≥1 bug).

### Limits (today)

- No two-pass “DOM neighborhood” planner (Phase 3 — only if AxisPay still blows context).
- Oracle drafts cover HTTP 4xx/5xx, failed asserts, and alerts; not full blank-main / unexpected-URL / `net::ERR_*` promotion yet.
- Dry-run simulates one cycle (stubs map/coverage); live needs `delivery.dry-run=false`.
- Candidate scenarios stay review-only until you import them yourself.

---

## Execute & verify

Run cases on the live site **without** building a customer ZIP.

### Start a run

- Pick project (needs base URL).
- Pick a credential profile when the project has them.
- Source: **project library** (all or selected `TC_ID`s) and/or upload `.xlsx` / `.csv`.
- Mix: library ∪ upload; **same `TC_ID` → upload wins**.
- Optional PNG design mockup per TC (also on project Settings).
- Optional **Review selected TCs with Cursor before run** (propose only). **Accept & run** uses that CSV **for this job only** — it does not save the library.
- **Start execute run**. Cancel while running.

### While it runs

- Progress, passed, blocked/TODO, status page link.
- Mid-run TC list as each case finishes.
- Login cases that already contain login steps run **as written** (no extra prelude that strips the body).
- After opening a login URL, Keel waits for the page and a password field before typing (~12s).
- If **every** case stays TODO / unproven, the job is **FAILED** (`ALL_CASES_TODO`), not a green complete.

### Results

- Recent runs for the project; **Results** opens that run’s evidence.
- Expand a row: step timeline + screenshots.
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
- Optional Cursor **pre-run review** (same propose-only rule as Execute).
- KeelPath guard: cannot start Automate if there are no AUTOMATE/blank rows.

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

## Vision (optional)

Requires a vision model in Ollama (UI-TARS by default; `delivery.vision.provider=qwen` to fall back).

- Help find a control when DOM bind is weak (grounding).
- **VisualAssertion** column: after steps succeed, one VLM check (PASS / FAIL / UNCERTAIN) + evidence.
- Design PNG compare on Execute (separate from the text column).
- Heal prompts can include a short “vision already tried this” history (text, not raw PNG bytes).

---

## Command line

- `delivery.cli.DeliveryCli` — batch conversion without the UI.
- Ops notes: `docs/ops/` (Ollama, portal restart, Windows).

Start scripts:

- `start-portal.bat`
- `start-portal-with-cursor-heal.bat` (needs `CURSOR_API_KEY`, never commit keys)

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
| Pre-run Accept writing the library | Use Generate Accept or project edit |
| Claude / Anthropic models | Hard ban for this project |
| LLM Wiki | Parked |
| Public SaaS billing / multi-tenant cloud | Self-hosted |
| Version history of the generated library | One current workbook |
| iframe / canvas / WebGL as first-class actions | Limited / follow-up |
| Stop-on-first-fail toggle on Execute | Continues past failed TCs |
| Bug Hunter auto-merge into library | Review pack only; import manually |
| Bug Hunter two-pass DOM (Phase 3) | Not built; map + slim fallback first |

---

## Suggested first-time path

1. Admin: change the default password. Start the portal. Hard-refresh after upgrades.
2. Create a **project**. Set base URL. Add a credential profile.
3. **Guide** — share columns and TestData rules with authors.
4. **Generate** from stories (or import CSV). Check placeholders, not invented emails.
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
