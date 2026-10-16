# Keel changelog

This is the **public story of what shipped**. When you publish, add a dated section here first. Write for everyone: what changed, why it matters, how to use it, and what still does not work.

The **full product** (every surface, not only the last week) is [`docs/PRODUCT.md`](docs/PRODUCT.md).

Internal design notes stay under `docs/superpowers/`. Engineers can start from `docs/ENGINEERING_HANDOFF.md`.

## 2026-10-16 — Smaller run price, clearer history, colored attention

**Surfaces:** **Execute**, **Automate**, **Bug Hunter**, **project Test cases**, **Overview**.

The **This run** price is a compact bar (count × unit on the left, total credits on the right), not a large card. The same bar is on Automate and Bug Hunter. Design mockups on Execute start collapsed, with a teal row you click to open. Version history copy is plain: Current (not “head”), Edited / Recorded / Generated instead of `CASE_EDIT`, and **Restore this version** sits on the same line as the filters. New Record and edit saves store your email as Author; old rows that stored `TC_3` show as —. Failed attention cards are red, blocked ones amber. Rebuild the portal jar and Ctrl+F5. Known defect: history rows saved before this still have a test-id author in the file; only the label is cleaned up.

## 2026-10-16 — Expected after each step, and a page locator catalog

**Surfaces:** **Execute**, **project test-case editor**.

Each click/type is followed by that row’s Expected cell when the cell is not empty. A leftover Expected on a blank “Step action” row folds into the last real step — there is no assert without a step. Numbered lists (`1. 2. 3.`), dash bullets, and outcome `and` split; “expands and sub-menu” stays one check. A failed assert stops; it does not start a 2-minute locator heal. Locators for a page are catalogued once and reused until the URL or the DOM fingerprint changes (navigation or a same-page expand). If bind misses, vision may confirm a catalog row or add a control it saw that the catalog did not. Rebuild the portal jar and Ctrl+F5. Known defect: Excel copy must still match the screen (`Upload Transaction List` vs `Upload Transactions List` still fails the assert, on purpose).

## 2026-10-16 — Overview chart, run progress, and missing screenshots

**Surfaces:** **Overview**, **Runs**, **Execute results**, **Record**.

The outcomes chart stacks proven vs blocked with rounder bars. Recent runs show a progress bar, a short status word (Done / Failed / Running), and Status / Results as buttons. Execute results now find screenshots stored under the per-run folder (`TC_01__occ_1`). A blocked bind also shows the heal screenshot. The Record name field no longer says “Describe it like you would to a colleague.” Rebuild and restart the portal; Ctrl+F5.

## 2026-10-16 — Named labels are clickable

**Surfaces:** **Execute**.

A step such as `Clicks "Support" in the sidebar` can land on a label that is not a button, link, or menu row. The quoted name is enough. The same rule applies in a toolbar, a dialog, a table, or anywhere else on the page. A wrapper whose text is longer than that name is not clicked. Two elements with the same name stay unresolved. Restart the portal from a new build before the next run; the copy that is already running does not have this yet.

## 2026-10-16 — Live credit total

**Surfaces:** **Bug Hunter**, **Execute**, **Automate**.

The cost sits in a card above the start button. Bug Hunter updates when iterations, cycles, or actions change. Execute and Automate update when you switch to the project library and change how many test cases are included. A workbook upload shows the price of one test case until the file is read at the start of the job. Hard-refresh if you still see the small grey sentence.

## 2026-10-16 — Dashes and dots on Execute

**Surfaces:** **Execute**, and a few labels that used the same broken characters.

A dash (`—`) and a middle dot (`·`) had been saved with the wrong encoding, so the page showed `Ã¢â‚¬â€` and `Ã‚Â·`. They read as punctuation again. The first row in **Credential profile** is the empty choice “— Select profile —”. It is not an account. Hard-refresh if the old letters are still on screen.

## 2026-10-15 — Project section tabs

**Surfaces:** a project page.

Summary, Test cases, Packages, and Settings sit in a short pill row. The open section is the filled pill, in light and in dark. Hard-refresh if the old underline is still on screen.

## 2026-10-15 — Prices per unit, and only the account owner can change them

