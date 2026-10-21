# CHANGELOG

- OWNER LLM Settings: Cursor/AgentRouter/Ollama keys + live model picker (/admin/llm-settings).

# Keel changelog

## 2026-10-20 - Codegen naming: page under test (not Excel title)

- **Page + test class names** come from the **screen under test** (IR `pageName`, usually URL path leaf such as `/login` → `Login_Page.java` and `Login_Test.java`). Excel titles like “Valid login” and words such as valid/invalid are **not** used for class names.
- **Test methods** use a short stem from that page (e.g. `login()`), with `@Test(description = "TC_ID — title")` keeping the Excel title for humans.
- When two cases share the same page stem, the second test class falls back to `TC_IDTest` (same rule as before, but keyed on page stem instead of title).
- **Revise phase** annotates the correct generated file (`Home_Test.java`, not `TC_REV.java`) using the same collision rules as emit.
- **Merge by page stem:** multiple Excel cases on the same screen (e.g. valid/invalid login) emit **one** `Login_Test.java` with several `@Test` methods; duplicate method names get a `TC_ID` suffix.
- **Primary page:** body steps prefer the **last non-login** screen (destination page), not login-heavy step counts.
- Re-run Automate **NEW** or **UPDATE** to regenerate the ZIP. Spec/handoff updated under `2026-10-20-codegen-ux-emit`.

## 2026-10-20 - Codegen UX (cleaner Automate ZIP)

- **Emit:** echo/html-only and `:pageShown` noise no longer appear in generated Java; login steps collapse to `login(...)` when possible; text asserts keep real locators instead of whole-page shortcuts where the IR had a locator.
- **Tests:** page-under-test class names (`Login_Test.java`, `Login_Page.java`); class-level page fields; prerequisites in `@BeforeMethod` via a private helper with local page vars.
- **Test data:** generated calls use `PropertyReader.getProperty` / secrets sidecar. Login placeholders map to **`username` / `password`** (and OTP-style TARGET keys similarly) by default. Set optional **Test data env** on Project → Settings (e.g. `sit`) to prefix keys as **`sit_username`**, **`sit_password`**, etc. Other fields keep TC-scoped keys like `TC_01.body.type_Email.1`.
- Rebuild jar, restart, Ctrl+F5; re-run Automate to regenerate the ZIP. Spec: `docs/superpowers/specs/2026-10-20-codegen-ux-emit-design.md`.

## 2026-10-20 - List-row-first click + extra Treat as kinds

- **List / grid clicks:** when a step names a shared cell (e.g. `with Pending edit status` or `"Pending Edit"`), Keel binds the **first** visible matching table/grid row and clicks that row’s name link (not the status badge). Quote a person when it matters: `Click on "Omar Nabil"`. Two **Login** buttons on the same page (not in a grid) stay unresolved.
- **Treat as:** Owner aliases and built-ins now include **Select**, **Clear**, **Drag**, **Drop**, **Hover**, **Double-click**, and **Right-click** where Execute can run them (`clear`, `select`, `drag`, `hover`, `doubleClick`, `rightClick`). Clear-the-field steps emit `clear`; drag steps need `drag X onto Y`. File upload / drag-and-drop upload stays unsupported.
- Guide `/guide#verbs` and Admin → LLM & API keys dropdowns updated. Rebuild jar, restart, Ctrl+F5. Spec: `docs/superpowers/specs/2026-10-20-list-row-first-and-treat-as-design.md`.

## 2026-10-20 - Admin LLM page actually loads

- Opening **Admin → LLM & API keys** no longer dies with `ERR_INCOMPLETE_CHUNKED_ENCODING`. Thymeleaf was treating `[[` in the page script as an expression, so the HTML never finished and Chat/Vision stayed on “refresh models.” The Ollama URL is now a real default (`http://127.0.0.1:11434`), not only a placeholder; Chat/Vision load from live Ollama (or the last cache if Ollama blips). Rebuild jar, restart, Ctrl+F5.

## 2026-10-20 - Admin LLM page paints verbs and cached models

- **Admin → LLM & API keys** now renders the read-only built-in verb table and the seeded `observe` alias in the HTML (so the block is not an empty header). Chat/vision dropdowns fill from the last saved Ollama tag cache, then refresh quietly. Click **Refresh models** if the list is still stale.

## 2026-10-20 - Guide: authoring verbs

- The Guide now has **Verbs Keel understands**: built-in click / type / assert / skip words, how the Owner adds aliases on Admin → LLM &amp; API keys, seeded `observe` → assert, and that aliases are not a status filter. Execute, Automate, Account, and the Steps column point at that section.

## 2026-10-19 - Authoring verb aliases

