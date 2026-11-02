# Per-bot status (Grok Keel team)

Status as of **2026-10-14** from chat transcripts. **Delivered** = described as patch/tests done in Grok workspace and/or likely present uncommitted in TestPilot. **Verify** = must confirm in your tree.

---

## Maestro (controller)

| Item | Status |
|------|--------|
| Patch series planning (branches vs apply stack) | Discussed; **apply-to-disk** chosen over PR split |
| Applied 45 patches to TestPilot folder | **Claimed done** â€” **Verify** with tests + diff |
| Mobile / locator research orchestration | Done in Grok; docs **not in repo** |
| Frontend theme prototype | Screenshots only; **not** merged to reskin until rebased |
| Commit / push | **Explicitly not done** |

---

## Keel Automation Engine

| Deliverable | Status | Notes |
|-------------|--------|-------|
| **A1** Axis sidebar (li rejection, Hidden Support ambiguity) | Delivered | Patches in Grok `patches/A1/` |
| **A2** PageSerializer (scroll sidebar, iframes, shadow) | Delivered | Tests pass in Grok; **wire to ProvePhase** â€” verify |
| **A3** AI-pick element + rank top-3 locators + route replay cache | Delivered | |
| **A5** Canvas plan | Plan only | `patches/A5/PLAN.md` on Grok machine |
| **A6** Assertion model switch (UI-TARS default, 7B on 12GB+ GPU) | Delivered | |
| **A7** Tap / action â€œno effectâ€ detection | Delivered | |
| **A8â€“A11** Settle table, failure evidence, loop guards, per-task models | Delivered | Feature flags **off by default** |
| UI-TARS coordinate parsing bug | Delivered | 36 tests |
| Settle tests vs background network noise | Delivered (r3) | |
| Vision shared-state leak across jobs | Fixed in wiring | |
| Contract v2 (text locators, point clicks) | Delivered | Off until codegen aligned |

---

## Keel Codegen

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Frame + shadow in generated tests | Delivered | |
| Top-3 fallback locators + Allure â€œwhich locator wonâ€ | Delivered | |
| Contract v2 codegen | Delivered | 513 tests claimed |
| Settle table in customer template | Delivered | Official A8 table from Automation |
| Flaky retries (read-only only), schemas, CI PR comment template | Delivered | CI reporter **off by default** |
| META-INF.services folder typo + E2E cron every minute | Fixed | **Verify** in `customer-framework-template` |
| Click with no effect â†’ **fail** test (default) | Delivered | |
| Password never in generated code | Delivered | |
| Re-record loses ranked locators (patch 36/38) | Delivered | |

---

## Keel Frontend