**Surfaces:** **Pricing** (`/pricing`), **Prices** (`/admin/pricing`), **Administration**, **Roles**, **Execute**, **Automate**, **Bug Hunter**, **Record**, **Guide**.

### What you can do

- Open **Pricing** and read the price of one unit for each service. The table cannot be edited.
- As the account owner, open **Admin → Prices** and change the service name, **Charged as**, **What it covers**, and the price of one unit. Saving applies that to every account.
- A run charges the Execute price once per test case. A framework build charges the Automation price once per test case, including a build from a recording. A manual-only row is not charged.
- Bug Hunter charges once per step. A step is one click or one thing typed. The hunt page shows how many steps this run may try before you start.
- Writing test cases stays one price for the whole writing job.
- The service names on Pricing are **Generate**, **Execute**, **Automation**, and **Bug Hunter**.

### Why

A flat price treated a one-case run and a large run as the same job. Hiding an admin button was not enough to keep prices, users, and credit grants with the account owner.

### How to use it

1. **Pricing** shows the current unit prices.
2. The account owner opens **Prices**, edits the name, Charged as, What it covers, and the credits for one unit, and saves.
3. Start a run, a framework build, or a hunt as usual. Keel multiplies the unit price by the cases or steps in that job.
4. If 15 was the old price of a whole Bug Hunter run, lower the Bug Hunter unit. The default hunt may try 80 steps (2 × 8 × 5), so 15 each costs 1,200.

### What we did not change

- Product credits stay on. Grants are still manual.
- Writing test cases is still one charge for the job, not one charge per story.
- Domains stays available to a user whose role is Admin.

### Known limits

- The server refuses a non-owner, including a role named Admin, on prices, user management, credit grants, and roles. The check is the stored Owner role, not a hidden button.
- A default Bug Hunter at 15 credits per step is expensive until the owner sets a smaller unit.
- This note is written while those prices can still be edited again.

## 2026-10-15 — Record a test, then build the framework

**Surfaces:** **Record** (`/record`), **Account** alerts, **Generate**, **Execute**, **Automate**, **Guide**, project cards, roles.

### What you can do

- Open **Record** from the sidebar and click through the project website in Chrome. Keel writes the steps into the project library.
- Use the Keel bar inside that Chrome window: **Steps**, **Check this** (confirm visible text), and **This page** (confirm the address).
- On the last step, choose **Build the framework from this recording**. Keel writes the Selenium framework from the locators it already captured. Chrome does not open again.
- Turn on Telegram, email, or Slack on **Account**. Keel sends a message when a run fails or finishes with a block, after a confirmation send succeeds.
- See extra Generate, Execute, and Automate fields already open. Project cards keep Open, Archive, and Delete. Roles use one multi-select for permissions.

### Why

A saved recording was only English steps. Automate had to open the site again to find locators. Alerts were saved in the browser and never sent.

### How to use it

1. Create a project and set its website.
2. **Record** → name the test → start Chrome → click through the site. Use **Check this** on the text that must be true.
3. Stop, leave **Build the framework** checked, and save. Watch the job under Runs. The download appears under Code packages.
4. On **Account**, turn on a channel only after that channel is configured. Saving sends a confirmation first.

### What we did not change

- Product credits stay on. A framework build is an Automate job and spends credits.
- The normal Automate path still opens Chrome and proves the case.
- Bug Hunter findings still do not join the library by themselves.
- The engine dead-man alert stays off and log-only.

### Known limits

- Recording is web only. An Android project is refused.
- Passwords typed in Chrome are not stored. A sign-in note tells the later run to use a project login.
- A click with no stable locator stays as written English and is left out of the framework.
- One recording at a time, for at most 20 minutes.
- This machine has no Telegram token and no mail server, so those switches stay off until they are configured.

**Plan:** `docs/superpowers/plans/2026-10-15-record-to-framework.md`.

---

## How to add a publish note

1. List every user-visible change (screens, buttons, job behavior), not file names.
2. Group by **what a person can do now**.
3. Add a short **How to use it** for each group.
4. Add **What we did not change** and **Known limits** so nobody assumes a silent extra feature.
5. Point at the git commit(s).

---

## 2026-09-11 — Bug Hunter quality pass 2

**Surfaces:** **Bug Hunter** (`/bug-hunter`), shared `JobLoginService` login CSS.

### What you can do