- Owner **Admin → LLM & API keys** now lists built-in click / type / assert / skip words (read-only) and lets you add aliases onto those same kinds (Click, Type, Assert visible, Ignore). Default seed: `observe` → assert, so Observe steps wait for visible instead of being skipped. Built-in words cannot be remapped. Max 50 aliases. Save applies to the next Execute / Automate / Generate review / Record bind without a JVM restart after this jar is running.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`.

## 2026-10-19 - Scoped inner hook when a submenu clones the link

- Named click/type/clear/select: if the unique menu row contains one preferred hook that also exists elsewhere (Ant popup copy), Keel now clicks that inner hook scoped under the row (`//li[…]//a[@data-axis-test-id='…']`) instead of keeping the row xpath. Document-unique hooks still become CSS. Two different hooks inside one row still keep xpath. Twin Settings stay unresolved.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-scoped-nested-hook-design.md`.

## 2026-10-19 - Spec: authoring verb aliases

- Approved design (not implemented): Admin shows built-in click/type/assert verbs read-only. Owner adds aliases (Click / Type / Assert visible / Ignore), seeded with `observe` → assert. Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`.

## 2026-10-19 - Admin review batch size + browser type

- Owner **Admin → LLM & API keys**: **Review cases per batch** (1–50, default 50) and **Browser** (Chrome, Chrome headless, Edge). Selecting more than 5 cases splits review into those batches (20 with 7 → 7+7+6). Combined preview after the last batch; Accept waits until every batch succeeded. Env still wins for `BROWSER_TYPE` / `BROWSER_HEADLESS`. Next job that opens a window uses the saved browser.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`.

## 2026-10-19 - Spec: admin review batch size + browser type

- Approved design (now implemented): Owner sets review TCs per batch and Chrome / Chrome headless / Edge on Admin → LLM & API keys. Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`.

## 2026-10-19 - Guide: review, leftover quotes, preferred hooks

- The Guide Execute / Automate / Expected sections now describe Review cases before run, Accept saving the library, leftover Expected quotes (filler words stay outside quotes), and inner preferred hooks on named clicks.

## 2026-10-19 - rebuild-portal.cmd

- Repo-root `rebuild-portal.cmd` packages `keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` (skip tests). It does not start the portal. After it finishes, run `start-portal.cmd` then Ctrl+F5.

## 2026-10-19 - Nested hook and quote heading follow-up

- **Execute asserts:** the live heading upgrade now runs on the text Execute actually binds. A leftover that became `Confirm the Upload Transactions list area is visible.` or `Confirm the text "Upload Transactions" is visible` can still become `'Upload Transactions List'` when that heading is the only match. Each part of a compound expected check is upgraded the same way before it is bound. Asserts still never heal, and Execute still does not write the library.
- **Remembered locators:** a saved click, type, clear, or select xpath is checked against the live page before it runs. One unique preferred hook inside that wrapper becomes CSS; otherwise the saved xpath is used.
- **Vision:** when the pointed-at node has exactly one document-unique preferred hook, that node and hook are kept even if a child also has a hook. Children are searched only when the pointed-at node has none, and only when those children yield exactly one hook.
- **Review cases:** dash bullets stay dash bullets after the leftover-quote pass (numbered rows were already numbered again). A quote the model already wrote that still contains a filler word, such as `'Upload Transactions list area'`, is replaced with the filtered name or the quote already used in Steps. A distinctive quote with no filler, such as `'Bill Payment Settlement'`, is left as written.

## 2026-10-19 - Nested preferred hook + leftover Expected quoting

- **Execute bind:** after a named click or type binds a wrapper row (for example an Ant Design `li`), Keel looks inside that node. When **exactly one** site preferred hook from project Settings matches one node in the whole document (for example `a[data-axis-test-id='…']`), the step uses that CSS; zero or two-or-more hooks keep the xpath. Twin Settings buttons stay AMBIGUOUS. Vision grounding uses the same preferred-hook list, including a unique child.
- **Review cases:** a deterministic pass after Cursor or Keel review strips filler words from leftover Expected (`area`, `button`, `label`, `title`, `page`, `section`, `field`, `menu`, `item`, `list`, `visible`, `shown`, `displayed`) and quotes the distinctive name (for example `the Upload Transactions list area is visible` → `'Upload Transactions'`). **Accept & run** still saves the library. **OutcomeKind** is unchanged: quoted text in Steps/Expected still means visible text at runtime.
- **Execute asserts:** before binding a leftover Expected check, Keel may upgrade **unquoted** page-shown leftover (filler stripped first) or a **quoted** leftover (library or review) to a wait on the **live** page title when **exactly one** visible heading or card title contains those tokens (for example `the Upload Transactions list area is visible` or `'Upload Transactions'` → `'Upload Transactions List'`). Zero or two-or-more matches leave the clause unchanged. Asserts still never heal; Execute does not write the library.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`. Plan: `docs/superpowers/plans/2026-10-19-nested-hook-and-quote-heading.md`. Handoff: `docs/handoff/2026-10-19-nested-hook-and-quote-heading.md`.

## 2026-10-18 - Add test case and review cases before run

