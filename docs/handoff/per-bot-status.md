# Per-bot status (Grok Keel team)

Status as of **2026-10-14** from chat transcripts. **Delivered** = described as patch/tests done in Grok workspace and/or likely present uncommitted in TestPilot. **Verify** = must confirm in your tree.

---

## Maestro (controller)

| Item | Status |
|------|--------|
| Patch series planning (branches vs apply stack) | Discussed; **apply-to-disk** chosen over PR split |
| Applied 45 patches to TestPilot folder | **Claimed done** — **Verify** with tests + diff |
| Mobile / locator research orchestration | Done in Grok; docs **not in repo** |
| Frontend theme prototype | Screenshots only; **not** merged to reskin until rebased |
| Commit / push | **Explicitly not done** |

---

## Keel Automation Engine

| Deliverable | Status | Notes |
|-------------|--------|-------|
| **A1** Axis sidebar (li rejection, Hidden Support ambiguity) | Delivered | Patches in Grok `patches/A1/` |
| **A2** PageSerializer (scroll sidebar, iframes, shadow) | Delivered | Tests pass in Grok; **wire to ProvePhase** — verify |
| **A3** AI-pick element + rank top-3 locators + route replay cache | Delivered | |
| **A5** Canvas plan | Plan only | `patches/A5/PLAN.md` on Grok machine |
| **A6** Assertion model switch (UI-TARS default, 7B on 12GB+ GPU) | Delivered | |
| **A7** Tap / action “no effect” detection | Delivered | |
| **A8–A11** Settle table, failure evidence, loop guards, per-task models | Delivered | Feature flags **off by default** |
| UI-TARS coordinate parsing bug | Delivered | 36 tests |
| Settle tests vs background network noise | Delivered (r3) | |
| Vision shared-state leak across jobs | Fixed in wiring | |
| Contract v2 (text locators, point clicks) | Delivered | Off until codegen aligned |

---

## Keel Codegen

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Frame + shadow in generated tests | Delivered | |
| Top-3 fallback locators + Allure “which locator won” | Delivered | |
| Contract v2 codegen | Delivered | 513 tests claimed |
| Settle table in customer template | Delivered | Official A8 table from Automation |
| Flaky retries (read-only only), schemas, CI PR comment template | Delivered | CI reporter **off by default** |
| META-INF.services folder typo + E2E cron every minute | Fixed | **Verify** in `customer-framework-template` |
| Click with no effect → **fail** test (default) | Delivered | |
| Password never in generated code | Delivered | |
| Re-record loses ranked locators (patch 36/38) | Delivered | |

---

## Keel Frontend

| Deliverable | Status | Notes |
|-------------|--------|-------|
| UX audit (141 phrases → plain language) | Done | |
| 6 UX patches (More options, Settings split, Results, guide) | Redone on **reskin** | First batch was **wrong UI** |
| Execute form hidden bug (`formPanel`) | Fixed | Align with Cursor session |
| Calm blue prototype (screenshots) | Mock only | **Not** production theme |
| Retry verdict badges on Execute | Delivered with QA patch | Demo data in screenshots |

---

## Keel Backend

| Deliverable | Status | Notes |
|-------------|--------|-------|
| SPLIT_DESIGN.md | Grok only | SQL Server Express, mTLS, engine pulls jobs |
| H2 password / issue #2 | Delivered | Portal won’t start without password |
| keel-contract / common / portal / engine modules | **Partial** in repo | |
| Runner API v2, signed requests | Delivered | |
| EngineAgent (Execute + Automate remote) | **Verify** `EngineAgent.java` |
| start-*.cmd launchers | **On disk** | |
| DB migration column on first start | **Backup DB first** | `db-backup-20261013-*` per Grok |
| Generate/Hunt still in portal | **Not moved** | |

---

## Keel QA/DevOps

| Deliverable | Status | Notes |
|-------------|--------|-------|
| Locator fixture pages + JS harness scores | Grok `locator-fixtures/` | 32% → 80% with iframe/shadow serializer |
| SPLIT_DESIGN_OPS.md | Grok only | Deferred on company PC |
| Honest retry / flaky / healed reporting | Delivered | ProvePhase retry bug fixed |
| Mobile plan v2 (40 items) | Written in Grok | Merge into `docs/superpowers/.../tasks.md` |
| GitHub issues #1 passwords, #2 H2 | Created on Keel repo | |

---

## Keel Codegen bot (separate thread)

Parallel work on **testpilot-codegen** patches; rebased A2→A3→v2→r1. **Not a substitute** for verifying **your** `TestPilot` folder after Maestro’s bulk apply.