- Pick a **credential profile** on Bug Hunter — the planner sees `$TARGET_USERNAME` / `$TARGET_PASSWORD` / `$TARGET_OTP` tokens only (password resolved at execute time, never written to prompts or pack files).
- Choose **Open site only** when you do not want auto-login.
- Benefit from **preferred-hook-aware login** when the project has locator hook attributes configured.
- Get fewer duplicate bugs: **fingerprint dedupe** plus an optional end-of-hunt **triage** pass (`bug-dedupe.json`, `bug-triage.json`).
- Hunts auto-**advance strategy** when happy-path is stuck on login; login-feature hunts no longer get false “unexpected redirect to login” oracle noise.
- Planner may use **`restart_browser`** (capped) and post-click **settle waits** for more reliable asserts.

### Why

Live hunts on Axis-style apps were noisy (dozens of duplicate bug rows) and often failed login because hooks and credential tokens were not wired through to the planner/executor.

### How to use it

1. Set project **base URL** and a **credential profile** (or Open site only).
2. Configure **preferred hooks** on the project if your app uses custom `data-*` test ids.
3. Start a hunt; download the pack and review `bug-triage.json` if triage ran.

### Known limits

- End triage adds one planner call; soft-skips on invalid JSON (keeps deduped list).
- Auto-login success may skip the login page — for login-page testing prefer Open site only or `restart_browser`.
- Phase 3 two-pass DOM neighborhoods still not built.

**Specs:** `docs/superpowers/specs/2026-09-10-bug-hunter-quality-pass2-design.md`.

---

## 2026-09-11 — Precision authoring engine (Keel vs Cursor)

**Commit:** `d4927cc` — *feat(authoring): add Precision engine with review fixes and pipeline toggle*  
**Surfaces:** **Automate**, **Execute**, **Generate → All in one**, CLI, job status API.

### What you can do

- Choose **Keel** (default) or **Precision (Cursor)** when starting Automate, Execute, or the All-in-one pipeline.
- Precision uses Cursor `groundRank` (multimodal) plus an optional one-shot `solve` per intent, with automatic **Keel fallback** when the API is missing, the cap is hit, or bind fails.
- See per-job fallback on the status page (`PRECISION_FALLBACK` banner) and in IR drafts (per-TC precision metadata).
- Run batch conversion from CLI: `--authoring-engine precision`.

### Why

Keel bind + heal is fast and deterministic but can struggle on dense or dynamic UIs. Precision trades API calls for stronger initial grounding while keeping Keel as the safety net.

### How to use it

1. Set `CURSOR_API_KEY` and start with `start-portal-with-cursor-heal.bat` (or equivalent env).
2. On **Automate**, **Execute**, or **Generate → All in one**, pick **Precision (Cursor)**.
3. Watch the job status — if Precision could not bind an intent, the banner explains Keel fallback.
4. Server-wide off switch: `delivery.authoring.precision.enabled=false` (portal warns when Precision is selected).

### What we did not change

- **Keel remains the default** for every surface unless you opt in.
- Generate / Compare / Bug Hunter planner paths are unchanged.
- Bug Hunter and Hunt changes in the working tree are **not** part of this publish.

### Known limits

- ~50 Cursor calls per job by default (`delivery.authoring.precision.max-calls-per-job`).
- Precision does not replace heal cascade — it improves the **first** bind; failures still flow through Ollama/Cursor invent/recovery (within the shared cap on Precision jobs).
- No Claude / Anthropic models (project rule).

**Specs:** `docs/superpowers/specs/2026-09-11-authoring-precision-engine-design.md`, fix plan `docs/superpowers/plans/2026-09-11-precision-engine-fixes.md`.

---

## 2026-09-10 — Bug Hunter (exploratory) + quality loop

**Commits:** `793a7a6` — *feat(hunt): ship Bug Hunter with page map, coverage, strategies, oracles* (plus docs follow-up).  
**Surfaces:** new top-nav **Bug Hunter**, job kind `HUNT`.

### What you can do

- Start an exploratory hunt from selected library TCs + optional user story (Ollama or Cursor planner).
- Cap invent scenarios, cycle ceiling, and actions per cycle (default 5; wait defaults to 5s).
- Choose DOM mode: Auto page map / map only / full slim.
- Optionally run the strategy playbook (happy → empty → boundary → abuse → session → invent).
- Download a hunter pack ZIP: bugs, candidate scenarios, per-cycle evidence (prompt/response/screenshot/page map), steps journal, coverage map.