| Deliverable | Status | Notes |
|-------------|--------|-------|
| UX audit (141 phrases â†’ plain language) | Done | |
| 6 UX patches (More options, Settings split, Results, guide) | Redone on **reskin** | First batch was **wrong UI** |
| Execute form hidden bug (`formPanel`) | Fixed | Align with Cursor session |
| Calm blue prototype (screenshots) | Mock only | **Not** production theme |
| Retry verdict badges on Execute | Delivered with QA patch | Demo data in screenshots |
| Generate-from-knowledge Phase 1 shell + Start->create-run | **Live tip** 2026-11-02 17:28 Africa/Cairo | POST `/api/projects/{id}/generate-from-knowledge/runs` + poll; stub-tolerant; backup `_backup\backup-pre-gfk-run-wire-20261102-172818`; see handoff |
| Phase 4A Plain-English case author FE shell | **Live tip** 2026-11-02 17:48 Africa/Cairo | `/plain-cases` after Record before Execute; stub POST `/api/projects/{id}/plain-cases`; backup `_backup\backup-pre-phase4a-plain-20261102-174801`; handoff `docs/handoff/2026-11-02-phase4-plain-english-fe.md` |
| Phase 4B Visual-diff approve-baseline UX | **Live tip** 2026-11-02 ~18:01 Africa/Cairo | Execute panel `#visual-diffs-panel`; Evidence link; GET `.../visual-diffs?pending=true` + POST approve; backup `_backup\backup-pre-phase4b-visual-20261102-180127`; handoff `docs/handoff/2026-11-02-phase4-visual-diffs-fe.md` |
| Phase 4A Plain-cases FE wire to Backend APIs | **Live tip** 2026-11-02 18:20 Africa/Cairo tip `83b9077` | Save/list/execute wired in `plain-cases.html`; smoke Save **201** list **200** execute **202** `exec_06a30b89ee3b`; backup `_backup\backup-pre-plain-cases-wire-20261102-181749`; handoff append Wire LIVE 83b9077 |
| GFK ingest strip + Visual diffs sidebar | **Live tip** 2026-11-02 ~18:35 Africa/Cairo tip `83b9077` | Add pack tabs Library/Upload TC/.md on GFK; sidebar Visual diffs → `/execute#visual-diffs-panel` + hash scroll; backup `_backup\backup-pre-gfk-ingest-vd-sidebar-20261102-183236`; no push; no portal restart |
| Phase 2 Failure-replay + heal Accept/Reject FE (locked contract) | **Live tip** 2026-11-02 ~19:05 Africa/Cairo tip `83b9077` | Evidence retargeted to Backend interim `failures`/`heal-proposals`/`flaky-tags`; stub-tolerant; backup `_backup\backup-pre-phase2-evidence-20261102-190110`; handoff `docs/handoff/2026-11-02-phase2-fe.md`; no push; no portal restart |
| Phase 3 Suites + schedule + alerts/monitor FE shell | **Live tip** 2026-11-02 ~19:55 Africa/Cairo tip `e9ee274` | `/suites` after Plain cases before Execute; locked contract `phase3-api-contract.md`; stub-tolerant; backup `_backup\backup-pre-phase3-suites-20261102-194545`; handoff `docs/handoff/2026-11-02-phase3-fe.md`; jar refresh needed (template+controller); no push; no portal restart |
| Plain-English UI copy pass (templates + JS + Suites/nav after P3) | **Live tip** 2026-11-02 ~19:55 Africa/Cairo tip `ce4bc2c` + dirty copy | UI strings only; suite->test pack; backup `_backup\backup-pre-plain-english-ui-20261102-194543`; handoff `docs/handoff/2026-11-02-plain-english-ui.md` GREEN; jar refresh needed; no push; no portal restart |

---

## Keel Backend

| Deliverable | Status | Notes |
|-------------|--------|-------|
| SPLIT_DESIGN.md | Grok only | SQL Server Express, mTLS, engine pulls jobs |
| H2 password / issue #2 | Delivered | Portal wonâ€™t start without password |
| keel-contract / common / portal / engine modules | **Partial** in repo | |
| Runner API v2, signed requests | Delivered | |
| EngineAgent (Execute + Automate remote) | **Verify** `EngineAgent.java` |
| start-*.cmd launchers | **On disk** | |
| DB migration column on first start | **Backup DB first** | `db-backup-20261013-*` per Grok |
| Generate/Hunt still in portal | **Not moved** | |
| OWNER LLM Settings clear + env-wins URL/model | **Live tip 2026-10-18** | Clear flags `System.clearProperty` when env unset; applyToRuntime skips props.set* when env present; PRODUCT Admin bullet |

---

## Keel QA/DevOps

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Locator fixture pages + JS harness scores | Grok `locator-fixtures/` | 32% â†’ 80% with iframe/shadow serializer |
| SPLIT_DESIGN_OPS.md | Grok only | Deferred on company PC |
| Honest retry / flaky / healed reporting | Delivered | ProvePhase retry bug fixed |
| Mobile plan v2 (40 items) | Written in Grok | Merge into `docs/superpowers/.../tasks.md` |
| GitHub issues #1 passwords, #2 H2 | Created on Keel repo | |

---

## Keel Codegen bot (separate thread)

Parallel work on **testpilot-codegen** patches; rebased A2â†’A3â†’v2â†’r1. **Not a substitute** for verifying **your** `TestPilot` folder after Maestroâ€™s bulk apply.