- Project **Test cases** tab: **Add test case** opens the existing editor with the next `TC_` id. **Save** writes the library workbook (an empty library is fine — Keel creates the workbook).
- **Execute** and **Automate**: optional **Review cases before run** sends the selected cases to **Cursor** or **Keel** with Keel authoring rules. **Accept & run** saves the edited cases into the **library** (version history) and then starts the job. Uncheck review to run the library as-is.
- **Accept & run** after an Upload review now starts only the reviewed `TC_ID`s, does not ask for the old Excel file again, and keeps existing CallBefore links when the review omits them.
- Pre-run review is **not** charged. **Execute** and **Automate** still charge their run/framework prices when the job **starts** (after Accept when review is on).
- Runtime **OutcomeKind** is unchanged: quoted text in Steps/Expected still means visible text at execute time.

## 2026-10-18 - Generate AI model list follows live Ollama

- Generate → AI model, Compare two, and Review list chat models from Ollama `/api/tags` (same URL/token as Admin LLM Settings). Vision-like names (`vl`, `llava`, `ui-tars`, `vision`) are omitted.
- Last full tag list is saved as `cachedOllamaModels` in `llm-settings.json`. No cache yet: loader until Ollama answers. Cache present: show it, then “Updating…”. Ollama down: last list plus a short unreachable note.
- Admin Save still sets the install default. Account → Vision stays UI-TARS vs Qwen. Pull a new chat model and it appears after refresh — no code edit.

## 2026-10-18 - LLM Settings clear drops System props; env wins for URL/model

- Clearing Cursor / AgentRouter / Ollama keys in Admin LLM settings now clears the matching runtime System property when process env is unset, so the key drops without a portal restart.
- Non-blank env still wins: publish does not touch the System property when getenv is set.
- Stored Ollama base URL / chat model no longer overwrite in-memory DeliveryPortalProperties when delivery.llm-base-url / DELIVERY_LLM_BASE_URL, delivery.llm-model / DELIVERY_LLM_MODEL, or delivery.generate-model / DELIVERY_GENERATE_MODEL is already set in the environment (same env-wins rule as System properties).
- Secrets stay masked; never logged.

## 2026-10-17 - Heal API keys via Settings publish + optional Ollama Bearer

- Automation Engine resolves CURSOR_API_KEY / AGENTROUTER_API_KEY / OLLAMA_API_KEY through ApiKeySource: env then System property (portal LLM Settings publish) then DEV-only LocalApiKeyLoader (api-keys.local.*) last. Never logs secret values.
- CursorHealClient and AgentRouter invent/final-revise read via ApiKeySource for those three names (other AgentRouter fallbacks unchanged).
- LocalLlmClient sends Authorization Bearer on Ollama /api/chat when OLLAMA_API_KEY resolves non-blank; blank means local tokenless.
- Portal UI for keys stays Backend /admin/llm-settings (no AE portal UI). Midscene / split-engine secret fetch deferred.

## 2026-10-17 - Richer TIE handoff (Ollama + Cursor)

- Both Ollama and Cursor TIE picks share HealPickPayload: whyTied (margin + same-label), scored shortlist (role/tag, label, stability, intentMatch, totals), HTML neighborhood (parent snippets, NEIGHBORHOOD_MAX), optional screenshotPath when evidence already has a file (empty-ok; never invent capture).
- DOM grounding only: pick candidateId from the shortlist; no invent / Thought / written locator on the pick path; no Midscene vision-first.
- Cursor sidecar mode=pick prompt uses quotedName / whyTied / scoredShortlist / neighborhood; slimHtmlExcerpt stays empty on this path.


## 2026-10-17 — OutcomeKind enabled / nameless / toast

- ENABLED/DISABLED no longer map to visible (false-pass when present but disabled). Real execute types enabled/disabled use isEnabled in prove and generated Page asserts.
- Nameless/weak UNKNOWN confirm lines skip-echo (css/html + intent:ASSERT_VISIBLE:unknown-echo) instead of CandidateScorer TIE; body textContains only for distinctive tokens (expanded denylist).
- CONTROL_VISIBLE / FIELD_VISIBLE accept OTP / Username / Logout without requiring the word field/button.
- Unquoted toast lines (error toast is shown, success message appears) bind textContains on the kind token not a shortlist TIE.
- Asserts still never heal. Twin Settings clicks stay AMBIGUOUS.

## 2026-10-16 — Candidate scorer pack

- Outcomes are a checklist: confirm/expected lines are classified before CandidateScorer. Page-shown and "is clicked" no longer TIE the shortlist.

- Bind chooses from a scored DOM shortlist (CandidateScorer / BindDecision CLEAR|TIE|EMPTY).
- Catalog fingerprint ignores class-hash churn; write-back only after a live click (commitAfterProof).
- Ollama/Cursor pick only on TIE (max 5); ASSERT_VISIBLE still never heals.
- Fixture gate: CandidateScorerFixtureTest + bind-quality.json.
- Review fixes: ProvePhase records bind/ollama/vision/heal into bind-quality.json (flush in finally); Ollama TIE pick gets slimHtml neighborhood; catalog preferStable uses CandidateScorer.stability (testid over raw id).
- bind-quality.json holds **job totals** across TCs (not last-TC-only). proveOne still clears pageCatalog; it no longer zeros BindQualityMetrics.
- Execute: “Username is accepted” after a fill binds that field (input over a same-name hint; spent fill wins a TIE). Excel is not rewritten. Asserts still do not heal.


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