### Why

Execute/Automate prove written cases. Bug Hunter **probes** for breakage and invents review-only edge scenarios without writing the library.

### How to use it

1. Set `delivery.dry-run=false` for a live browser hunt (dry-run still produces a stub pack).
2. Open **Bug Hunter**, pick project + TCs, start.
3. When the job completes, download the pack from Status / jobs — review bugs and candidates yourself.

### What we did not change

- No auto-merge of candidate scenarios into the generated library.
- Automate prove / Execute regression flows unchanged.

### Known limits

- **Phase 3** (two-pass DOM neighborhoods) not built — only if AxisPay still exceeds context after map + slim.
- Oracles: HTTP errors / asserts / alerts; not yet blank-main, unexpected URL, or full `net::ERR_*` promotion.
- Live AxisPay smoke still a human follow-up after portal restart.
- Screenshot remains one per cycle at cycle start (not after every action).

**Specs:** `docs/superpowers/specs/2026-09-09-bug-hunter-design.md`, `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md`.

---

## 2026-09-07 — Project library, honest runs, optional Cursor pre-run

**Commit:** `abc06ce` — *Keep generated TCs editable in the project and stop silent invent/soft-pass.*

This release is for people who generate cases, keep them on a project, then run Execute or Automate without the product inventing data or calling a failed job “done.”

### 1. Generated cases stay on the project

**What you can do**

- Open a project → **Test cases** → **Generated library**.
- See the saved generated workbook (`latest.xlsx`).
- Edit a case (same save path as before).
- Delete selected cases. Keel will not delete the last remaining case.

**Why**

Generated TCs used to live mainly as a file you re-uploaded. You can now treat the project as the library and come back later.

**How to use it**

1. Generate (or import) until the project has a generated workbook.
2. Open the project **Test cases** tab.
3. Edit or delete in **Generated library**.
4. On Execute or Automate, pick those cases instead of starting from a blank upload.

**Design note:** `docs/superpowers/specs/2026-09-07-project-tc-library-design.md`

### 2. Execute / Automate: pick library cases and mix an upload

**What you can do**

- Use the **project library** as the source.
- Select one or more `TC_ID`s. Selecting none means **all** library cases.
- Optionally attach an `.xlsx` or `.csv` at the same time (**mix**).
- If the same `TC_ID` exists in both, **the upload wins**.

**Why**

You can rerun a subset, or patch a few cases from a file, without replacing the whole library every time.

**How to use it**

1. Open Execute or Automate.
2. Leave the library selected (or pick specific IDs).
3. Optionally choose a file to mix in.
4. Start the job.

### 3. CSV download can be uploaded again

**What you can do**

- Download the generated CSV from Generate.
- Upload that CSV on Execute or Automate (file picker accepts `.xlsx` and `.csv`).
- Keel converts Keel CSV into a workbook internally.

**Why**

The download used to be rejected because the run pages only accepted Excel.

### 4. Optional Cursor review right before a run

**What you can do**

- On Execute or Automate, check **Review selected TCs with Cursor before run**.
- Inspect findings and the proposed CSV.
- **Accept & run** starts the job with the reviewed suite.
- **Cancel** does not start the job.

**Why**

Generate already had **Review with AI** (Cursor or Ollama). That button is **manual** and only on Generate. It never ran automatically before Execute/Automate, which is why broken TCs could go straight to the browser.

This new checkbox is **propose-only**. It reviews the exact suite that job will run.

**How this differs from Generate Review with AI**

| | Generate **Review with AI** | Execute/Automate **Review before run** |
|---|---|---|
| When | After a workbook is saved | Right before a job |
| Providers | Cursor or Ollama | Cursor |
| Accept | Saves into the project workbook | Uses the proposal **for that job only** |
| If you skip it | Nothing happens | Job starts as usual |

**Known limit:** Accept & run does **not** write the proposal back into **Generated library**. To keep the reviewed text, edit/save on the project page, or Accept on Generate Review with AI.

If Cursor is down and the box is checked, uncheck it to run without review.

