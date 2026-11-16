# First-release contract (P0-01)

**Date:** 2026-09-16  
**Status:** Working engineering default — pending product/security named sign-off  
**Owner (acting):** engineering, from [launch implementation plan](../../reviews/2026-09-15/IMPLEMENTATION-PLAN.md) first batch  
**Source revision at start:** `0aca9c6` plus later Hunt/docs commits on `main`; this work lands on `launch/p0-baseline-and-p1-assertions`

This document is the first-release contract for P0-01. It is the working default so P0-02 and P1-01 can proceed. Product and security must still assign a named owner and approval date before Phase 5 launch advertising.

---

## 1. Supported first-release scope

| Area | In first-release promise | Out of promise |
|------|--------------------------|----------------|
| Browsers | Chrome and Edge via Selenium as used by portal workers and the customer template | Safari, Firefox, mobile WebView, headless-only as a support guarantee |
| Application types | Public HTTPS web apps with a DOM (HTML forms, buttons, links, text) | Native apps, file-upload flows, canvas/WebGL as first-class actions, captcha/passkeys/2FA |
| Authentication | Username/password credential profiles; `${TARGET_USERNAME}` / `${TARGET_PASSWORD}` tokens; optional OTP hint for Hunt | SSO, passkeys, MFA beyond a typed OTP field |
| Input formats | Excel `.xlsx`, CSV, JSON import, Generate-from-stories workbook | Live Figma/Jira/ALM sync |
| Authoring engines | **Keel** (default) and **Precision** (project Settings); Precision is accuracy/cost, **not** a data-residency policy | Additional engines |
| Generated framework | Java 21, Selenium, TestNG ZIP from `customer-framework-template/` | Other languages |
| Surfaces | Generate, Execute, Automate NEW/UPDATE, Bug Hunter (review-only packs) | Hunt auto-merge into the library |

KeelPath: Automate and Execute both run every non-`MANUAL` row (`AUTOMATE`, `EXECUTE`, `VISION_ONLY`, blank). `MANUAL` is skipped on both surfaces.

---

## 2. Result vocabulary

One contract for IR drafts, portal jobs, exports, and downloaded tests. Do not collapse these into a single unexplained pass rate.

| Term | Meaning | Where it appears |
|------|---------|------------------|
| **PASSED** | Every authored intent was proven in a **live** browser for this case (or, after P1-02, eligible reused **prior live proof** with complete IR). | IR `PASSED`; portal passed count; generated executable test |
| **FAIL** | A generated-framework assertion did not hold at replay time. Not a first-class IR status today. | TestNG failed result; Maven nonzero exit (P1-01) |
| **TODO** | No body step was proven. Login/bind failed before any step, or dry-run simulation of an unauthored case. | IR `TODO`; portal todo/blocked count; generated TODO class |
| **PARTIAL** | A prefix of steps was proven; a later intent could not be recovered. | IR `PARTIAL`; portal blocked; generated TODO/partial emit |
| **BLOCKED** | Job or case cannot be trusted as client-ready (final-revise soft block, quality gate, or `MANUAL` skip). | Job `COMPLETED_WITH_BLOCK`; dashboard blocked |
| **REUSED** | UPDATE kept a prior draft because content hash was unchanged. **Not automatically PASSED.** After P1-02: only eligible prior PASSED with complete compatible IR may count as passed; TODO/PARTIAL/failed must be re-proven. | IR `REUSED`; must expose provenance, not imply a fresh run |
| **SIMULATED** | Dry-run packaging/execute with no live browser and no model authoring. Outcomes are TODO/SIMULATED. Must never inflate live pass rate. | Job message / status metadata; tests and UI must label simulation |

Portal job statuses remain: `QUEUED`, `RUNNING`, `COMPLETED`, `COMPLETED_WITH_BLOCK`, `FAILED`, `CANCELLED`.

---

## 3. Dry-run contract (locked for P0-02)

`delivery.dry-run=true` means **simulate packaging / execute / hunt without a live browser or model**.