### 5. Generate does not invent TestData unless you allow it

**What you can do**

- Generated TestData stays blank or `<PLACEHOLDER>` unless you explicitly allow inventing values.
- Login username/password steps use `${TARGET_USERNAME}` and `${TARGET_PASSWORD}` (project credentials), not fake names.

**Why**

Keel was filling empty cells with faker data (for example a random username). Login then typed those values, failed, and still looked “successful” in some cases.

Heal fillers can still invent when they are repairing a live step. That is not the Generate column invent path.

### 6. Login cases run as the case, not a hidden prelude

**What you can do**

- A case that already has login steps (type user, type password, click login) is executed as written.
- Keel no longer injects a separate login prelude and then strips those body steps.

**Why**

Prelude + stripped body made login TCs type the wrong fields or skip the case the author wrote.

### 7. Jobs that cannot prove anything fail honestly

**What you can do**

- If every case is still `TODO` / not proven, the job status is **FAILED** (`ALL_CASES_TODO`).
- You do not get a green **COMPLETED** with `0` passed and `1` TODO.

**Why**

A white page or unresolved host used to finish as completed. That looked like a pass.

### 8. Locator memory does not treat every click as login

**What you can do**

- After login, clicks on cart / checkout / etc. are remembered for the **live page**, not as login locators.
- Auth-only locators (`login-button`, `user-name`, `password`, …) are rejected for non-login intents.

**Why**

SauceDemo (and similar apps) stored `login-button` against later clicks, so the next run clicked login again.

### 9. Login page wait before typing

**What you can do**

- After opening a login URL, Keel waits for the document to be ready and for a password field (about 12 seconds) before typing.

**Why**

SPA / slow pages opened a blank white window, the job typed too early, then the browser closed.

### 10. Execute Results button

**What you can do**

- From a recent Execute run, **Results** scrolls to and loads that run’s evidence.
- Empty or error states say so clearly instead of looking like a dead button.

---

## What this release did not change

- LLM Wiki (still parked).
- No Claude / Anthropic models.
- Generate **Review with AI** still exists and is still the way to **save** a reviewed suite into the project workbook.
- No version history of the library (one current generated workbook).
- Cursor pre-run is opt-in. Unchecked = no review.

---

## Known limits (read before you demo)

1. **Accept & run** is for that job only. The project library stays as it was until you edit it or Accept on Generate.
2. Cursor pre-run can still rewrite TestData in the **proposal**. Runtime invent-on-empty is what we stopped.
3. Convert jobs that **FAILED** may still offer a download if a ZIP was left behind. Treat that ZIP as incomplete.
4. After UI or prompt changes: **restart the portal** and **hard-refresh** the browser.

---

## Earlier published work (short)

These are already on the public history. Details live in older specs under `docs/superpowers/specs/`.

### 2026-09-06 — Generate authoring preflight review

**Commit:** `fbb1a28`

- Optional **Review with AI** on Generate (Cursor or Ollama).
- Findings + proposed suite; **Accept** saves the workbook, **Discard** leaves it.
- Banner when the quality gate finds issues; review never starts by itself.

### 2026-09-02 — Heal recovery written back to the workbook

**Commit:** `93e86c2`

- Successful heal **recovery** can patch the generated workbook (for example leave-empty + blank TestData) so the next run matches what the page needed.

### Portal publish — Generate, Automate, Execute

**Commit:** `2fc6832`

- Public portal surfaces: Generate, Automate, Execute, heal recovery, and product docs (`README.md`).

---

## Suggested demo path for this release

1. Restart Keel. Hard-refresh Generate, the project page, Execute, and Automate.
2. Generate a suite. Confirm TestData is blank / placeholder unless you allowed invent.
3. Open the project → **Test cases** → **Generated library**. Edit one row. Delete nothing required for the demo except a spare case.
4. Execute: select two library IDs, no file. Run.
5. Execute again: mix a CSV that overrides one `TC_ID`. Confirm the upload row is what ran.
6. Optional: check Cursor review, read findings, Accept & run. Confirm the library file did **not** change unless you saved it on the project.
7. Upload a Generate CSV on Automate and confirm it is accepted.
8. If you have a login-only case, confirm the job types `${TARGET_*}` credentials and does not invent a fake user.