| Situation | Job status | Case outcomes | Pass rate |
|-----------|------------|---------------|-----------|
| Dry-run Automate/Execute, all cases TODO | **COMPLETED** (simulated) | TODO + SIMULATED | Live pass rate: 0; do not treat as failed authoring |
| Live (`dry-run=false`), passed=0 and todo>0 | **FAILED** `ALL_CASES_TODO` | TODO/PARTIAL | Hard stop — nothing was proven |
| Live, mix of passed and todo | **COMPLETED** | mixed | Honest counts |
| Live, final-revise BLOCK | **COMPLETED_WITH_BLOCK** | as proven | ZIP still downloadable |

Workers must **not** apply `ALL_CASES_TODO` when `delivery.dry-run=true`. Dry-run is a valid simulation, not a failed prove.

Tests that assert dry-run jobs complete are aligned with this contract. Production must not silently call real Ollama/Cursor from deterministic tests.

---

## 4. Dependent tests (Call-before)

**First-release advertising:** a downloaded ZIP is replay-ready for Call-before when emit copied each prerequisite's proven steps into the leaf `@BeforeMethod` (same fresh browser as login).

- Prove-time Call-before still shares a session within a leaf chain.
- Emitted tests start a fresh browser per class and replay setup from IR (`setupTcIds` plus prerequisite proven steps). They do not rely on TestNG class order.
- Failed or incomplete prerequisites demote the dependent leaf (`CALL_BEFORE_BLOCKED`) instead of advertising PASSED automation.
- Cycles are validation errors (`CALL_BEFORE_CYCLE`) at ingest and expand time.
- Repeated executions of the same logical TC keep distinct evidence folders (`{tcId}__occ_{n}`).

---

## 5. Decision records (owners still required)

| Decision | Working default | Owner / deadline | Blocks |
|----------|-----------------|------------------|--------|
| AI data policy | Explicit provider allowlist; no silent fallback outside it. Precision ≠ privacy. Cloud vs local-only is a commercial mode, not implied by engine choice. | Security + product — before P2-04 | P2-04, E05 |
| Hosted target access | Shared host: approved public HTTPS origins only. Dedicated/private-runner: explicit private-target policy. | Security — before P2-03 | P2-03, E04 |
| Tenant / team | User-owned projects today. First shared launch: workspace + owner/admin/member (P2-01). | Product — before P2-01 | P2-01, E03 |
| Retention | Keep current sweeper as operational default; do not invent new numbers from the audit. Set measurable limits before P3-03. | Ops — before P3-03 | P3-03 |
| Concurrency | Two conversion workers exist; no tenant quota yet. Set launch limits before P3-01. | Ops — before P3-01 | P3-01, P5-01 |
| Support ownership | Self-hosted; default admin must be changed. Named support contact required before P5-02. | Product — before P5-02 | P5-02 |
| Customer-editable framework files | Preserve `webapp.properties` and non-generated sources; regenerate generated pages/tests/data (P1-03). | Engineering — before P1-03 | P1-03 |

Shared vs dedicated: same identity model; dedicated is single-tenant configuration of the same contracts, not a fork.

---

## 6. Authoring and generate contracts (for P0-02 tests)

- **TestData invention is off** unless stories explicitly allow it. Unknown values stay blank or `<PLACEHOLDER>`. Tests that still expect invented nonblank values must be updated to the current contract, not the other way around.
- **Page/test naming** follows the current codegen normalizer (`LoginPage`, catalog `TC_1` vs old `Login` / `TC_...Test`). Tests expecting retired names are outdated.
- **Generate/compare tests** must stub the method the production path actually calls (`callOllamaDetailed` if that is the live path). Deterministic tests must fail immediately on unexpected network/provider calls.
- **Import tests** must provide the stored workbook the service reads after save, or isolate fixtures — no runtime `delivery-store` leftovers.

---

## 7. Acceptance for this package

- [x] Working default recorded for results, dry-run, support boundaries, and deferred decisions.
- [ ] Named product owner + approval date (required before advertising these guarantees).
- [ ] Security owner + approval date for AI and hosted-target rows.

P0-02 and P1-01 proceed against this working default.
